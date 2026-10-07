package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.MemberCountRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.UnpaidCounting;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountsResultDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCount;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCountRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The unpaid counts captured by hand (DOD-28, V72).
 *
 * <h2>All or nothing, and named by row</h2>
 * Every row is checked before anything is written -- a number nobody holds, a count outside 0 to 3, a member named
 * twice -- and every problem is named by its row, never by echoing what was typed (EH2-E). Then the rows are written
 * by JDBC batch: an update for the members on record, an insert for the rest -- two round trips for two thousand rows.
 *
 * <h2>Safe to capture twice</h2>
 * A member whose count is on record already, as at the same day, is left as it is and counted unchanged. The audit
 * row says how many, never whose (SD3-D).
 */
@Service
public class AcbUnpaidCountServiceImpl implements AcbUnpaidCountService {

    /** Membership numbers asked for in one statement. */
    private static final int NUMBERS_PER_QUERY = 1_000;

    private static final int MEMBERSHIP_NUMBER_MAX = 20;

    private final AcbUnpaidCountRepository counts;
    private final AcbPopulationRepository population;
    private final AcbAuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbUnpaidCountServiceImpl(AcbUnpaidCountRepository counts, AcbPopulationRepository population,
                                     AcbAuditTrail audit, JdbcTemplate jdbc, Clock clock,
                                     PlatformTransactionManager transactions) {
        this.counts = counts;
        this.population = population;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public AcbPage<AcbUnpaidCountDto> list(int page, int size) {
        Page<AcbUnpaidCount> found = counts.findAllByOrderByMembershipNumberAscIdAsc(
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE)));
        Map<Integer, String> surnames = new HashMap<>();
        if (!found.isEmpty()) {
            population.findCountRows(found.getContent().stream().map(AcbUnpaidCount::getMemberId).toList())
                    .forEach(row -> surnames.put(row.id(), row.surname()));
        }
        return AcbPage.of(found, c -> new AcbUnpaidCountDto(c.getMemberId(), c.getMembershipNumber(),
                surnames.get(c.getMemberId()), c.getUnpaidCount(), AcbTime.iso(c.getCountedOn()), c.getSource(),
                c.getEnteredBy(), AcbTime.iso(c.getEnteredAt())));
    }

    @Override
    public AcbUnpaidCountsResultDto capture(UnpaidCountsRequest request, String actor) {
        return capture(request, actor, AcbTime.instant(clock));
    }

    /** The capture as at {@code at} -- the seed's past, or now. */
    @Override
    public AcbUnpaidCountsResultDto capture(UnpaidCountsRequest request, String actor, Instant at) {
        String source = request.source().trim();
        List<UnpaidCountsRequest.Entry> entries = request.entries();
        List<String> problems = new ArrayList<>();
        Set<String> numbers = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            UnpaidCountsRequest.Entry entry = entries.get(i);
            String number = entry == null || entry.membershipNumber() == null ? "" : entry.membershipNumber().trim();
            if (number.isEmpty() || number.length() > MEMBERSHIP_NUMBER_MAX) {
                problems.add("row " + (i + 1) + ": a membership number is needed, at most " + MEMBERSHIP_NUMBER_MAX
                        + " characters");
            } else if (!numbers.add(number)) {
                problems.add("row " + (i + 1) + ": that member is on the list twice");
            }
            Integer count = entry == null ? null : entry.unpaidCount();
            if (count == null || count < 0 || count > UnpaidCounting.LIMIT) {
                problems.add("row " + (i + 1) + ": a count is a whole number from 0 to " + UnpaidCounting.LIMIT);
            }
        }
        requireNone(problems);

        Map<String, MemberCountRow> members = new HashMap<>();
        List<String> all = new ArrayList<>(numbers);
        for (int start = 0; start < all.size(); start += NUMBERS_PER_QUERY) {
            population.findCountRowsByNumbers(all.subList(start, Math.min(start + NUMBERS_PER_QUERY, all.size())))
                    .forEach(row -> members.putIfAbsent(row.membershipNumber(), row));
        }
        for (int i = 0; i < entries.size(); i++) {
            String number = entries.get(i).membershipNumber().trim();
            if (!members.containsKey(number)) {
                problems.add("row " + (i + 1) + ": no member has that membership number");
            }
        }
        requireNone(problems);

        LocalDate countedOn = LocalDate.ofInstant(at, AcbTime.ZONE);
        try {
            return tx.execute(status -> write(entries, members, source, countedOn, actor, at));
        } catch (DataIntegrityViolationException capturedMeanwhile) {
            // Another capture of the same members committed between the read and the insert: the unique key on the
            // member is the arbiter. Nothing of this one was kept.
            problems.add("another list naming the same members was captured at the same moment; capture this one again");
            requireNone(problems);
            throw capturedMeanwhile;
        }
    }

    private AcbUnpaidCountsResultDto write(List<UnpaidCountsRequest.Entry> entries, Map<String, MemberCountRow> members,
                                           String source, LocalDate countedOn, String actor, Instant at) {
        Map<Integer, AcbUnpaidCount> onRecord = new HashMap<>();
        counts.findByMemberIdIn(members.values().stream().map(MemberCountRow::id).toList())
                .forEach(c -> onRecord.put(c.getMemberId(), c));
        List<Object[]> updates = new ArrayList<>();
        List<Object[]> inserts = new ArrayList<>();
        int unchanged = 0;
        int atThree = 0;
        Timestamp enteredAt = Timestamp.from(at);
        for (UnpaidCountsRequest.Entry entry : entries) {
            MemberCountRow member = members.get(entry.membershipNumber().trim());
            int count = entry.unpaidCount();
            atThree += count >= UnpaidCounting.LIMIT ? 1 : 0;
            AcbUnpaidCount existing = onRecord.get(member.id());
            if (existing != null && existing.getUnpaidCount() == count && countedOn.equals(existing.getCountedOn())) {
                unchanged++;
            } else if (existing != null) {
                updates.add(new Object[] {count, Date.valueOf(countedOn), source, actor, enteredAt, member.id()});
            } else {
                inserts.add(new Object[] {member.id(), member.membershipNumber(), count, Date.valueOf(countedOn),
                        source, actor, enteredAt});
            }
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate("UPDATE acb_unpaid_counts SET unpaid_count = ?, counted_on = ?, source = ?, entered_by = ?, "
                    + "entered_at = ? WHERE member_id = ?", updates);
        }
        if (!inserts.isEmpty()) {
            jdbc.batchUpdate("INSERT INTO acb_unpaid_counts (member_id, membership_number, unpaid_count, counted_on, "
                    + "source, entered_by, entered_at) VALUES (?, ?, ?, ?, ?, ?, ?)", inserts);
        }
        int captured = updates.size() + inserts.size();
        if (captured > 0) {
            // 🔒 How many, never whose, and never the source's words (SD3-D).
            audit.recordRunless(AcbRunlessEvent.UNPAID_COUNTS_CAPTURED, captured + " member(s)' unpaid counts captured as at "
                    + countedOn + ", " + unchanged + " unchanged, " + atThree + " at three", actor);
        }
        return new AcbUnpaidCountsResultDto(captured, unchanged, countedOn.toString(), atThree);
    }

    /** A list's refusal: every problem, named by row (EH2-E). */
    private static void requireNone(List<String> problems) {
        AcbReturnsServiceImpl.requireNoProblems(problems, AcbRefusalCode.UNPAID_COUNTS_INVALID,
                "This list of unpaid counts has problems");
    }
}

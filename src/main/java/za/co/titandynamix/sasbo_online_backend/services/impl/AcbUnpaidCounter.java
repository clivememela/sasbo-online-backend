package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.MemberCountRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.OpeningRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.TerminationRule;
import za.co.titandynamix.sasbo_online_backend.acb.returns.UnpaidCounting;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCount;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCountRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Members' unpaid counts, read from the database and judged by {@link UnpaidCounting} -- for one member (a re-debit
 * refused at three, an ending confirmed) or for everyone the count has anything to say about (the termination report,
 * the run that leaves a member at three out). Both ask the same rule of the same rows, so the report and a refusal can
 * never disagree about a member.
 *
 * <h2>Bounded, and never a query per member (SD3-K, R3-17)</h2>
 * Everyone's count is five statements whatever the ledger: the window's debits, the opening position, the captured
 * counts, and the members' rows, a thousand ids at a time. One member's is four.
 */
@Component
public class AcbUnpaidCounter {

    /** The months the count reads: the clock's own and the twelve before it (SD3-K). */
    static final int WINDOW_MONTHS = 13;

    /** Ids asked for in one statement -- far below every driver's parameter limit. */
    private static final int IDS_PER_QUERY = 1_000;

    private final AcbReturnsRepository returns;
    private final AcbPopulationRepository population;
    private final AcbUnpaidCountRepository captured;
    private final Clock clock;

    public AcbUnpaidCounter(AcbReturnsRepository returns, AcbPopulationRepository population,
                            AcbUnpaidCountRepository captured, Clock clock) {
        this.returns = returns;
        this.population = population;
        this.captured = captured;
        this.clock = clock;
    }

    /**
     * A member as the rules count them, with what the report prints.
     *
     * @param openingUnpaids   the opening position's unpaids, 0 where it does not name the member (D24)
     * @param capturedUnpaids  the count captured by hand, null where none is in the window (DOD-28)
     * @param twelveMonthTotal unpaids over the last twelve months, whatever paid in between
     */
    public record Counted(Integer memberId, String membershipNumber, String surname, String status,
                          UnpaidCounting.Count count, int openingUnpaids, Integer capturedUnpaids,
                          int twelveMonthTotal) {
    }

    /** The first day the count reads. */
    LocalDate windowStart() {
        return AcbTime.currentMonth(clock).minusMonths(WINDOW_MONTHS - 1L).atDay(1);
    }

    /** One member's count now: four statements. */
    public Counted of(int memberId) {
        LocalDate from = windowStart();
        List<PresentationRow> history = returns.findHistoryOf(memberId, AcbRuns.SENT, from);
        Optional<OpeningRow> opening = returns.findOpeningPositionOf(memberId);
        Optional<AcbUnpaidCount> capture = captured.findByMemberId(memberId)
                .filter(c -> !c.getCountedOn().isBefore(from));
        MemberCountRow row = population.findCountRows(List.of(memberId)).stream().findFirst().orElse(null);
        return judge(memberId, history, opening.orElse(null), capture.orElse(null), row);
    }

    /**
     * Everyone the count has anything to say about -- an unpaid in the window, an opening position, or a count
     * captured in the window -- by member id.
     */
    public Map<Integer, Counted> everyone() {
        LocalDate from = windowStart();
        Map<Integer, List<PresentationRow>> histories = returns.findHistoriesToCount(AcbRuns.SENT,
                        AcbLineOutcome.UNPAID, from).stream()
                .collect(Collectors.groupingBy(PresentationRow::memberId, TreeMap::new, Collectors.toList()));
        Map<Integer, OpeningRow> opening = returns.findOpeningPosition().stream()
                .collect(Collectors.toMap(OpeningRow::memberId, r -> r, (a, b) -> a));
        Map<Integer, AcbUnpaidCount> captures = captured.findByCountedOnGreaterThanEqual(from).stream()
                .collect(Collectors.toMap(AcbUnpaidCount::getMemberId, c -> c, (a, b) -> a));
        Set<Integer> ids = new TreeSet<>(histories.keySet());
        ids.addAll(opening.keySet());
        ids.addAll(captures.keySet());
        Map<Integer, MemberCountRow> rows = rows(ids);
        Map<Integer, Counted> counted = new TreeMap<>();
        for (Integer id : ids) {
            counted.put(id, judge(id, histories.getOrDefault(id, List.of()), opening.get(id), captures.get(id),
                    rows.get(id)));
        }
        return counted;
    }

    /** The members at three -- whom a run leaves out (DOD-03). */
    public Set<Integer> atTheLimit() {
        return everyone().values().stream().filter(c -> c.count().atTheLimit()).map(Counted::memberId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    // ------------------------------------------------------------------------------------------

    private Counted judge(Integer memberId, List<PresentationRow> history, OpeningRow opening, AcbUnpaidCount capture,
                         MemberCountRow row) {
        List<TerminationRule.Presentation> presentations = history.stream().map(PresentationRow::presentation)
                .toList();
        UnpaidCounting.Count count = UnpaidCounting.count(presentations,
                opening == null ? null : new UnpaidCounting.Opening(opening.unpaidsStanding(), opening.lastUnpaidMonth()),
                capture == null ? null : new UnpaidCounting.Captured(capture.getUnpaidCount(), capture.getCountedOn()),
                row == null ? null : row.joinDate());
        YearMonth twelveFrom = AcbTime.currentMonth(clock).minusMonths(11);
        int twelveMonthTotal = (int) count.counted().stream()
                .filter(p -> p.outcome() == AcbLineOutcome.UNPAID && !YearMonth.from(p.actionDate()).isBefore(twelveFrom))
                .count();
        String number = row != null ? row.membershipNumber() : snapshotNumber(history, opening, capture);
        String surname = row != null ? row.surname() : snapshotSurname(history, opening);
        return new Counted(memberId, number, surname, row == null ? null : row.status(), count,
                opening == null ? 0 : opening.unpaidsStanding(), capture == null ? null : capture.getUnpaidCount(),
                twelveMonthTotal);
    }

    private Map<Integer, MemberCountRow> rows(Collection<Integer> ids) {
        Map<Integer, MemberCountRow> rows = new HashMap<>();
        List<Integer> all = new ArrayList<>(ids);
        for (int start = 0; start < all.size(); start += IDS_PER_QUERY) {
            population.findCountRows(all.subList(start, Math.min(start + IDS_PER_QUERY, all.size())))
                    .forEach(row -> rows.put(row.id(), row));
        }
        return rows;
    }

    private static String snapshotNumber(List<PresentationRow> history, OpeningRow opening, AcbUnpaidCount capture) {
        if (!history.isEmpty()) {
            return history.get(history.size() - 1).membershipNumber();
        }
        if (opening != null) {
            return opening.membershipNumber();
        }
        return capture == null ? null : capture.getMembershipNumber();
    }

    private static String snapshotSurname(List<PresentationRow> history, OpeningRow opening) {
        if (!history.isEmpty()) {
            return history.get(history.size() - 1).surname();
        }
        return opening == null ? null : opening.surname();
    }
}

package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningMemberRow;
import za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningPositionSheet;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbOpeningPositionDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbOpeningPosition;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbOpeningPositionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The opening arrears position (D24): a sheet of tens of rows, validated whole, stored whole or not
 * at all, replaced whole until the first returns are in -- and never after.
 *
 * <p>🔒 Nothing of a row reaches a log line or an audit row: the one audit row says how many members
 * and which months, never a membership number (SD4-A). A refusal names rows and fields, never values.
 */
@Service
public class AcbOpeningPositionServiceImpl implements AcbOpeningPositionService {

    /** At most this many problems are named in a refusal; the total is always given. */
    static final int PROBLEMS_SHOWN = 50;

    /** Where every captured row came from. */
    static final String SOURCE = "the legacy membership profile, captured by hand at switch-over";

    /** A month older than this many months before today is refused: the rule counts recent unpaids. */
    static final int OLDEST_MONTHS = 12;

    private final AcbOpeningPositionRepository positions;
    private final AcbPopulationRepository population;
    private final AcbRunRepository runs;
    private final AcbRateRepository rates;
    private final AcbAuditTrail audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbOpeningPositionServiceImpl(AcbOpeningPositionRepository positions, AcbPopulationRepository population,
                                         AcbRunRepository runs, AcbRateRepository rates, AcbAuditTrail audit,
                                         JdbcTemplate jdbc, Clock clock, PlatformTransactionManager transactions) {
        this.positions = positions;
        this.population = population;
        this.runs = runs;
        this.rates = rates;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public AcbOpeningPositionDto current() {
        List<AcbOpeningPosition> all = positions.findAll();
        return dto(all, all.isEmpty() ? null : all.get(0));
    }

    @Override
    public AcbOpeningPositionDto capture(byte[] sheet, String actor) {
        OpeningPositionSheet.Read read = OpeningPositionSheet.read(sheet);
        List<OpeningPositionSheet.Problem> problems = new ArrayList<>(read.problems());
        Map<String, OpeningMemberRow> members = population.findByMembershipNumbers(read.lines().stream()
                        .map(OpeningPositionSheet.Line::membershipNumber).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(OpeningMemberRow::membershipNumber, Function.identity(), (a, b) -> a));
        YearMonth today = AcbTime.currentMonth(clock);
        LocalDate firstSent = runs.firstMonthIn(AcbRuns.SENT);
        for (OpeningPositionSheet.Line line : read.lines()) {
            if (line.membershipNumber() != null) {
                OpeningMemberRow member = members.get(line.membershipNumber());
                if (member == null) {
                    problems.add(new OpeningPositionSheet.Problem(line.row(), "membershipNumber", "no member on record has it"));
                } else if (!"debit order".equalsIgnoreCase(Objects.toString(member.paymentType(), "").trim())) {
                    problems.add(new OpeningPositionSheet.Problem(line.row(), "membershipNumber", "the member does not pay by debit order"));
                }
            }
            if (line.unpaidsStanding() != null && (line.unpaidsStanding() < 1 || line.unpaidsStanding() > 2)) {
                problems.add(new OpeningPositionSheet.Problem(line.row(), "unpaidsStanding",
                        "one or two -- three would already have terminated the member"));
            }
            YearMonth month = line.lastUnpaidMonth();
            if (month != null) {
                if (month.isAfter(today)) {
                    problems.add(new OpeningPositionSheet.Problem(line.row(), "lastUnpaidMonth", "in the future"));
                } else if (month.isBefore(today.minusMonths(OLDEST_MONTHS))) {
                    problems.add(new OpeningPositionSheet.Problem(line.row(), "lastUnpaidMonth", "more than a year ago"));
                } else if (firstSent != null && !month.isBefore(YearMonth.from(firstSent))) {
                    // SD3-J: this system's own ledger already holds that month; counted twice otherwise.
                    problems.add(new OpeningPositionSheet.Problem(line.row(), "lastUnpaidMonth",
                            "not before " + YearMonth.from(firstSent) + ", the first month this system sent a file"));
                }
            }
        }
        if (!problems.isEmpty()) {
            throw invalid(problems);
        }
        Instant now = AcbTime.instant(clock);
        tx.executeWithoutResult(status -> {
            requireOpen();
            positions.deleteEverything();
            for (OpeningPositionSheet.Line line : read.lines()) {
                AcbOpeningPosition row = new AcbOpeningPosition();
                row.setMemberId(members.get(line.membershipNumber()).id());
                row.setMembershipNumber(line.membershipNumber());
                row.setUnpaidsStanding(line.unpaidsStanding());
                row.setLastUnpaidMonth(line.lastUnpaidMonth().atDay(1));
                row.setSource(SOURCE);
                row.setEnteredBy(actor);
                row.setEnteredAt(now);
                positions.save(row);
            }
            YearMonth earliest = read.lines().stream().map(OpeningPositionSheet.Line::lastUnpaidMonth)
                    .min(Comparator.naturalOrder()).orElseThrow();
            YearMonth latest = read.lines().stream().map(OpeningPositionSheet.Line::lastUnpaidMonth)
                    .max(Comparator.naturalOrder()).orElseThrow();
            // 🔒 How many and which months -- never a member's number (SD4-A).
            audit.recordRunless(AcbRunlessEvent.OPENING_POSITION_CAPTURED, read.lines().size()
                    + " member(s), last unpaid months " + earliest + " to " + latest, actor);
        });
        return current();
    }

    /** The first time any run's returns were taken in, or {@code null} while none has been. */
    private Instant lockedSince() {
        return jdbc.query("SELECT min(occurred_at) FROM acb_audit_events WHERE kind = 'RECORD_RETURNS'",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class)).stream()
                .filter(Objects::nonNull).map(OffsetDateTime::toInstant).findFirst().orElse(null);
    }

    private void requireOpen() {
        Instant since = lockedSince();
        if (since != null) {
            throw new AcbRefusal(AcbRefusalCode.OPENING_POSITION_LOCKED, "The opening position is fixed: the first "
                    + "run's returns were taken in on " + AcbTime.iso(since) + ", and from then it is counted.",
                    Map.of("lockedSince", AcbTime.iso(since)));
        }
    }

    private AcbOpeningPositionDto dto(List<AcbOpeningPosition> all, AcbOpeningPosition latest) {
        Instant since = lockedSince();
        List<AcbOpeningPositionDto.Disagreement> disagreements = new ArrayList<>();
        if (!all.isEmpty()) {
            Map<String, OpeningMemberRow> members = population.findByMembershipNumbers(
                    all.stream().map(AcbOpeningPosition::getMembershipNumber).toList()).stream()
                    .collect(Collectors.toMap(OpeningMemberRow::membershipNumber, Function.identity(), (a, b) -> a));
            for (AcbOpeningPosition row : all) {
                OpeningMemberRow member = members.get(row.getMembershipNumber());
                BigDecimal balance = member == null ? null : member.arrearsBalance();
                BigDecimal expected = rateFor(YearMonth.from(row.getLastUnpaidMonth()))
                        .multiply(BigDecimal.valueOf(row.getUnpaidsStanding()));
                if (balance == null || balance.compareTo(expected) != 0) {
                    disagreements.add(new AcbOpeningPositionDto.Disagreement(row.getMembershipNumber(),
                            row.getUnpaidsStanding(), balance == null ? null : balance.toPlainString()));
                }
            }
        }
        return new AcbOpeningPositionDto(all.size(), latest == null ? null : latest.getEnteredBy(),
                latest == null ? null : AcbTime.iso(latest.getEnteredAt()), since != null, AcbTime.iso(since),
                List.copyOf(disagreements));
    }

    private BigDecimal rateFor(YearMonth month) {
        return rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(month.atDay(1))
                .map(AcbRate::getAmount).orElse(BigDecimal.ZERO);
    }

    private static AcbRefusal invalid(List<OpeningPositionSheet.Problem> problems) {
        List<OpeningPositionSheet.Problem> sorted = new ArrayList<>(problems);
        sorted.sort(Comparator.comparingInt(OpeningPositionSheet.Problem::row));
        Map<String, String> details = new LinkedHashMap<>();
        details.put("totalProblems", String.valueOf(sorted.size()));
        details.put("rows", sorted.stream().limit(PROBLEMS_SHOWN)
                .map(p -> (p.row() == 0 ? "sheet" : "row " + p.row()) + " " + p.field() + ": " + p.problem())
                .collect(Collectors.joining("; ")));
        return new AcbRefusal(AcbRefusalCode.OPENING_POSITION_INVALID, sorted.size() + " problem(s) in the sheet, "
                + "and nothing of it was stored. Correct each named row and send the whole sheet again.", details);
    }
}

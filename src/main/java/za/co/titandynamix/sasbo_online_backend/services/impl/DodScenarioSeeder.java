package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;
import za.co.titandynamix.sasbo_online_backend.enums.AcbHolidayKind;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The Debit Order Deductions demonstration: Stephne's rules of 6 October 2026, each to be seen working on a fresh
 * stack and on {@code dev.sasbo.online} alike -- with no zeros (the owner, 6 October: "some good seed data that
 * reflect all of these work").
 *
 * <h2>What it adds to the seeded debit-order history (its two months, one closed and one returned)</h2>
 * <ul>
 *   <li>late returns on the later month, one bank report per debit day (DOD-06), under the codes on her sheet: 6 and
 *       15 and 26 (provisional: nobody has said whether they may be re-debited), 30 (never), and 2 -- two members
 *       whose month's debit and re-debit both come back, reaching three unpaids (DOD-03);</li>
 *   <li>one of the two has the ending confirmed by a staff member and the short letter (DOD-22, DOD-24); the other
 *       is left on the report for the ending to be confirmed;</li>
 *   <li>suspensions with SASBO's codes from her sheet: bank 15 is SASBO 15, bank 26 is 19, bank 30 is 30;</li>
 *   <li>a member suspended for a stopped payment (bank 4, SASBO 16) and made active again: only the current month is
 *       collected, so their waiting re-debit is withdrawn (DOD-25, DOD-26);</li>
 *   <li>a bank's debit-order members moved to a new debit day in one step (DOD-19);</li>
 *   <li>unpaid counts captured by hand (DOD-28) -- one member at two, on the report;</li>
 *   <li>a day the bank was closed, in the non-working days list (DOD-17), in a month with no run.</li>
 * </ul>
 * Members are the generated debit-order population's ({@code M-099851} to {@code M-100000}) -- invented people,
 * chosen by the seeded history's own rule ({@link SeededHistory.Population}). Every status change is dated on the
 * last day of the month before the clock's, so the clock's own month stays empty for a live demonstration; the
 * history seeder's months move with the clock, and so does this.
 *
 * <h2>When, and when never</h2>
 * Never where the production marker is set. Once: a run-less {@code DOD_SEED_COMPLETE} event, written last in the
 * same transaction as everything else, says it is done -- so it is all there or none of it is. It needs the seeded
 * history in its two states (CLOSED, RETURNED) and V70's codes; a database without them is left alone with one INFO
 * line. On a database started before this existed, it runs at the next start.
 */
@Slf4j
@Component
public class DodScenarioSeeder {

    /** The actor on every row the demonstration writes -- the seed's, as the history's. */
    static final String ACTOR = SeededHistory.ACTOR;

    /** The bank whose members the demonstration moves, and the day it moves them to. */
    static final String MOVED_BANK = "African Bank Limited";
    static final int MOVED_TO_DAY = 27;

    /** The codes and SASBO's suspension codes the scenarios use -- V70's rows. */
    private static final List<String> BANK_CODES = List.of("2", "4", "6", "15", "26", "30");
    private static final List<Integer> SUSPENSION_CODES = List.of(13, 15, 16, 19, 30);

    /** The population index the last scenario member is read from. */
    private static final int MEMBERS_NEEDED = 73;

    private final SasboEnvironment environment;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final AcbReturnsService returns;
    private final AcbUnpaidCountService counts;
    private final AcbDebitDayService debitDays;
    private final AcbCalendarService calendar;
    private final HolidayCalendarSource calendars;
    private final AcbAuditTrail audit;

    public DodScenarioSeeder(SasboEnvironment environment, JdbcTemplate jdbc, PlatformTransactionManager transactions,
                             Clock clock, AcbReturnsService returns, AcbUnpaidCountService counts,
                             AcbDebitDayService debitDays, AcbCalendarService calendar,
                             HolidayCalendarSource calendars, AcbAuditTrail audit) {
        this.environment = environment;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
        this.clock = clock;
        this.returns = returns;
        this.counts = counts;
        this.debitDays = debitDays;
        this.calendar = calendar;
        this.calendars = calendars;
        this.audit = audit;
    }

    /** What a call did. */
    public enum Outcome { SEEDED, PRODUCTION, ALREADY_SEEDED, NO_HISTORY, SKIPPED, RACED }

    /**
     * @param statusChanges member status changes written into the month before the clock's -- what month end's take
     *                      seeder needs to know to take that month again (contracts/demo-population.md section 5)
     */
    public record Result(Outcome outcome, int statusChanges) {
    }

    private record SeededRun(long id, LocalDate actionMonth, String status) {
    }

    private record Line(long id, LocalDate actionDate, int memberId) {
    }

    /** Seeds the demonstration once, wherever the production marker is not set. Called by {@code DataSeeder}. */
    public Result seed() {
        if (environment.isProduction()) {
            return new Result(Outcome.PRODUCTION, 0);
        }
        Long done = jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL AND kind = ?",
                Long.class, AcbRunlessEvent.DOD_SEED_COMPLETE.name());
        if (done != null && done > 0) {
            return new Result(Outcome.ALREADY_SEEDED, 0);
        }
        List<SeededRun> seeded = jdbc.query("SELECT id, action_month, status FROM acb_runs WHERE created_by = ? "
                        + "ORDER BY action_month",
                (rs, n) -> new SeededRun(rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getString(3)), ACTOR);
        if (seeded.size() != 2 || !"CLOSED".equals(seeded.get(0).status()) || !"RETURNED".equals(seeded.get(1).status())) {
            log.info("The Debit Order demonstration is not seeded: it needs the seeded history's two months, one closed "
                    + "and one returned, and this database holds {} seeded run(s)", seeded.size());
            return new Result(Outcome.NO_HISTORY, 0);
        }
        Optional<String> missing = missingPrerequisite();
        if (missing.isPresent()) {
            log.info("The Debit Order demonstration is not seeded: {}", missing.get());
            return new Result(Outcome.SKIPPED, 0);
        }
        SeededHistory.Population population = SeededHistory.Population.read();
        if (population.withAddress().size() < MEMBERS_NEEDED) {
            log.info("The Debit Order demonstration is not seeded: the debit-order seed has {} members with an address",
                    population.withAddress().size());
            return new Result(Outcome.SKIPPED, 0);
        }
        try {
            int changes = Objects.requireNonNull(tx.execute(status -> scenarios(seeded.get(0), seeded.get(1),
                    population.withAddress())));
            log.info("Seeded the Debit Order demonstration: {} member status change(s) in {}", changes,
                    YearMonth.from(seeded.get(1).actionMonth()));
            return new Result(Outcome.SEEDED, changes);
        } catch (DataIntegrityViolationException beaten) {
            log.info("The Debit Order demonstration was seeded by another instance starting at the same time");
            return new Result(Outcome.RACED, 0);
        }
    }

    /** What the scenarios need that only V70 writes -- absent in the fast lane's H2, for one. */
    private Optional<String> missingPrerequisite() {
        for (String code : BANK_CODES) {
            Long found = jdbc.queryForObject("SELECT count(*) FROM acb_unpaid_codes WHERE code = ?", Long.class, code);
            if (found == null || found == 0) {
                return Optional.of("the bank's code " + code + " is not in the codes table (V70)");
            }
        }
        for (Integer code : SUSPENSION_CODES) {
            Long found = jdbc.queryForObject("SELECT count(*) FROM suspension_codes WHERE code = ?", Long.class, code);
            if (found == null || found == 0) {
                return Optional.of("SASBO's suspension code " + code + " is not on record (V70)");
            }
        }
        Long bank = jdbc.queryForObject("SELECT count(*) FROM banks WHERE LOWER(TRIM(name)) = LOWER(?)", Long.class,
                MOVED_BANK);
        return bank == null || bank == 0 ? Optional.of("the bank " + MOVED_BANK + " is not on record") : Optional.empty();
    }

    /** Everything, in the caller's one transaction. Answers how many member status changes it wrote. */
    private int scenarios(SeededRun earlier, SeededRun later, List<String> members) {
        YearMonth month = YearMonth.from(later.actionMonth());
        LocalDate lastDay = month.atEndOfMonth();
        String endedMember = members.get(3);
        String atThreeMember = members.get(11);
        String reactivatedMember = members.get(35);
        int changes = 0;

        // 1. The later month's late returns, one report per debit day (DOD-06): a member's month's debit and its
        //    re-debit both back makes three (question 27, DOD-03) -- at three nothing is presented again, so SUSPEND.
        List<Object[]> late = new ArrayList<>();
        addLate(late, later.id(), endedMember, false, "2", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), endedMember, true, "2", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), atThreeMember, false, "2", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), atThreeMember, true, "2", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), members.get(62), false, "6", null);
        addLate(late, later.id(), members.get(63), false, "15", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), members.get(64), false, "26", UnpaidDecision.SUSPEND);
        addLate(late, later.id(), members.get(65), false, "30", UnpaidDecision.SUSPEND);
        Map<LocalDate, List<ReturnsBatchRequest.Entry>> byDay = new TreeMap<>();
        Map<Long, LocalDate> reportedOn = new TreeMap<>();
        for (Object[] row : late) {
            Line line = (Line) row[0];
            byDay.computeIfAbsent(line.actionDate(), d -> new ArrayList<>())
                    .add(new ReturnsBatchRequest.Entry(line.id(), null, (String) row[1], (UnpaidDecision) row[2]));
            reportedOn.put(line.id(), SeededHistory.notedOn(line.actionDate(), month));
        }
        byDay.forEach((day, entries) -> returns.recordReturnsBatch(later.id(),
                new ReturnsBatchRequest(day.toString(), SeededHistory.notedOn(day, month).toString(), entries), ACTOR));
        // Seeded letters are SENT in their own past month (F62): never the first mail a member gets, unasked.
        reportedOn.forEach((lineId, day) -> markSent(lineId, at(day, 10, 0)));

        // 2. Three unpaids: one ending confirmed by a staff member, with its short letter (DOD-22, DOD-24); the other
        //    left on the report for a person to confirm.
        AcbMemberStatusDto ended = returns.terminate(memberId(endedMember), ACTOR, at(lastDay, 12, 0));
        changes++;
        if (ended.letterId() != null) {
            jdbc.update("UPDATE acb_letters SET status = 'SENT', rendered_at = ?, sent_at = ? WHERE id = ? AND status = "
                    + "'RENDERED'", Timestamp.from(at(lastDay, 12, 0)), Timestamp.from(at(lastDay, 12, 0)), ended.letterId());
        }

        // 3. Suspended with SASBO's codes from her sheet: bank 15 -> 15, 26 -> 19, 30 -> 30 (DOD-01, DOD-12).
        for (int index = 63; index <= 65; index++) {
            Line line = ownLine(later.id(), members.get(index)).orElse(null);
            if (line != null) {
                returns.suspendMember(later.id(), line.id(), null, ACTOR, at(lastDay, 9, 30));
                changes++;
            }
        }

        // 4. Suspended for the earlier month's stopped payment (bank 4, SASBO 16), then made active again: only the
        //    current month is collected, so the later month's re-debit waiting for them is withdrawn (DOD-26).
        Optional<Line> stopped = unpaidOwnLine(earlier.id(), reactivatedMember);
        if (stopped.isPresent()) {
            returns.suspendMember(earlier.id(), stopped.get().id(), null, ACTOR, at(lastDay, 10, 0));
            returns.reactivate(stopped.get().memberId(), new MemberReturnRequest(null, null), ACTOR, at(lastDay, 11, 0));
            changes += 2;
        }

        // 5. A bank's debit-order members moved to a new day in one step (DOD-19), after the month's last debit.
        Integer bankId = jdbc.queryForObject("SELECT MIN(id) FROM banks WHERE LOWER(TRIM(name)) = LOWER(?)",
                Integer.class, MOVED_BANK);
        AcbDebitDayMoveDto preview = debitDays.preview(Objects.requireNonNull(bankId), MOVED_TO_DAY);
        if (preview.toMove() > 0) {
            debitDays.move(bankId, new DebitDayMoveRequest(MOVED_TO_DAY, preview.toMove()), ACTOR, at(lastDay, 15, 0));
        }

        // 6. Unpaid counts captured by hand (DOD-28): one member at two, one at one, one cleared.
        counts.capture(new UnpaidCountsRequest("Re-debit list (demonstration)", List.of(
                new UnpaidCountsRequest.Entry(members.get(70), 2),
                new UnpaidCountsRequest.Entry(members.get(71), 1),
                new UnpaidCountsRequest.Entry(members.get(72), 0))), ACTOR, at(lastDay, 16, 0));

        // 7. A day the bank was closed (DOD-17), in a month no run covers, so no run's dates move.
        LocalDate closed = closureDay(AcbTime.currentMonth(clock).minusMonths(5));
        Long listed = jdbc.queryForObject("SELECT count(*) FROM acb_holidays WHERE holiday_date = ?", Long.class, closed);
        if (listed != null && listed == 0) {
            calendar.add(closed.toString(), "Demonstration: the bank closed for a systems upgrade",
                    AcbHolidayKind.BANK_CLOSED, ACTOR);
        }

        audit.recordRunless(AcbRunlessEvent.DOD_SEED_COMPLETE, "the Debit Order demonstration seeded in " + month,
                ACTOR);
        return changes;
    }

    // ------------------------------------------------------------------------------------------

    private void addLate(List<Object[]> late, long runId, String membershipNumber, boolean reDebit, String code,
                         UnpaidDecision decision) {
        Optional<Line> line = reDebit ? reDebitLine(runId, membershipNumber) : ownLine(runId, membershipNumber);
        line.ifPresent(found -> late.add(new Object[] {found, code, decision}));
    }

    private Optional<Line> ownLine(long runId, String membershipNumber) {
        return line("SELECT id, action_date, member_id FROM acb_run_lines WHERE run_id = ? AND membership_number = ? "
                + "AND re_presents_line_id IS NULL", runId, membershipNumber);
    }

    private Optional<Line> reDebitLine(long runId, String membershipNumber) {
        return line("SELECT id, action_date, member_id FROM acb_run_lines WHERE run_id = ? AND membership_number = ? "
                + "AND re_presents_line_id IS NOT NULL ORDER BY id", runId, membershipNumber);
    }

    private Optional<Line> unpaidOwnLine(long runId, String membershipNumber) {
        return line("SELECT id, action_date, member_id FROM acb_run_lines WHERE run_id = ? AND membership_number = ? "
                + "AND re_presents_line_id IS NULL AND outcome = 'UNPAID' AND decision = 'SUSPEND'", runId,
                membershipNumber);
    }

    private Optional<Line> line(String sql, Object... arguments) {
        return jdbc.query(sql, (rs, n) -> new Line(rs.getLong(1), rs.getDate(2).toLocalDate(), rs.getInt(3)), arguments)
                .stream().findFirst();
    }

    private int memberId(String membershipNumber) {
        Integer id = jdbc.queryForObject("SELECT MIN(id) FROM members WHERE membership_number = ?", Integer.class,
                membershipNumber);
        return Objects.requireNonNull(id, "the seed member is not on record");
    }

    private void markSent(long lineId, Instant at) {
        Timestamp when = Timestamp.from(at);
        int sent = jdbc.update("UPDATE acb_letters SET status = 'SENT', rendered_at = ?, sent_at = ? WHERE line_id = ? "
                + "AND status = 'RENDERED'", when, when, lineId);
        if (sent > 0) {
            Long runId = jdbc.queryForObject("SELECT run_id FROM acb_run_lines WHERE id = ?", Long.class, lineId);
            audit.record(runId, lineId, "LETTER_SENT", null, null, "seeded as sent -- no mail left this system", ACTOR);
        }
    }

    /** The first business day on or after the 15th of {@code month}. */
    private LocalDate closureDay(YearMonth month) {
        HolidayCalendar known = calendars.load();
        LocalDate day = month.atDay(15);
        while (!known.isBusinessDay(day)) {
            day = day.plusDays(1);
        }
        return day;
    }

    private static Instant at(LocalDate day, int hour, int minute) {
        return day.atTime(LocalTime.of(hour, minute)).atZone(AcbTime.ZONE).toInstant();
    }
}

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
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunAction;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.utils.SeedDebitOrderMember;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The seed's A.C.B. history: the two months before the clock's, on record -- one {@code CLOSED}, one
 * {@code RETURNED} with its unpaids, decisions and letters (D33, 03.8).
 *
 * <h2>Why a history at all</h2>
 * No run can be approved or submitted through the application until the API knows its callers (B1),
 * so without this the returns half of the module can be proven but never SEEN: the Runs screen opens
 * empty, the re-debits never appear, the termination report has nobody on it. With it, the first
 * live <i>Prepare</i> of this month shows last month's re-debits as second lines, "like a duplication".
 *
 * <h2>How, and why not through the front door (SD4-B)</h2>
 * The service refuses a past month -- a run may be prepared for this month or the next -- and its
 * launcher runs a preparation on one background thread outside tests, so writing the later states
 * over it would race the job. So this seeder is the ONE caller allowed to create a run outside the
 * window, and nothing else reaches it: it writes its two run rows itself and calls the job's
 * {@code prepare(runId)} INLINE, on its own thread -- the same preparer the API's job runs, so the
 * lines are made by the rules, never typed. The states past {@code PREPARED} are written directly,
 * because a file cannot render until members carry an account type (00.5); from {@code SUBMITTED}
 * on, the unpaids, the returns and the close go through the real services. Every audit row it causes
 * says the actor {@value #ACTOR}.
 *
 * <h2>When, and when never</h2>
 * Wherever the production marker is NOT set and no ACB run exists -- so it reaches a database started
 * long ago, as {@code dev.sasbo.online} is (D33 as amended). Never where the marker is set (SD3-A).
 * Months come from the injected clock, so a fresh database's history is always the last two months;
 * on a database that lives for months it simply ages. A month with no rate in force, or beyond what
 * the holiday calendar knows, is skipped with one {@code WARN} that says which (SD4-D).
 *
 * <h2>Finished, or visibly not (SD4-H)</h2>
 * It ends by writing the run-less {@code SEED_COMPLETE} event. A start that finds seeded runs without
 * it says so once, at {@code ERROR}, with the remedy, and touches nothing. Two instances starting
 * together race on the one-live-run-a-month index: the loser was beaten by somebody seeding it.
 */
@Slf4j
@Component
public class AcbHistorySeeder {

    /** The actor on every audit row the seeded history carries. */
    static final String ACTOR = SeededHistory.ACTOR;

    /** A code the unpaid-code table does not know: recorded as OTHER, with no letter. */
    static final String UNKNOWN_CODE = "99";

    private final AcbRunRepository runs;
    private final AcbRateRepository rates;
    private final AcbPreparationJob job;
    private final AcbRunService service;
    private final AcbReturnsService returns;
    private final AcbAuditTrail audit;
    private final HolidayCalendarSource calendars;
    private final SasboEnvironment environment;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public AcbHistorySeeder(AcbRunRepository runs, AcbRateRepository rates, AcbPreparationJob job,
                            AcbRunService service, AcbReturnsService returns, AcbAuditTrail audit,
                            HolidayCalendarSource calendars, SasboEnvironment environment, Clock clock, JdbcTemplate jdbc,
                            PlatformTransactionManager transactions) {
        this.runs = runs;
        this.rates = rates;
        this.job = job;
        this.service = service;
        this.returns = returns;
        this.audit = audit;
        this.calendars = calendars;
        this.environment = environment;
        this.clock = clock;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactions);
    }

    /** What a call did. */
    public enum Outcome {
        /** The two months were written, and SEED_COMPLETE after them. */
        SEEDED,
        /** The production marker is set: nothing synthetic, ever. */
        PRODUCTION,
        /** SEED_COMPLETE is there: seeded before. */
        ALREADY_SEEDED,
        /** A run exists that the seed did not finish -- somebody used the module, or a seed died. */
        RUNS_EXIST,
        /** The months fall outside the rate or the calendar (SD4-D). */
        SKIPPED,
        /** Another instance seeded it first (SD4-H). */
        RACED
    }

    /** Seeds the history for the clock's month. Called last by {@code DataSeeder}, inside its catch (SD3-I). */
    public Outcome seed() {
        return seedBefore(AcbTime.currentMonth(clock));
    }

    /** Seeds the two months before {@code current}. Package-private: the tests pin the month (SD4-D). */
    Outcome seedBefore(YearMonth current) {
        if (environment.isProduction()) {
            return Outcome.PRODUCTION;
        }
        State state = state();
        if (state.complete()) {
            return Outcome.ALREADY_SEEDED;
        }
        if (state.runs() > 0) {
            if (state.seeded() > 0) {
                log.error("The seeded A.C.B. history was started but never finished -- there is no SEED_COMPLETE event. "
                        + "Nothing is touched: rebuild the demo database to seed it again");
            }
            return Outcome.RUNS_EXIST;
        }
        YearMonth earlier = current.minusMonths(2);
        YearMonth later = current.minusMonths(1);
        Optional<String> outside = outsideWhatIsKnown(earlier, later);
        if (outside.isPresent()) {
            log.warn("The seeded A.C.B. history is skipped: {}", outside.get());
            return Outcome.SKIPPED;
        }
        try {
            Plan plan = Plan.of(seedMembers());
            long closed = seedMonth(earlier, plan.earlier(), true);
            long returned = seedMonth(later, plan.later(), false);
            tx.executeWithoutResult(status -> audit.recordRunless(AcbRunlessEvent.SEED_COMPLETE,
                    "seeded history: " + earlier + " closed (run " + closed + "), " + later + " returned (run "
                            + returned + ")", ACTOR));
            log.info("Seeded the A.C.B. history: {} closed, {} returned", earlier, later);
            return Outcome.SEEDED;
        } catch (DataIntegrityViolationException beaten) {
            log.info("The A.C.B. history was seeded by another instance starting at the same time");
            return Outcome.RACED;
        }
    }

    // ------------------------------------------------------------------------------------------
    // One month: its run row, the real preparation, then the states the seed writes itself
    // ------------------------------------------------------------------------------------------

    private long seedMonth(YearMonth month, List<Unpaid> unpaids, boolean close) {
        long runId = createRun(month);
        job.prepare(runId);
        AcbRun prepared = runs.findById(runId).orElseThrow();
        if (prepared.getStatus() != AcbRunStatus.PREPARED) {
            throw new IllegalStateException("the seeded run for " + month + " did not prepare: "
                    + prepared.getStatus() + " -- " + prepared.getFailureReason());
        }
        writeAsSent(runId, month);
        Map<Long, LocalDate> letters = new TreeMap<>();
        for (Unpaid unpaid : unpaids) {
            Optional<Line> line = ownLine(runId, unpaid.membershipNumber());
            if (line.isEmpty()) {
                continue;
            }
            LocalDate notedOn = notedOn(line.get().actionDate(), month);
            returns.recordUnpaid(runId, line.get().id(),
                    new RecordUnpaidRequest(unpaid.code(), notedOn.toString(), unpaid.decision()), ACTOR);
            letters.put(line.get().id(), notedOn);
        }
        markLettersSent(runId, letters);
        AcbRun sent = runs.findById(runId).orElseThrow();
        service.recordReturns(runId, sent.getVersion(), ACTOR, "seeded: the bank's report for " + month);
        if (close) {
            AcbRun returned = runs.findById(runId).orElseThrow();
            service.close(runId, returned.getVersion(), ACTOR, "seeded: every unpaid decided");
        }
        return runId;
    }

    /**
     * When the seed says the bank's report showed a return: two days after the debit, as the report
     * arrives -- but never after its own month ends (F62). The seeded months are the two before the
     * clock's, so a return noted inside its month is always in the past; one noted two days after
     * the 30th of the later month would be dated in the clock's own month, and at 00:30 on the 1st
     * AFTER the clock -- with its letter "sent" in the future.
     */
    static LocalDate notedOn(LocalDate actionDate, YearMonth month) {
        return SeededHistory.notedOn(actionDate, month);
    }

    /**
     * The run row, in {@code PREPARING} -- the one place a run is created outside this month and the
     * next. Package-private, and reached from no controller and no service: a guard says so.
     */
    long createRun(YearMonth month) {
        return Objects.requireNonNull(tx.execute(status -> {
            AcbRun run = new AcbRun();
            run.setActionMonth(month.atDay(1));
            run.setStatus(AcbRunStatus.PREPARING);
            run.setSetupDate(month.atDay(15));
            run.setHolidaySet(calendars.load().name());
            run.setLineCount(0);
            run.setTotalAmount(BigDecimal.ZERO.setScale(2));
            run.setCreatedBy(ACTOR);
            run.setStartedAt(AcbTime.instant(clock));
            run.setHeartbeatAt(AcbTime.instant(clock));
            AcbRun saved = runs.saveAndFlush(run);
            audit.record(saved.getId(), null, "PREPARE", null, AcbRunStatus.PREPARING,
                    "seeded history: preparation of " + month, ACTOR);
            return saved.getId();
        }));
    }

    /**
     * {@code PREPARED} to {@code SUBMITTED}, written by the seed: no file can be made until members
     * carry an account type (00.5), so nothing here pretends one was. The audit rows say so.
     */
    private void writeAsSent(long runId, YearMonth month) {
        tx.executeWithoutResult(status -> {
            AcbRun run = runs.findById(runId).orElseThrow();
            move(run, AcbRunAction.APPROVE, "seeded as approved -- no bureau file was made");
            run.setApprovedBy(ACTOR);
            runs.saveAndFlush(run);
            move(run, AcbRunAction.RECORD_SUBMITTED, "seeded as submitted for " + month + " -- nothing went to any bank");
            runs.saveAndFlush(run);
        });
    }

    private void move(AcbRun run, AcbRunAction action, String reason) {
        AcbRunStatus from = run.getStatus();
        AcbRunStatus to = action.from(from).orElseThrow(() -> AcbRuns.notInState(run, action));
        run.setStatus(to);
        audit.record(run.getId(), null, action.name(), from, to, reason, ACTOR);
    }

    /**
     * A seeded letter is written already SENT, in its own past month (F62): a sender picks up
     * QUEUED and RENDERED letters, and a seeded one must never be the first mail a member gets
     * unasked. A held letter (NO_EMAIL) stays held -- that is what the worklist is for.
     */
    private void markLettersSent(long runId, Map<Long, LocalDate> notedOn) {
        tx.executeWithoutResult(status -> notedOn.forEach((lineId, day) -> {
            Timestamp at = Timestamp.from(day.atTime(LocalTime.of(10, 0)).atZone(AcbTime.ZONE).toInstant());
            int sent = jdbc.update("UPDATE acb_letters SET status = 'SENT', rendered_at = ?, sent_at = ? "
                    + "WHERE line_id = ? AND status = 'RENDERED'", at, at, lineId);
            if (sent > 0) {
                audit.record(runId, lineId, "LETTER_SENT", null, null,
                        "seeded as sent on " + day + " -- no mail left this system", ACTOR);
            }
        }));
    }

    // ------------------------------------------------------------------------------------------
    // What the database holds, and what the seed may assume
    // ------------------------------------------------------------------------------------------

    private record State(long runs, long seeded, boolean complete) {
    }

    /** One query: any run, any run the seed made, and whether the seed finished (SD4-F, SD4-H). */
    private State state() {
        return jdbc.queryForObject("SELECT (SELECT count(*) FROM acb_runs) AS runs, "
                        + "(SELECT count(*) FROM acb_runs WHERE created_by = ?) AS seeded, "
                        + "(SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL AND kind = ?) AS complete",
                (rs, row) -> new State(rs.getLong("runs"), rs.getLong("seeded"), rs.getLong("complete") > 0),
                ACTOR, AcbRunlessEvent.SEED_COMPLETE.name());
    }

    private Optional<String> outsideWhatIsKnown(YearMonth earlier, YearMonth later) {
        try {
            AcbRuns.rateInForce(rates, earlier);
        } catch (AcbRefusal noRate) {
            return Optional.of("no subscription rate is in force for " + earlier
                    + " -- the history is the two months before today's, and no rate is dated that early");
        }
        HolidayCalendar calendar = calendars.load();
        if (!calendar.covers(later)) {
            return Optional.of(calendar.knownUntil()
                    .map(until -> "the holiday calendar is known only until " + until + ", before the end of " + later)
                    .orElse("no holiday calendar is on record, so no month's dates can be computed"));
        }
        return Optional.empty();
    }

    private record Line(long id, LocalDate actionDate) {
    }

    /** The member's own line in the run -- never a re-presentation. */
    private Optional<Line> ownLine(long runId, String membershipNumber) {
        return jdbc.query("SELECT id, action_date FROM acb_run_lines WHERE run_id = ? AND membership_number = ? "
                        + "AND re_presents_line_id IS NULL",
                (rs, row) -> new Line(rs.getLong("id"), rs.getDate("action_date").toLocalDate()),
                runId, membershipNumber).stream().findFirst();
    }

    private static List<SeedDebitOrderMember> seedMembers() {
        return SeededHistory.seedMembers();
    }

    // ------------------------------------------------------------------------------------------
    // Who comes back unpaid, and with what -- chosen by rule from the generated population
    // ------------------------------------------------------------------------------------------

    /** One unpaid the seed records: the member, the bank's code, and SASBO's decision (null: undecided). */
    record Unpaid(String membershipNumber, String code, UnpaidDecision decision) {
    }

    /**
     * The two months' unpaids. The earlier (closed) month: four decided, one of them the member who
     * comes back again. The later (returned) month: eight -- present-again for most, suspend for three
     * (one of them a code the table does not know, which is never re-debited), one undecided (so a month
     * that cannot close can be shown), one to
     * a member with no address (so a held letter can be shown), and that member again: unpaid in both
     * months, one away from the termination report's three.
     */
    record Plan(List<Unpaid> earlier, List<Unpaid> later) {

        static Plan of(List<SeedDebitOrderMember> members) {
            // The same rule the Debit Order demonstration reads (SeededHistory), so both name the same members.
            SeededHistory.Population population = SeededHistory.Population.of(members);
            List<String> withAddress = population.withAddress();
            List<String> withoutAddress = population.withoutAddress();
            if (withAddress.size() < 60 || withoutAddress.isEmpty()) {
                throw new IllegalStateException("the debit-order seed is too small for the seeded history: "
                        + withAddress.size() + " members with an address, " + withoutAddress.size() + " without");
            }
            String twice = withAddress.get(35);
            List<Unpaid> earlier = List.of(
                    new Unpaid(withAddress.get(3), "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(withAddress.get(11), "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(withAddress.get(19), "4", UnpaidDecision.SUSPEND),
                    new Unpaid(withAddress.get(27), "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(twice, "4", UnpaidDecision.SUSPEND));
            List<Unpaid> later = List.of(
                    new Unpaid(twice, "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(withAddress.get(40), "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(withAddress.get(44), "2", UnpaidDecision.RE_PRESENT),
                    new Unpaid(withAddress.get(48), "4", UnpaidDecision.SUSPEND),
                    new Unpaid(withAddress.get(52), "4", UnpaidDecision.SUSPEND),
                    // A code nobody has ruled on is never presented again (Stephne, DOD-03; Friday item 10).
                    new Unpaid(withAddress.get(56), UNKNOWN_CODE, UnpaidDecision.SUSPEND),
                    new Unpaid(withAddress.get(59), "2", null),
                    new Unpaid(withoutAddress.get(0), "2", UnpaidDecision.RE_PRESENT));
            Set<String> distinct = new HashSet<>();
            later.forEach(u -> distinct.add(u.membershipNumber()));
            if (distinct.size() != later.size()) {
                throw new IllegalStateException("the seeded history's later month names a member twice");
            }
            return new Plan(earlier, later);
        }
    }
}

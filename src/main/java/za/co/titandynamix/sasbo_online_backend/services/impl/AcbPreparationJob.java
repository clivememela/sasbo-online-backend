package za.co.titandynamix.sasbo_online_backend.services.impl;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.BankIndex;
import za.co.titandynamix.sasbo_online_backend.acb.DebitWindow;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.acb.MemberDebitRow;
import za.co.titandynamix.sasbo_online_backend.acb.RePresentationCandidate;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Exclusion;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.PreparedLine;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Preparation;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunAction;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prepares a run: reads the population, applies the rules, writes the lines.
 *
 * <h2>A job, because it outlives a request</h2>
 * A preparation walks every member on record. The browser's write ceiling is twenty seconds, and a
 * retry after a timeout would meet the one-live-run-per-month index for a run that may or may not
 * have finished. So the request creates the run in {@code PREPARING} and answers at once, this job
 * does the work, and the screen polls the run until it settles.
 *
 * <h2>🔴 It never exists half-done</h2>
 * Every merge redeploys the service, and a redeploy kills whatever this job is doing. So the lines
 * are computed in memory and written in ONE transaction at the end -- a killed job leaves a
 * {@code PREPARING} run with no lines, never half a run. While it reads, it touches the run's
 * heartbeat once a page; a run whose heartbeat stops is found by {@link #sweep} and failed, audited,
 * and its month can be prepared again.
 *
 * <h2>Statements do not grow with the membership</h2>
 * Banks, subscription rows, the rate, the holiday calendar and the re-presentation candidates are
 * each ONE query, made before any member is read. Members come a page at a time -- nine columns,
 * keyed on the last id -- and the writes are JDBC batches. Nothing here queries per member.
 */
@Slf4j
@Service
public class AcbPreparationJob {

    /** Members per page. A page is also a heartbeat. */
    static final int PAGE_SIZE = 1000;

    /** The first page's cursor: below every id, so the first page starts at the first member. */
    static final int BEFORE_FIRST_MEMBER = Integer.MIN_VALUE;

    /** Rows per JDBC batch. */
    private static final int BATCH_SIZE = 500;

    /**
     * The file's purge date: this many days after the last action date, moved back to a business day
     * in the run's calendar -- SASBO's rule, which reproduces July 2026's 7 August (D31, Q37). The
     * CREATION date is not set here: it is the day the file is made, stamped when it is rendered.
     */
    static final int PURGE_LAG_DAYS = 10;

    private static final String INSERT_LINE = "INSERT INTO acb_run_lines (run_id, member_id, "
            + "re_presents_line_id, run_released, membership_number, surname, initials, bank_name, "
            + "branch_code, account_number, account_type, debit_day, action_date, amount, outcome) "
            + "VALUES (?, ?, ?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String INSERT_EXCLUSION =
            "INSERT INTO acb_run_exclusions (run_id, member_id, reason, detail) VALUES (?, ?, ?, ?)";

    private static final String INSERT_COUNT = "INSERT INTO acb_run_exclusion_counts "
            + "(run_id, reason, detail, member_count) VALUES (?, ?, ?, ?)";

    private final AcbRunRepository runs;
    private final AcbRunLineRepository lines;
    private final AcbRateRepository rates;
    private final AcbPopulationRepository population;
    private final AcbAuditTrail audit;
    private final HolidayCalendarSource calendars;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final MeterRegistry meters;

    public AcbPreparationJob(AcbRunRepository runs, AcbRunLineRepository lines, AcbRateRepository rates,
                             AcbPopulationRepository population, AcbAuditTrail audit,
                             HolidayCalendarSource calendars, Clock clock,
                             PlatformTransactionManager transactions, JdbcTemplate jdbc,
                             MeterRegistry meters) {
        this.runs = runs;
        this.lines = lines;
        this.rates = rates;
        this.population = population;
        this.audit = audit;
        this.calendars = calendars;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.jdbc = jdbc;
        this.meters = meters;
    }

    /**
     * Prepares run {@code runId}, which must be {@code PREPARING}. Never throws: every way it can
     * go wrong ends with the run {@code FAILED} and the reason on it, because nobody is waiting on
     * this thread to be told.
     */
    public void prepare(long runId) {
        try {
            prepareOrThrow(runId);
        } catch (AcbRefusal refusal) {
            fail(runId, refusal.code().name() + ": " + refusal.getMessage());
        } catch (ObjectOptimisticLockingFailureException raced) {
            // The sweep failed the run while this job was reading; its write rolled back whole.
            log.info("ACB run {}: swept before its preparation could be written; nothing written", runId);
        } catch (RuntimeException unexpected) {
            log.error("ACB run {}: preparation failed", runId, AcbLogging.scrubbed(unexpected));
            fail(runId, "UNEXPECTED: " + unexpected.getClass().getSimpleName()
                    + " -- the log has the stack, under this run's id");
        }
    }

    private void prepareOrThrow(long runId) {
        AcbRun run = runs.findById(runId).orElse(null);
        if (run == null || run.getStatus() != AcbRunStatus.PREPARING) {
            return;
        }
        YearMonth month = YearMonth.from(run.getActionMonth());
        AcbRate rate = AcbRuns.rateInForce(rates, month);
        // Read once, here, and used for every member: the calendar the lines are computed with is
        // the one the run's fingerprint records (SD3-F, SD3-L).
        HolidayCalendar calendar = AcbRuns.calendarCovering(calendars.load(), month);
        BankIndex banks = AcbRuns.unambiguousBanks(population);
        List<RePresentationCandidate> candidates = lines.findRePresentationCandidates(
                month.atDay(1), AcbLineOutcome.UNPAID, UnpaidDecision.RE_PRESENT, AcbRuns.SENT);

        RunPreparer preparer = new RunPreparer(month, new DebitWindow(calendar), banks,
                rate.getAmount(), candidates);
        Integer after = BEFORE_FIRST_MEMBER;
        List<MemberDebitRow> page;
        do {
            page = population.findPageAfter(after, PageRequest.of(0, PAGE_SIZE));
            page.forEach(preparer::accept);
            if (!page.isEmpty()) {
                after = page.get(page.size() - 1).id();
            }
            heartbeat(runId);
        } while (page.size() == PAGE_SIZE);

        Preparation prepared = preparer.finish();
        if (Boolean.TRUE.equals(tx.execute(status -> write(runId, rate, calendar, prepared)))) {
            meters.counter("acb.runs.prepared").increment();
            meters.counter("acb.lines.written").increment(prepared.lineCount());
            log.info("ACB run {}: prepared {} lines, {} listed exclusions", runId,
                    prepared.lineCount(), prepared.exclusions().size());
        }
    }

    /**
     * Writes everything, in the one transaction the caller opened, or nothing.
     *
     * @return false when the run is no longer {@code PREPARING} -- the sweep failed it while this
     *         job was reading -- in which case nothing is written and the sweep's verdict stands
     */
    private boolean write(long runId, AcbRate rate, HolidayCalendar calendar, Preparation prepared) {
        AcbRun run = runs.findById(runId).orElse(null);
        if (run == null) {
            return false;
        }
        AcbRunStatus from = run.getStatus();
        Optional<AcbRunStatus> to = AcbRunAction.COMPLETE_PREPARATION.from(from);
        if (to.isEmpty()) {
            return false;
        }
        insertLines(runId, prepared.lines());
        insertExclusions(runId, prepared.exclusions());
        insertCounts(runId, prepared.counted());

        run.setStatus(to.get());
        run.setLineCount(prepared.lineCount());
        run.setTotalAmount(prepared.total());
        run.setRateAmount(rate.getAmount());
        run.setRateEffectiveFrom(rate.getEffectiveFrom());
        run.setHolidaySet(calendar.name());
        run.setCalendarFingerprint(calendar.fingerprint(YearMonth.from(run.getActionMonth())));
        prepared.firstActionDate().ifPresent(run::setFirstDueDate);
        prepared.lastActionDate().ifPresent(last -> {
            run.setLastDueDate(last);
            run.setPurgeDate(purgeDate(last, calendar));
        });
        run.setHeartbeatAt(AcbTime.instant(clock));
        // The version check. A sweep that failed this run while we read makes this throw, and the
        // whole write -- lines included -- rolls back with it.
        runs.saveAndFlush(run);
        audit.record(runId, null, AcbRunAction.COMPLETE_PREPARATION.name(), from, to.get(),
                prepared.lineCount() + " lines, R " + prepared.total() + ", "
                        + prepared.exclusions().size() + " members listed as excluded",
                AcbActor.SYSTEM);
        return true;
    }

    /** Ten days after the last action date, moved back to a business day (D31): 30 July 2026 -> Friday 7 August. */
    static LocalDate purgeDate(LocalDate lastActionDate, HolidayCalendar calendar) {
        return calendar.businessDayOnOrBefore(lastActionDate.plusDays(PURGE_LAG_DAYS));
    }

    /**
     * Fails run {@code runId} if it is still {@code PREPARING}, with {@code reason} on it and on its
     * audit trail. A run someone else has already moved on is left as they left it.
     */
    public void fail(long runId, String reason) {
        try {
            tx.executeWithoutResult(status -> {
                AcbRun run = runs.findById(runId).orElse(null);
                if (run != null) {
                    failInThisTransaction(run, reason, AcbRunAction.FAIL_PREPARATION.name());
                }
            });
        } catch (ObjectOptimisticLockingFailureException raced) {
            log.info("ACB run {}: moved on by someone else before it could be failed", runId);
        }
    }

    /**
     * Fails every {@code PREPARING} run whose heartbeat is older than {@code cutoff}: its job died
     * with its instance. Each run is re-read and re-checked in its own transaction, so a job that
     * finished in the meantime wins, and one stuck run cannot stop the others being swept.
     *
     * @return how many runs were failed
     */
    public int sweep(Instant cutoff) {
        int swept = 0;
        for (AcbRun stale : runs.findByStatusAndHeartbeatAtBefore(AcbRunStatus.PREPARING, cutoff)) {
            try {
                Boolean failed = tx.execute(status -> {
                    AcbRun run = runs.findById(stale.getId()).orElse(null);
                    if (run == null || !run.getHeartbeatAt().isBefore(cutoff)) {
                        return false;
                    }
                    return failInThisTransaction(run, "interrupted: no heartbeat since "
                            + AcbTime.iso(run.getHeartbeatAt()), "SWEPT");
                });
                if (Boolean.TRUE.equals(failed)) {
                    swept++;
                    log.warn("ACB run {}: swept to FAILED -- its preparation stopped", stale.getId());
                }
            } catch (ObjectOptimisticLockingFailureException raced) {
                log.info("ACB run {}: finished while being swept; left as it is", stale.getId());
            }
        }
        return swept;
    }

    private boolean failInThisTransaction(AcbRun run, String reason, String kind) {
        AcbRunStatus from = run.getStatus();
        Optional<AcbRunStatus> to = AcbRunAction.FAIL_PREPARATION.from(from);
        if (to.isEmpty()) {
            return false;
        }
        run.setStatus(to.get());
        run.setFailureReason(reason.length() <= 500 ? reason : reason.substring(0, 500));
        runs.saveAndFlush(run);
        audit.record(run.getId(), null, kind, from, to.get(), reason, AcbActor.SYSTEM);
        return true;
    }

    private void heartbeat(long runId) {
        tx.executeWithoutResult(status ->
                runs.touchHeartbeat(runId, AcbRunStatus.PREPARING, AcbTime.instant(clock)));
    }

    // ------------------------------------------------------------------------------------------
    // The writes: JDBC batches, so a run of four thousand lines is a handful of round trips
    // ------------------------------------------------------------------------------------------

    private void insertLines(long runId, List<PreparedLine> prepared) {
        if (prepared.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_LINE, prepared, BATCH_SIZE, (ps, line) -> {
            ps.setLong(1, runId);
            ps.setInt(2, line.memberId());
            setLong(ps, 3, line.rePresentsLineId());
            ps.setString(4, line.membershipNumber());
            ps.setString(5, line.surname());
            ps.setString(6, line.initials());
            ps.setString(7, line.bankName());
            ps.setString(8, line.branchCode());
            ps.setString(9, line.accountNumber());
            setInt(ps, 10, line.accountType());
            ps.setInt(11, line.debitDay());
            ps.setObject(12, line.actionDate());
            ps.setBigDecimal(13, line.amount());
            ps.setString(14, AcbLineOutcome.PENDING.name());
        });
    }

    private void insertExclusions(long runId, List<Exclusion> exclusions) {
        if (exclusions.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(INSERT_EXCLUSION, exclusions, BATCH_SIZE, (ps, exclusion) -> {
            ps.setLong(1, runId);
            ps.setInt(2, exclusion.memberId());
            ps.setString(3, exclusion.reason().name());
            ps.setString(4, exclusion.detail());
        });
    }

    private void insertCounts(long runId, Map<AcbExclusionReason, Map<String, Integer>> counted) {
        List<Object[]> rows = new ArrayList<>();
        counted.forEach((reason, byValue) -> byValue.forEach((detail, count) ->
                rows.add(new Object[] {runId, reason.name(), detail, count})));
        if (!rows.isEmpty()) {
            jdbc.batchUpdate(INSERT_COUNT, rows);
        }
    }

    private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private static void setInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }
}

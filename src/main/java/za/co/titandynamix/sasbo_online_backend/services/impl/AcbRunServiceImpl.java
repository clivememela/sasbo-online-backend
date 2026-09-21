package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.acb.LineTotals;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunExclusionCount;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunAction;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunExclusionCountRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunExclusionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The debit-order run: prepare a month, read it, move it on.
 *
 * <h2>Transactions are explicit here, and that is deliberate</h2>
 * Each change runs in a {@link TransactionTemplate} rather than under {@code @Transactional},
 * because what happens when a change LOSES a race has to happen outside the transaction that lost.
 * A second approval of one run fails at flush with an optimistic-lock failure; its transaction is
 * then dead, so the run's current state is re-read in a fresh one and returned as
 * {@code RUN_VERSION_CONFLICT} -- and the screen can say "approved by someone else" without asking
 * again. The same shape turns a lost race for a month into {@code RUN_ALREADY_EXISTS}.
 *
 * <h2>Every change writes its audit row inside its own transaction</h2>
 * A change that commits without its row, or a row whose change rolled back, is a trail that lies.
 */
@Service
public class AcbRunServiceImpl implements AcbRunService {

    /** The largest page any ACB list returns, however many the caller asks for. */
    static final int MAX_PAGE_SIZE = 200;

    private final AcbRunRepository runs;
    private final AcbRunLineRepository lines;
    private final AcbRunExclusionRepository exclusions;
    private final AcbRunExclusionCountRepository exclusionCounts;
    private final AcbRateRepository rates;
    private final AcbPopulationRepository population;
    private final AcbAuditTrail audit;
    private final AcbPreparationJob job;
    private final AcbFileService files;
    private final AcbJobLauncher launcher;
    private final HolidayCalendarSource calendars;
    private final SeededMembersGuard seededMembers;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final Duration preparationCeiling;

    public AcbRunServiceImpl(AcbRunRepository runs, AcbRunLineRepository lines,
                             AcbRunExclusionRepository exclusions,
                             AcbRunExclusionCountRepository exclusionCounts, AcbRateRepository rates,
                             AcbPopulationRepository population, AcbAuditTrail audit,
                             AcbPreparationJob job, AcbFileService files, AcbJobLauncher launcher,
                             HolidayCalendarSource calendars, SeededMembersGuard seededMembers,
                             Clock clock, PlatformTransactionManager transactions,
                             @Value("${acb.jobs.preparation-ceiling:PT5M}") Duration preparationCeiling) {
        this.runs = runs;
        this.lines = lines;
        this.exclusions = exclusions;
        this.exclusionCounts = exclusionCounts;
        this.rates = rates;
        this.population = population;
        this.audit = audit;
        this.job = job;
        this.files = files;
        this.launcher = launcher;
        this.calendars = calendars;
        this.seededMembers = seededMembers;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.preparationCeiling = preparationCeiling;
    }

    // ------------------------------------------------------------------------------------------
    // Prepare
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbRunDto prepare(String actionMonth, String actor) {
        YearMonth month = parseMonth(actionMonth);
        requirePreparable(month);
        // Checked here so the administrator hears at once, and again by the job, which is the
        // one that has to be right.
        AcbRuns.rateInForce(rates, month);
        HolidayCalendar calendar = AcbRuns.calendarCovering(calendars.load(), month);
        AcbRuns.unambiguousBanks(population);

        AcbRun created;
        try {
            created = tx.execute(status -> create(month, calendar.name(), actor));
        } catch (DataIntegrityViolationException lostTheRace) {
            // Another preparation of the same month committed between the check and the insert.
            // The partial unique index is the arbiter, and its answer is the same refusal.
            AcbRun live = runs.findLive(month.atDay(1), AcbRuns.RELEASED).orElseThrow(() -> lostTheRace);
            throw AcbRuns.alreadyExists(live);
        }

        long runId = Objects.requireNonNull(created).getId();
        try {
            launcher.launch(() -> job.prepare(runId));
        } catch (RejectedExecutionException busy) {
            job.fail(runId, "NOT_STARTED: the preparation could not be started; cancel and prepare again");
        }
        return toDto(created, new LineTotals(0L, null), List.of(), calendar);
    }

    private AcbRun create(YearMonth month, String calendarName, String actor) {
        runs.findLive(month.atDay(1), AcbRuns.RELEASED).ifPresent(live -> {
            throw AcbRuns.alreadyExists(live);
        });
        Instant now = AcbTime.instant(clock);
        AcbRun run = new AcbRun();
        run.setActionMonth(month.atDay(1));
        run.setStatus(AcbRunStatus.PREPARING);
        run.setSetupDate(AcbTime.today(clock));
        run.setHolidaySet(calendarName);
        // The run the bank declined for this month, if any: this one is prepared to replace it (SD3-H).
        runs.findFirstByActionMonthAndStatusOrderByIdDesc(month.atDay(1), AcbRunStatus.DECLINED)
                .ifPresent(declined -> run.setReplacesRunId(declined.getId()));
        run.setLineCount(0);
        run.setTotalAmount(BigDecimal.ZERO.setScale(2));
        run.setCreatedBy(actor);
        run.setStartedAt(now);
        run.setHeartbeatAt(now);
        AcbRun saved = runs.saveAndFlush(run);
        audit.record(saved.getId(), null, "PREPARE", null, AcbRunStatus.PREPARING,
                "Preparation of " + month + " started", actor);
        return saved;
    }

    /** {@code YYYY-MM}, and nothing looser: "2026-9" is refused rather than guessed at. */
    static YearMonth parseMonth(String actionMonth) {
        try {
            return YearMonth.parse(actionMonth == null ? "" : actionMonth.trim());
        } catch (DateTimeParseException malformed) {
            throw new AcbRefusal(AcbRefusalCode.ACTION_MONTH_INVALID,
                    "The action month must be written YYYY-MM, for example 2026-10.",
                    Map.of("actionMonth", String.valueOf(actionMonth)));
        }
    }

    /**
     * This month or next, in Johannesburg. Whether a run may ever be prepared for a month already
     * past -- a late run -- is SASBO's to answer; until they do, it is refused rather than allowed.
     */
    private void requirePreparable(YearMonth month) {
        YearMonth current = AcbTime.currentMonth(clock);
        if (!month.equals(current) && !month.equals(current.plusMonths(1))) {
            Map<String, String> facts = new LinkedHashMap<>();
            facts.put("actionMonth", month.toString());
            facts.put("earliest", current.toString());
            facts.put("latest", current.plusMonths(1).toString());
            throw new AcbRefusal(AcbRefusalCode.ACTION_MONTH_INVALID,
                    "A run can be prepared for " + current + " or " + current.plusMonths(1) + ".",
                    facts);
        }
    }

    // ------------------------------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbPage<AcbRunSummaryDto> list(int page, int size) {
        sweepInterrupted();
        Page<AcbRun> found = runs.findPage(pageRequest(page, size));
        Map<Long, LineTotals> totals = totalsFor(found.getContent());
        // One calendar read for the page, never one per run.
        HolidayCalendar calendar = calendars.load();
        return AcbPage.of(found, run -> summary(run, totals.getOrDefault(run.getId(), new LineTotals(0L, null)),
                calendar));
    }

    @Override
    public AcbRunDto get(long runId) {
        sweepInterrupted();
        AcbRun run = requireRun(runId);
        return toDto(run, lines.totalsFor(runId), exclusionSummary(runId), calendars.load());
    }

    @Override
    public AcbPage<AcbRunLineDto> lines(long runId, int page, int size) {
        requireRun(runId);
        return AcbPage.of(lines.findPage(runId, pageRequest(page, size)), AcbRunServiceImpl::lineDto);
    }

    @Override
    public AcbPage<AcbExclusionDto> exclusions(long runId, int page, int size) {
        requireRun(runId);
        return AcbPage.of(exclusions.findPage(runId, pageRequest(page, size)), Function.identity());
    }

    // ------------------------------------------------------------------------------------------
    // Move on
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbRunDto approve(long runId, int version, String actor, String reason) {
        return transition(runId, version, AcbRunAction.APPROVE, actor, reason, run -> {
            if (lines.totalsFor(runId).lineCount() == 0) {
                throw new AcbRefusal(AcbRefusalCode.RUN_EMPTY,
                        "This run debits nobody, so there is nothing to approve. Its exclusions "
                                + "say why each member was left out.",
                        AcbRuns.facts(run));
            }
            seededMembers.refuseSeededMembers(run);
            AcbRuns.requireCalendarAgrees(run, calendars.load());
            // Whoever prepared the run may approve it: the bank, not a second person here, is the
            // check on the file (D14 as amended, Q29). Who did each act is still recorded.
            run.setApprovedBy(actor);
        });
    }

    @Override
    public AcbRunDto recordSubmitted(long runId, int version, String actor, String reason) {
        return transition(runId, version, AcbRunAction.RECORD_SUBMITTED, actor, reason, run -> {
            // The stored file is the evidence of what was sent; a submission with no file behind
            // it would be a claim nobody could check.
            if (!files.hasRendered(runId)) {
                throw new AcbRefusal(AcbRefusalCode.FILE_NOT_RENDERED,
                        "This run's bureau file has not been produced, so its submission cannot be "
                                + "recorded. Produce the file, upload it at the bank, then record it here.",
                        AcbRuns.facts(run));
            }
        });
    }

    @Override
    public AcbRunDto recordReturns(long runId, int version, String actor, String reason) {
        return transition(runId, version, AcbRunAction.RECORD_RETURNS, actor, reason, run -> {
            // The bank's report lists only what came back (Q8), so what it does not list was paid (D28).
            int paid = lines.markPending(runId, AcbLineOutcome.PENDING, AcbLineOutcome.PAID);
            audit.record(runId, null, "LINES_PAID", null, null, paid + " debit(s) not returned, marked paid", actor);
        });
    }

    @Override
    public AcbRunDto close(long runId, int version, String actor, String reason) {
        return transition(runId, version, AcbRunAction.CLOSE, actor, reason, run -> {
            long undecided = lines.countUndecided(runId, AcbLineOutcome.UNPAID);
            if (undecided > 0) {
                Map<String, String> facts = AcbRuns.facts(run);
                facts.put("undecided", String.valueOf(undecided));
                throw new AcbRefusal(AcbRefusalCode.UNPAIDS_UNDECIDED,
                        undecided + " unpaid(s) in this run have no decision yet. Decide whether each is "
                                + "presented again or suspended, then close the month.", facts);
            }
        });
    }

    /** The bank's words are kept to this many characters -- refused beyond it, never cut (SD3-D). */
    static final int BANK_WORDS_LIMIT = 300;

    @Override
    public AcbRunDto recordDeclined(long runId, int version, String actor, AcbDeclineReason reason, String bankWords) {
        String words = bankWords == null || bankWords.isBlank() ? null : bankWords.trim();
        if (reason == null || (words != null && words.length() > BANK_WORDS_LIMIT)) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED,
                    "A decline needs its reason -- wrong debit date, layout or other -- and the bank's words in at most "
                            + BANK_WORDS_LIMIT + " characters.", Map.of("runId", String.valueOf(runId)));
        }
        // 🔒 The audit row says THAT the bank declined, and why from the list -- never the bank's words (SD3-D).
        return transition(runId, version, AcbRunAction.RECORD_DECLINED, actor, "declined by the bank: " + reason.name(),
                run -> {
                    long answered = lines.countByRunIdAndOutcomeNot(runId, AcbLineOutcome.PENDING);
                    if (answered > 0) {
                        Map<String, String> facts = new LinkedHashMap<>(AcbRuns.facts(run));
                        facts.put("returnedLines", String.valueOf(answered));
                        throw new AcbRefusal(AcbRefusalCode.RUN_HAS_RETURNS,
                                answered + " line(s) of this run have already come back from the bank, so its file "
                                        + "was not declined. A declined file produces no returns.", facts);
                    }
                    run.setDeclinedReason(reason);
                    run.setDeclinedWords(words);
                    // The month's unpaids it re-presented are offered again when the month is prepared
                    // again -- as a cancel releases them (D26).
                    lines.releaseRun(runId);
                });
    }

    @Override
    public AcbRunDto cancel(long runId, int version, String actor, String reason) {
        return transition(runId, version, AcbRunAction.CANCEL, actor, reason,
                // A cancelled run's lines stop claiming the unpaids they re-present, in the same
                // transaction, so the month prepared again can offer them.
                run -> lines.releaseRun(runId));
    }

    /**
     * One transition, in one transaction: the version the caller saw, the state table, the
     * action's own checks, the change, and its audit row -- or none of it.
     */
    private AcbRunDto transition(long runId, int version, AcbRunAction action, String actor,
                                 String reason, Consumer<AcbRun> checkAndApply) {
        AcbRun changed;
        try {
            changed = tx.execute(status -> {
                AcbRun run = requireRun(runId);
                if (!Objects.equals(run.getVersion(), version)) {
                    throw AcbRuns.versionConflict(run);
                }
                AcbRunStatus from = run.getStatus();
                AcbRunStatus to = action.from(from).orElseThrow(() -> AcbRuns.notInState(run, action));
                checkAndApply.accept(run);
                run.setStatus(to);
                AcbRun saved = runs.saveAndFlush(run);
                audit.record(runId, null, action.name(), from, to,
                        reason == null || reason.isBlank() ? action.name().toLowerCase(Locale.ROOT) : reason.trim(),
                        actor);
                return saved;
            });
        } catch (ObjectOptimisticLockingFailureException lostTheRace) {
            throw AcbRuns.versionConflict(requireRun(runId));
        }
        return get(Objects.requireNonNull(changed).getId());
    }

    @Override
    public int sweepInterrupted() {
        return job.sweep(AcbTime.instant(clock).minus(preparationCeiling));
    }

    // ------------------------------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------------------------------

    private AcbRun requireRun(long runId) {
        return runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private Map<Long, LineTotals> totalsFor(List<AcbRun> page) {
        Map<Long, LineTotals> totals = new HashMap<>();
        List<Long> ids = page.stream().map(AcbRun::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : lines.totalsForRuns(ids)) {
                totals.put((Long) row[0], new LineTotals((Long) row[1], (BigDecimal) row[2]));
            }
        }
        return totals;
    }

    private List<AcbExclusionSummaryDto> exclusionSummary(long runId) {
        List<AcbExclusionSummaryDto> summary = new ArrayList<>();
        for (Object[] row : exclusions.countByReason(runId)) {
            summary.add(new AcbExclusionSummaryDto((AcbExclusionReason) row[0], true, null,
                    ((Number) row[1]).intValue()));
        }
        for (AcbRunExclusionCount count : exclusionCounts.findByRunIdOrderByReasonAscDetailAsc(runId)) {
            summary.add(new AcbExclusionSummaryDto(count.getReason(), false, count.getDetail(),
                    count.getMemberCount()));
        }
        summary.sort(Comparator.comparing(AcbExclusionSummaryDto::reason)
                .thenComparing(s -> s.detail() == null ? "" : s.detail()));
        return summary;
    }

    /** Whether the totals stored at preparation still equal what the lines add up to. */
    private static boolean totalsAgree(AcbRun run, LineTotals counted) {
        return counted.lineCount() == run.getLineCount()
                && counted.total().compareTo(run.getTotalAmount()) == 0;
    }

    private AcbRunDto toDto(AcbRun run, LineTotals counted, List<AcbExclusionSummaryDto> summary,
                            HolidayCalendar calendar) {
        return new AcbRunDto(
                run.getId(),
                AcbTime.iso(YearMonth.from(run.getActionMonth())),
                run.getStatus(),
                run.getVersion(),
                AcbTime.iso(run.getSetupDate()),
                AcbTime.iso(run.getCreationDate()),
                AcbTime.iso(run.getPurgeDate()),
                AcbTime.iso(run.getFirstDueDate()),
                AcbTime.iso(run.getLastDueDate()),
                AcbTime.iso(AcbRuns.makeBy(run.getFirstDueDate())),
                AcbRuns.makeByPassed(run, AcbTime.today(clock)),
                counted.lineCount(),
                counted.total(),
                totalsAgree(run, counted),
                AcbRuns.calendarAgrees(run, calendar),
                run.getRateAmount(),
                AcbTime.iso(run.getRateEffectiveFrom()),
                run.getHolidaySet(),
                holidaysKnown(run),
                run.getCreatedBy(),
                run.getApprovedBy(),
                AcbTime.iso(run.getStartedAt()),
                AcbTime.iso(run.getHeartbeatAt()),
                run.getFailureReason(),
                run.getDeclinedReason(),
                run.getDeclinedWords(),
                run.getReplacesRunId(),
                summary);
    }

    private AcbRunSummaryDto summary(AcbRun run, LineTotals counted, HolidayCalendar calendar) {
        return new AcbRunSummaryDto(
                run.getId(),
                AcbTime.iso(YearMonth.from(run.getActionMonth())),
                run.getStatus(),
                run.getVersion(),
                AcbTime.iso(run.getSetupDate()),
                counted.lineCount(),
                counted.total(),
                totalsAgree(run, counted),
                AcbRuns.calendarAgrees(run, calendar),
                holidaysKnown(run),
                run.getFailureReason(),
                run.getDeclinedReason());
    }

    /**
     * A run's dates considered holidays unless it was computed on weekends alone -- which only a run
     * prepared before the calendar was data can have been (D30).
     */
    private static boolean holidaysKnown(AcbRun run) {
        return !HolidayCalendar.WEEKENDS_ONLY.name().equals(run.getHolidaySet());
    }

    /** 🔒 A line for the sheet: the account shows its last four digits, and never more. */
    static AcbRunLineDto lineDto(AcbRunLine line) {
        return lineDto(line, null);
    }

    /** A line for the sheet, with the decision its code suggests if it is an undecided unpaid. */
    static AcbRunLineDto lineDto(AcbRunLine line, UnpaidDecision suggested) {
        return new AcbRunLineDto(
                line.getId(),
                line.getMemberId(),
                line.getMembershipNumber(),
                line.getSurname(),
                line.getInitials(),
                line.getBankName(),
                line.getBranchCode(),
                lastFour(line.getAccountNumber()),
                line.getAccountType(),
                line.getDebitDay(),
                AcbTime.iso(line.getActionDate()),
                line.getAmount(),
                line.getOutcome(),
                line.getRePresentsLineId(),
                line.getFileSequence(),
                line.getUnpaidCode(),
                line.getUnpaidReason(),
                line.getDecision(),
                line.getOutcome() == AcbLineOutcome.UNPAID && line.getDecision() == null ? suggested : null,
                line.getPermissionNote());
    }

    /** The last four digits, or nothing at all for a number so short that four would be all of it. */
    static String lastFour(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return null;
        }
        return accountNumber.substring(accountNumber.length() - 4);
    }
}

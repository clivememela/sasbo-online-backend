package za.co.titandynamix.sasbo_online_backend.services.impl;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRenderer;
import za.co.titandynamix.sasbo_online_backend.acb.returns.ReDebitRules;
import za.co.titandynamix.sasbo_online_backend.acb.returns.UnpaidCounting;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDaysDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.SuspendMemberRequest;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusChangeRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbReturnsBatch;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebitWithdrawal;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsBatchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The returns loop: the bank's unpaids recorded -- by line, or a debit day's report at a time -- with the rules
 * Stephne gave on 6 October 2026, and what follows them: the letters, suspensions with SASBO's codes, an ending after
 * the third unpaid, and a member's return.
 *
 * <h2>Recording twice records once</h2>
 * The same unpaid recorded again -- same line, same code, the same decision or none -- changes
 * nothing and answers with the line as it stands. A different code or decision for a line already
 * recorded is refused with {@code UNPAID_ALREADY_RECORDED}, naming what is there: a return is a
 * fact about what the bank did, and it is corrected deliberately, not overwritten by a second click.
 *
 * <h2>The letter goes with the unpaid, the same day</h2>
 * "Letters must go out as soon as we do the updates on the system" (DOD-07). So recording an
 * unpaid queues its letter in the same transaction, and renders it at once for a member with an
 * email address -- the rendered copy's hash and the address it was rendered for are kept, never
 * the body. A member with no email address leaves the letter QUEUED with the reason NO_EMAIL, shown
 * on the worklist (Q46). Nothing is sent while sending is switched off (the letters review).
 *
 * <h2>A re-debit only where the rules allow it</h2>
 * {@link ReDebitRules}: the bank's code must allow one -- 2 yes, 4 and 30, 31, 32, 34 never, a code nobody has
 * ruled on not until it is set (DOD-03, DOD-04) -- and the member must be below three unpaids, counted by
 * {@link AcbUnpaidCounter} as the termination report counts them. A decision that breaks a rule is refused, and the
 * unpaid recorded with it rolls back: nothing is half-recorded.
 */
@Service
public class AcbReturnsServiceImpl implements AcbReturnsService {

    /** A run whose file went to the bank -- the only kind whose lines can come back unpaid. */
    private static final Set<AcbRunStatus> RETURNABLE = EnumSet.of(AcbRunStatus.SUBMITTED, AcbRunStatus.RETURNED);

    /** The bank prints its codes as numbers: one to three digits. */
    private static final Pattern BANK_CODE = Pattern.compile("[0-9]{1,3}");

    /** Problems a refusal names, at most -- the total is always given (EH2-E). */
    static final int PROBLEMS_SHOWN = 50;

    private static final String BLINDNESS = "Counted from the first month this system sent a file. A member "
            + "already in arrears in the old system is not counted until SASBO's opening arrears position "
            + "is captured.";

    private final AcbRunRepository runs;
    private final AcbRunLineRepository lines;
    private final AcbReturnsRepository returns;
    private final AcbUnpaidCodeRepository codes;
    private final AcbPopulationRepository population;
    private final AcbLetterRepository letters;
    private final AcbAuditTrail audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;
    private final MemberService memberService;
    private final AcbLetterSender sender;
    private final za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher launcher;
    private final AcbUnpaidCounter counter;
    private final SasboReferenceRepository reference;
    private final AcbReturnsBatchRepository batches;
    private final JdbcTemplate jdbc;

    public AcbReturnsServiceImpl(AcbRunRepository runs, AcbRunLineRepository lines, AcbReturnsRepository returns,
                                 AcbUnpaidCodeRepository codes, AcbLetterRepository letters, AcbAuditTrail audit,
                                 AcbPopulationRepository population,
                                 Clock clock, PlatformTransactionManager transactions, MeterRegistry meters,
                                 MemberService memberService, AcbLetterSender sender,
                                 za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher launcher,
                                 AcbUnpaidCounter counter, SasboReferenceRepository reference,
                                 AcbReturnsBatchRepository batches, JdbcTemplate jdbc) {
        this.runs = runs;
        this.lines = lines;
        this.returns = returns;
        this.codes = codes;
        this.letters = letters;
        this.audit = audit;
        this.population = population;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.meters = meters;
        this.memberService = memberService;
        this.sender = sender;
        this.launcher = launcher;
        this.counter = counter;
        this.reference = reference;
        this.batches = batches;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------
    // Recording, by line
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbRunLineDto recordUnpaid(long runId, long lineId, RecordUnpaidRequest request, String actor) {
        LocalDate notedOn = parseDate(request.notedOn());
        String code = request.code().trim();
        Boolean recorded = tx.execute(status -> {
            AcbRun run = requireReturnable(runId);
            AcbRunLine line = requireLine(runId, lineId);
            Recorded outcome = record(run, line, code, notedOn, request.decision(), null, codes::findById,
                    memberId -> returns.findRecipient(memberId).orElse(null), actor);
            if (outcome.newly() && request.decision() != null) {
                requireDecisionAllowed(line, request.decision(), counter.of(line.getMemberId()).count().unpaids());
            }
            return outcome.newly();
        });
        if (Boolean.TRUE.equals(recorded)) {
            meters.counter("acb.unpaids.recorded").increment();
            // D23: emailed the same day -- by the sender, after the recording has committed, off this thread.
            launcher.launch(sender::sendDue);
        }
        return withRules(requireLine(runId, lineId));
    }

    /** What recording one unpaid did. */
    private record Recorded(boolean newly, boolean letterQueued) {
    }

    /**
     * Records one unpaid on a line of a returnable run, with its letter, in the caller's transaction. Recording the
     * same again is nothing; a different code or decision for a line already unpaid is refused.
     */
    private Recorded record(AcbRun run, AcbRunLine line, String code, LocalDate notedOn, UnpaidDecision decision,
                            Long batchId, Function<String, Optional<AcbUnpaidCode>> codeTable,
                            Function<Integer, LetterRecipient> recipients, String actor) {
        if (line.getOutcome() == AcbLineOutcome.UNPAID) {
            boolean same = code.equals(line.getUnpaidCode()) && (decision == null || decision == line.getDecision());
            if (same) {
                return new Recorded(false, false);
            }
            throw new AcbRefusal(AcbRefusalCode.UNPAID_ALREADY_RECORDED,
                    "This debit is already recorded as unpaid with code " + line.getUnpaidCode()
                            + (line.getDecision() == null ? ", undecided" : ", " + line.getDecision()) + ".",
                    lineFacts(line));
        }
        // PAID is only a presumption -- the report did not list the debit when the returns were
        // taken in (D28) -- so a return that arrives late overturns it, and the audit says so.
        boolean late = line.getOutcome() == AcbLineOutcome.PAID;
        Optional<AcbUnpaidCode> known = codeTable.apply(code);
        UnpaidReason reason = known.map(AcbUnpaidCode::getReason).orElse(UnpaidReason.OTHER);
        line.setOutcome(AcbLineOutcome.UNPAID);
        line.setUnpaidCode(code);
        line.setUnpaidReason(reason.name());
        line.setUnpaidNotedOn(notedOn);
        line.setDecision(decision);
        line.setReturnsBatchId(batchId);
        lines.saveAndFlush(line);
        audit.record(run.getId(), line.getId(), "UNPAID_RECORDED", null, null,
                "code " + code + " (" + reason + ")" + (decision == null ? ", undecided" : ", " + decision)
                        + (late ? ", a late return: was PAID" : "") + (batchId == null ? "" : ", report " + batchId),
                actor);
        AcbLetter letter = queueLetter(run, line, reason.letter(),
                known.map(AcbUnpaidCode::getWording).orElse(reason.wording()), recipients, actor);
        return new Recorded(true, letter != null);
    }

    @Override
    public AcbRunLineDto decide(long runId, long lineId, UnpaidDecision decision, String actor) {
        tx.executeWithoutResult(status -> {
            requireReturnable(runId);
            AcbRunLine line = requireLine(runId, lineId);
            if (line.getOutcome() != AcbLineOutcome.UNPAID) {
                throw new AcbRefusal(AcbRefusalCode.RUN_NOT_IN_STATE,
                        "Only an unpaid debit takes a decision; this one is " + line.getOutcome() + ".", lineFacts(line));
            }
            if (line.getDecision() == decision) {
                return;
            }
            if (line.getDecision() != null) {
                throw new AcbRefusal(AcbRefusalCode.UNPAID_ALREADY_RECORDED,
                        "The decision on this unpaid is already " + line.getDecision() + ".", lineFacts(line));
            }
            requireDecisionAllowed(line, decision, counter.of(line.getMemberId()).count().unpaids());
            line.setDecision(decision);
            lines.saveAndFlush(line);
            audit.record(runId, lineId, "UNPAID_DECIDED", null, null, decision.name(), actor);
        });
        return withRules(requireLine(runId, lineId));
    }

    /** The note is kept to this many characters -- refused beyond it, never cut (SD3-D). */
    static final int PERMISSION_NOTE_LIMIT = 300;

    @Override
    public AcbRunLineDto permit(long runId, long lineId, String note, String actor) {
        String permission = note == null || note.isBlank() ? null : note.trim();
        if (permission == null) {
            throw new AcbRefusal(AcbRefusalCode.PERMISSION_NOTE_REQUIRED,
                    "A further re-debit needs the member's permission: who agreed, when, and how.",
                    Map.of("lineId", String.valueOf(lineId)));
        }
        if (permission.length() > PERMISSION_NOTE_LIMIT) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED,
                    "The member's permission is kept to " + PERMISSION_NOTE_LIMIT + " characters.",
                    Map.of("lineId", String.valueOf(lineId), "field", "note"));
        }
        tx.executeWithoutResult(status -> {
            AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
            if (!AcbRuns.SENT.contains(run.getStatus())) {
                throw new AcbRefusal(AcbRefusalCode.RUN_NOT_IN_STATE, "Permission is recorded for an unpaid of a run "
                        + "whose file went to the bank; this run is " + run.getStatus() + ".", AcbRuns.facts(run));
            }
            AcbRunLine line = requireLine(runId, lineId);
            if (line.getOutcome() != AcbLineOutcome.UNPAID) {
                throw new AcbRefusal(AcbRefusalCode.RUN_NOT_IN_STATE,
                        "Only an unpaid debit is presented again; this one is " + line.getOutcome() + ".", lineFacts(line));
            }
            if (line.getDecision() == UnpaidDecision.SUSPEND) {
                throw new AcbRefusal(AcbRefusalCode.UNPAID_ALREADY_RECORDED,
                        "The decision on this unpaid is SUSPEND; it is not presented again.", lineFacts(line));
            }
            requireDecisionAllowed(line, UnpaidDecision.RE_PRESENT, counter.of(line.getMemberId()).count().unpaids());
            line.setDecision(UnpaidDecision.RE_PRESENT);
            line.setPermissionNote(permission);
            lines.saveAndFlush(line);
            // 🔒 THAT permission was given, never what it says (SD3-D).
            audit.record(runId, lineId, "PERMISSION_RECORDED", null, null,
                    "the member's permission for a further re-debit recorded", actor);
        });
        return withRules(requireLine(runId, lineId));
    }

    /**
     * Refuses a RE_PRESENT the rules do not allow (Stephne, DOD-03, DOD-04). {@code unpaids} is the member's count with
     * this unpaid in it. SUSPEND is always allowed: it presents nothing.
     */
    private void requireDecisionAllowed(AcbRunLine line, UnpaidDecision decision, int unpaids) {
        if (decision != UnpaidDecision.RE_PRESENT) {
            return;
        }
        ReDebit rule = ruleOf(line.getUnpaidCode());
        switch (ReDebitRules.mayPresentAgain(rule, unpaids)) {
            case ALLOWED -> {
            }
            case CODE_FORBIDS, CODE_UNKNOWN -> throw reDebitNotAllowed(line, rule);
            case LIMIT_REACHED -> throw limitReached(line.getMemberId(), unpaids);
        }
    }

    private static AcbRefusal reDebitNotAllowed(AcbRunLine line, ReDebit rule) {
        Map<String, String> facts = lineFacts(line);
        facts.put("reDebit", rule.name());
        String code = String.valueOf(line.getUnpaidCode());
        return new AcbRefusal(AcbRefusalCode.RE_DEBIT_NOT_ALLOWED, rule == ReDebit.NO
                ? "A debit returned with code " + code + " is never presented again (debit order rules). Suspend it."
                : "Nobody has said yet whether a debit returned with code " + code + " may be presented again, so it "
                        + "is not. Suspend it, or set the code's rule under Debit Order settings first.", facts);
    }

    private static AcbRefusal limitReached(Integer memberId, int unpaids) {
        return new AcbRefusal(AcbRefusalCode.UNPAID_LIMIT_REACHED, "This member has " + unpaids
                + " unpaid debits. No more than three are presented: the debit is not presented again, and a staff "
                + "member confirms whether the membership ends.",
                Map.of("memberId", String.valueOf(memberId), "unpaidCount", String.valueOf(unpaids)));
    }

    /** The bank code's rule: unknown for a code the table does not know, and for a line with none. */
    private ReDebit ruleOf(String code) {
        return code == null ? ReDebit.UNKNOWN : codes.findById(code).map(AcbUnpaidCode::reDebit).orElse(ReDebit.UNKNOWN);
    }

    // ------------------------------------------------------------------------------------------
    // Recording, a debit day's report at a time (DOD-05, DOD-06, DOD-12)
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbReturnsBatchDto recordReturnsBatch(long runId, ReturnsBatchRequest request, String actor) {
        LocalDate actionDate = parseDate(request.actionDate());
        LocalDate reportDate = request.reportDate() == null || request.reportDate().isBlank()
                ? null : parseDate(request.reportDate());
        List<ReturnsBatchRequest.Entry> entries = request.entries();
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            ReturnsBatchRequest.Entry entry = entries.get(i);
            String code = entry.code() == null ? "" : entry.code().trim();
            if (!BANK_CODE.matcher(code).matches()) {
                problems.add("entry " + (i + 1) + ": the bank's code is written as one to three digits");
            }
            if ((entry.lineId() == null) == (entry.fileSequence() == null)) {
                problems.add("entry " + (i + 1) + ": name the debit by its line or by the file's sequence number, "
                        + "not both and not neither");
            }
        }
        requireNoProblems(problems, AcbRefusalCode.RETURNS_BATCH_INVALID, "This returns report has problems");

        Batched batched = tx.execute(status -> {
            AcbRun run = requireReturnable(runId);
            Map<Long, AcbRunLine> byId = new HashMap<>();
            List<Long> ids = entries.stream().map(ReturnsBatchRequest.Entry::lineId).filter(Objects::nonNull).toList();
            if (!ids.isEmpty()) {
                lines.findAllById(ids).forEach(l -> byId.put(l.getId(), l));
            }
            Map<Integer, AcbRunLine> bySequence = new HashMap<>();
            List<Integer> sequences = entries.stream().map(ReturnsBatchRequest.Entry::fileSequence)
                    .filter(Objects::nonNull).toList();
            if (!sequences.isEmpty()) {
                lines.findByRunIdAndFileSequenceIn(runId, sequences).forEach(l -> bySequence.put(l.getFileSequence(), l));
            }
            Map<String, AcbUnpaidCode> codeTable = codes.findAll().stream()
                    .collect(Collectors.toMap(AcbUnpaidCode::getCode, c -> c));
            List<AcbRunLine> resolved = new ArrayList<>();
            Set<Long> seen = new HashSet<>();
            for (int i = 0; i < entries.size(); i++) {
                ReturnsBatchRequest.Entry entry = entries.get(i);
                AcbRunLine line = entry.lineId() != null ? byId.get(entry.lineId()) : bySequence.get(entry.fileSequence());
                String named = entry.lineId() != null ? "line " + entry.lineId() : "sequence number " + entry.fileSequence();
                String code = entry.code().trim();
                if (line == null || !Objects.equals(line.getRunId(), runId)) {
                    problems.add("entry " + (i + 1) + ": " + named + " is not in this run");
                } else if (!actionDate.equals(line.getActionDate())) {
                    problems.add("entry " + (i + 1) + ": " + named + " is debited on " + line.getActionDate()
                            + ", not on " + actionDate);
                } else if (!seen.add(line.getId())) {
                    problems.add("entry " + (i + 1) + ": " + named + " is on this report twice");
                } else if (line.getOutcome() == AcbLineOutcome.UNPAID && (!code.equals(line.getUnpaidCode())
                        || (entry.decision() != null && entry.decision() != line.getDecision()))) {
                    problems.add("entry " + (i + 1) + ": " + named + " is already recorded unpaid with code "
                            + line.getUnpaidCode() + (line.getDecision() == null ? ", undecided" : ", " + line.getDecision()));
                }
                resolved.add(line);
            }
            requireNoProblems(problems, AcbRefusalCode.RETURNS_BATCH_INVALID, "This returns report has problems");

            AcbReturnsBatch batch = new AcbReturnsBatch();
            batch.setRunId(runId);
            batch.setActionDate(actionDate);
            batch.setReportDate(reportDate);
            batch.setSource(AcbReturnsBatch.MANUAL);
            batch.setEntries(entries.size());
            batch.setRecorded(0);
            batch.setAlreadyRecorded(entries.size());
            batch.setLettersQueued(0);
            batch.setRecordedBy(actor);
            batch.setRecordedAt(AcbTime.instant(clock));
            AcbReturnsBatch saved = batches.saveAndFlush(batch);

            Map<Integer, LetterRecipient> recipients = new HashMap<>();
            returns.findRecipients(resolved.stream().map(AcbRunLine::getMemberId).distinct().toList())
                    .forEach(r -> recipients.put(r.memberId(), r));
            LocalDate notedOn = reportDate != null ? reportDate : AcbTime.today(clock);
            int recorded = 0;
            int lettersQueued = 0;
            for (int i = 0; i < entries.size(); i++) {
                Recorded outcome = record(run, resolved.get(i), entries.get(i).code().trim(), notedOn,
                        entries.get(i).decision(), saved.getId(), c -> Optional.ofNullable(codeTable.get(c)),
                        recipients::get, actor);
                recorded += outcome.newly() ? 1 : 0;
                lettersQueued += outcome.letterQueued() ? 1 : 0;
            }
            // Every unpaid is in; now each decision is weighed against the count it makes (DOD-03).
            Map<Integer, AcbUnpaidCounter.Counted> counts = counter.everyone();
            for (int i = 0; i < entries.size(); i++) {
                ReturnsBatchRequest.Entry entry = entries.get(i);
                if (entry.decision() != UnpaidDecision.RE_PRESENT) {
                    continue;
                }
                AcbRunLine line = resolved.get(i);
                AcbUnpaidCounter.Counted counted = counts.get(line.getMemberId());
                int unpaids = counted == null ? 1 : counted.count().unpaids();
                AcbUnpaidCode known = codeTable.get(entry.code().trim());
                ReDebit rule = known == null ? ReDebit.UNKNOWN : known.reDebit();
                switch (ReDebitRules.mayPresentAgain(rule, unpaids)) {
                    case ALLOWED -> {
                    }
                    case CODE_FORBIDS -> problems.add("entry " + (i + 1) + ": a debit returned with code "
                            + entry.code().trim() + " is never presented again; decide SUSPEND");
                    case CODE_UNKNOWN -> problems.add("entry " + (i + 1) + ": nobody has said whether code "
                            + entry.code().trim() + " may be presented again; decide SUSPEND or leave it undecided");
                    case LIMIT_REACHED -> problems.add("entry " + (i + 1) + ": the member has " + unpaids
                            + " unpaid debits, and no more than three are presented");
                }
            }
            requireNoProblems(problems, AcbRefusalCode.RETURNS_BATCH_INVALID, "This returns report has problems");
            saved.setRecorded(recorded);
            saved.setAlreadyRecorded(entries.size() - recorded);
            saved.setLettersQueued(lettersQueued);
            batches.saveAndFlush(saved);
            audit.record(runId, null, "RETURNS_REPORT_RECORDED", null, null, "the report for " + actionDate + ": "
                    + entries.size() + " entries, " + recorded + " new, " + lettersQueued + " letter(s) queued", actor);
            return new Batched(saved.getId(), resolved.stream().map(AcbRunLine::getId).toList(), recorded);
        });
        Objects.requireNonNull(batched);
        if (batched.recorded() > 0) {
            meters.counter("acb.unpaids.recorded").increment(batched.recorded());
            launcher.launch(sender::sendDue);
        }
        AcbReturnsBatch saved = batches.findById(batched.batchId()).orElseThrow();
        Map<Integer, AcbUnpaidCounter.Counted> counts = counter.everyone();
        Map<String, AcbUnpaidCode> codeTable = codes.findAll().stream()
                .collect(Collectors.toMap(AcbUnpaidCode::getCode, c -> c));
        List<AcbRunLineDto> recordedLines = lines.findAllById(batched.lineIds()).stream()
                .sorted(Comparator.comparing(AcbRunLine::getId))
                .map(line -> view(line, codeTable, counts, null)).toList();
        return batchDto(saved, recordedLines);
    }

    private record Batched(Long batchId, List<Long> lineIds, int recorded) {
    }

    @Override
    public AcbPage<AcbReturnsBatchDto> returnsBatches(long runId, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        return AcbPage.of(batches.findByRunIdOrderByActionDateDescIdDesc(runId, pageRequest(page, size)),
                batch -> batchDto(batch, null));
    }

    @Override
    public AcbDebitDaysDto debitDays(long runId) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        Map<LocalDate, Long> reports = new HashMap<>();
        for (Object[] row : batches.countByActionDate(runId)) {
            reports.put((LocalDate) row[0], ((Number) row[1]).longValue());
        }
        List<AcbDebitDaysDto.Day> days = new ArrayList<>();
        for (Object[] row : lines.countByActionDate(runId, AcbLineOutcome.UNPAID)) {
            LocalDate day = (LocalDate) row[0];
            days.add(new AcbDebitDaysDto.Day(day.toString(), ((Number) row[1]).longValue(),
                    row[2] == null ? 0L : ((Number) row[2]).longValue(), reports.getOrDefault(day, 0L)));
        }
        return new AcbDebitDaysDto(runId, days);
    }

    private static AcbReturnsBatchDto batchDto(AcbReturnsBatch batch, List<AcbRunLineDto> recordedLines) {
        return new AcbReturnsBatchDto(batch.getId(), batch.getRunId(), AcbTime.iso(batch.getActionDate()),
                AcbTime.iso(batch.getReportDate()), batch.getSource(), batch.getEntries(), batch.getRecorded(),
                batch.getAlreadyRecorded(), batch.getLettersQueued(), batch.getRecordedBy(),
                AcbTime.iso(batch.getRecordedAt()), recordedLines);
    }

    /** Refuses with every problem named -- the first fifty, and the count of all (EH2-E). Nothing is recorded. */
    static void requireNoProblems(List<String> problems, AcbRefusalCode code, String what) {
        if (problems.isEmpty()) {
            return;
        }
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("problems", String.valueOf(problems.size()));
        for (int i = 0; i < Math.min(problems.size(), PROBLEMS_SHOWN); i++) {
            facts.put("problem." + (i + 1), problems.get(i));
        }
        throw new AcbRefusal(code, what + " (" + problems.size() + "), and nothing of it has been recorded. "
                + problems.get(0) + (problems.size() > 1 ? "; and " + (problems.size() - 1) + " more." : "."), facts);
    }

    // ------------------------------------------------------------------------------------------
    // Held over, held letters, the member search
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbPage<AcbHeldOverDto> heldOver(long runId, int page, int size) {
        AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        return AcbPage.of(returns.findHeldOver(runId, run.getActionMonth(), AcbRuns.SENT, AcbLineOutcome.UNPAID,
                        UnpaidDecision.RE_PRESENT, pageRequest(page, size)),
                row -> new AcbHeldOverDto(row.lineId(), row.runId(), row.memberId(), row.membershipNumber(),
                        row.surname(), AcbTime.iso(YearMonth.from(row.monthStart())), row.amount(), row.permitted()));
    }

    /** The note is kept to this many characters -- refused beyond it, never cut (SD3-D). */
    static final int HANDLED_NOTE_LIMIT = 300;

    /** The letters that could not go: held for want of an address, or a send that FAILED (Q46, EH-E). */
    static final Set<LetterStatus> COULD_NOT_GO = EnumSet.of(LetterStatus.QUEUED, LetterStatus.FAILED);

    @Override
    public AcbPage<AcbHeldLetterDto> heldLetters(long runId, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        return AcbPage.of(returns.findHeldLetters(runId, COULD_NOT_GO, pageRequest(page, size)), row -> {
            String now = new LetterRecipient(row.memberId(), null, null, row.personalEmail(), row.workEmail(),
                    row.preferredEmail(), null).email();
            return new AcbHeldLetterDto(row.letterId(), row.lineId(), row.memberId(), row.membershipNumber(),
                    row.surname(), row.template(), row.status(), row.reason(), LetterRecipient.sendable(now));
        });
    }

    @Override
    public AcbHeldLetterDto markHandled(long runId, long letterId, String note, String actor) {
        String how = note == null || note.isBlank() ? null : note.trim();
        if (how == null || how.length() > HANDLED_NOTE_LIMIT) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED, "Say how the letter was dealt with, in at most "
                    + HANDLED_NOTE_LIMIT + " characters.", Map.of("letterId", String.valueOf(letterId), "field", "note"));
        }
        tx.executeWithoutResult(status -> {
            AcbLetter letter = letters.findById(letterId).orElseThrow(() -> new AcbRefusal(AcbRefusalCode.LINE_NOT_FOUND,
                    "There is no letter " + letterId + ".", Map.of("letterId", String.valueOf(letterId))));
            AcbRunLine line = requireLine(runId, letter.getLineId());
            requireCouldNotGo(letter, "dealt with by hand");
            letter.setStatus(LetterStatus.HANDLED);
            letter.setHandledNote(how);
            letter.setHandledAt(AcbTime.instant(clock));
            letters.saveAndFlush(letter);
            // 🔒 THAT it was handled, never how (SD3-D): the note can hold an address or a name.
            audit.record(runId, line.getId(), "LETTER_HANDLED", null, null, "a held letter marked handled", actor);
        });
        return heldLetterDto(letterId);
    }

    @Override
    public AcbHeldLetterDto sendHeldLetter(long runId, long letterId, String actor) {
        tx.executeWithoutResult(status -> {
            AcbLetter letter = letters.findById(letterId).orElseThrow(() -> new AcbRefusal(AcbRefusalCode.LINE_NOT_FOUND,
                    "There is no letter " + letterId + ".", Map.of("letterId", String.valueOf(letterId))));
            AcbRunLine line = requireLine(runId, letter.getLineId());
            requireCouldNotGo(letter, "sent again");
            LetterRecipient recipient = returns.findRecipient(line.getMemberId()).orElse(null);
            String email = recipient == null ? null : recipient.email();
            if (email == null || !LetterRecipient.sendable(email)) {
                // 🔒 SD3-C: never to no address, and never to one that could carry a header or a second recipient.
                throw new AcbRefusal(AcbRefusalCode.LETTER_NO_ADDRESS, "This member has no address the letter can go "
                        + "to yet. Nothing has been sent.", Map.of("letterId", String.valueOf(letterId)));
            }
            AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
            UnpaidReason reason = UnpaidReason.valueOf(line.getUnpaidReason());
            String wording = codes.findById(line.getUnpaidCode()).map(AcbUnpaidCode::getWording).orElse(reason.wording());
            // Rendered NOW, from the member as they stand: the letter the sender checks is the one this person sends.
            String body = LetterRenderer.render(letter.getTemplate(), letterData(run, line, recipient, wording));
            String was = letter.getStatus().name();
            letter.setStatus(LetterStatus.RENDERED);
            letter.setStatusReason(null);
            letter.setFailureClass(null);
            letter.setRenderedAt(AcbTime.instant(clock));
            letter.setBodySha256(LetterRenderer.bodySha256(body));
            letter.setAddressSnapshot(email);
            letter.setQueuedBy(actor);
            letters.saveAndFlush(letter);
            audit.record(runId, line.getId(), "LETTER_SENT_AGAIN", null, null,
                    "a " + was.toLowerCase(java.util.Locale.ROOT) + " letter sent again", actor);
        });
        // After the commit, off this thread: the sender claims it, counts the attempt, and sends it once (D23).
        launcher.launch(sender::sendDue);
        return heldLetterDto(letterId);
    }

    /** A letter is dealt with by hand or sent again only while it could not go -- held, or FAILED. */
    private static void requireCouldNotGo(AcbLetter letter, String what) {
        if (!COULD_NOT_GO.contains(letter.getStatus())) {
            Map<String, String> facts = new LinkedHashMap<>();
            facts.put("letterId", String.valueOf(letter.getId()));
            facts.put("status", letter.getStatus().name());
            throw new AcbRefusal(AcbRefusalCode.LETTER_NOT_HELD, "Only a letter that could not go is " + what
                    + "; this one is " + letter.getStatus() + ".", facts);
        }
    }

    private AcbHeldLetterDto heldLetterDto(long letterId) {
        AcbLetter letter = letters.findById(letterId).orElseThrow();
        AcbRunLine line = lines.findById(letter.getLineId()).orElseThrow();
        String reason = letter.getStatusReason() != null ? letter.getStatusReason() : letter.getFailureClass();
        return new AcbHeldLetterDto(letter.getId(), line.getId(), line.getMemberId(), line.getMembershipNumber(),
                line.getSurname(), letter.getTemplate(), letter.getStatus(), reason, false);
    }

    @Override
    public AcbPage<AcbMemberDto> searchMembers(MemberSearchRequest request) {
        String surname = like(request.surname());
        String number = like(request.membershipNumber());
        String identity = request.identityNumber() == null || request.identityNumber().isBlank()
                ? null : request.identityNumber().trim();
        int size = Math.min(Math.max(request.size() == null ? 25 : request.size(), 1), AcbRunServiceImpl.MAX_PAGE_SIZE);
        int page = Math.max(request.page() == null ? 0 : request.page(), 0);
        if (surname == null && number == null && identity == null) {
            // An empty search is a question nobody has asked: never "everyone".
            return new AcbPage<>(List.of(), page, size, 0, 0);
        }
        return AcbPage.of(population.searchMembers(surname, number, identity, PageRequest.of(page, size)),
                row -> new AcbMemberDto(row.id(), row.membershipNumber(), row.title(), row.initials(), row.surname(),
                        row.bankName(), AcbRunServiceImpl.lastFour(row.bankAccountNumber()), row.debitDay(),
                        row.status()));
    }

    private static String like(String part) {
        if (part == null || part.isBlank()) {
            return null;
        }
        String escaped = part.trim().toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    // ------------------------------------------------------------------------------------------
    // Reading the unpaids, with the rules beside each
    // ------------------------------------------------------------------------------------------

    /** One line with its code's rule, the member's count and the decision the rules suggest (DOD-03, DOD-04). */
    private AcbRunLineDto withRules(AcbRunLine line) {
        AcbUnpaidCode code = line.getUnpaidCode() == null ? null : codes.findById(line.getUnpaidCode()).orElse(null);
        Integer unpaids = line.getOutcome() == AcbLineOutcome.UNPAID
                ? counter.of(line.getMemberId()).count().unpaids() : null;
        return view(line, code, unpaids, null);
    }

    private static AcbRunLineDto view(AcbRunLine line, Map<String, AcbUnpaidCode> codeTable,
                                      Map<Integer, AcbUnpaidCounter.Counted> counts, LetterStatus letterStatus) {
        AcbUnpaidCode code = line.getUnpaidCode() == null ? null : codeTable.get(line.getUnpaidCode());
        AcbUnpaidCounter.Counted counted = counts.get(line.getMemberId());
        Integer unpaids = line.getOutcome() != AcbLineOutcome.UNPAID ? null
                : counted == null ? 0 : counted.count().unpaids();
        return view(line, code, unpaids, letterStatus);
    }

    private static AcbRunLineDto view(AcbRunLine line, AcbUnpaidCode code, Integer unpaids, LetterStatus letterStatus) {
        ReDebit rule = code == null ? ReDebit.UNKNOWN : code.reDebit();
        UnpaidDecision suggested = line.getOutcome() == AcbLineOutcome.UNPAID && line.getDecision() == null
                ? ReDebitRules.suggestion(rule, unpaids == null ? 0 : unpaids) : null;
        return AcbRunServiceImpl.lineDto(line, suggested, letterStatus, rule, code, unpaids);
    }

    @Override
    public AcbPage<AcbRunLineDto> unpaids(long runId, UnpaidReason reason, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        PageRequest request = pageRequest(page, size);
        // The codes, read once for the page -- a handful of rows, never a query per line; the counts likewise, bounded
        // to the window like the report's.
        Map<String, AcbUnpaidCode> codeTable = codes.findAll().stream()
                .collect(Collectors.toMap(AcbUnpaidCode::getCode, c -> c));
        org.springframework.data.domain.Page<AcbRunLine> found = reason == null
                ? returns.findUnpaids(runId, AcbLineOutcome.UNPAID, request)
                : returns.findUnpaidsFor(runId, AcbLineOutcome.UNPAID, reason.name(), request);
        Map<Integer, AcbUnpaidCounter.Counted> counts = found.isEmpty() ? Map.of() : counter.everyone();
        // Where each line's letter is -- one query for the page, never one per line. The unpaid's own letter: an
        // ending letter on the same line (DOD-24, V71) is the member's, not the unpaid's.
        Map<Long, LetterStatus> letterOf = new HashMap<>();
        if (!found.isEmpty()) {
            letters.findByLineIdIn(found.getContent().stream().map(AcbRunLine::getId).toList()).stream()
                    .filter(letter -> letter.getTemplate() != LetterTemplate.MEMBERSHIP_ENDED)
                    .forEach(letter -> letterOf.put(letter.getLineId(), letter.getStatus()));
        }
        return AcbPage.of(found, line -> view(line, codeTable, counts, letterOf.get(line.getId())));
    }

    @Override
    public AcbReturnsSummaryDto summary(long runId) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        Map<String, Integer> byOutcome = counts(returns.countByOutcome(runId));
        Map<String, Integer> byReason = counts(returns.countUnpaidByReason(runId, AcbLineOutcome.UNPAID));
        Map<String, Integer> byLetter = counts(letters.countByStatusForRun(runId));
        int undecided = (int) lines.countUndecided(runId, AcbLineOutcome.UNPAID);
        return new AcbReturnsSummaryDto(runId, byOutcome, byReason, undecided,
                byReason.getOrDefault(UnpaidReason.OTHER.name(), 0), byLetter);
    }

    // ------------------------------------------------------------------------------------------
    // Three unpaids: the report, and the ending a staff member confirms (DOD-22, DOD-24)
    // ------------------------------------------------------------------------------------------

    /** The report reads this many months: the clock's own and the twelve before it (SD3-K). */
    static final int WINDOW_MONTHS = AcbUnpaidCounter.WINDOW_MONTHS;

    /** Members listed from this many unpaids: at two, one more makes three. */
    static final int LISTED_FROM = 2;

    @Override
    public AcbTerminationReportDto terminationReport(int page, int size) {
        Map<Integer, AcbUnpaidCounter.Counted> everyone = counter.everyone();
        List<AcbTerminationReportDto.Candidate> candidates = new ArrayList<>();
        int openingMembers = 0;
        for (AcbUnpaidCounter.Counted counted : everyone.values()) {
            if (counted.openingUnpaids() > 0) {
                openingMembers++;
            }
            if (counted.count().unpaids() < LISTED_FROM) {
                continue;
            }
            UnpaidCounting.Count count = counted.count();
            candidates.add(new AcbTerminationReportDto.Candidate(counted.memberId(), counted.membershipNumber(),
                    counted.surname(), count.unpaids(), count.verdict().terminate(), counted.twelveMonthTotal(),
                    count.verdict().unpaidMonths().stream().map(YearMonth::toString).toList(), counted.openingUnpaids(),
                    counted.status(), AcbTime.iso(count.thirdUnpaidOn()), counted.capturedUnpaids()));
        }
        candidates.sort(Comparator.comparing(AcbTerminationReportDto.Candidate::terminate).reversed()
                .thenComparing(Comparator.comparingInt(AcbTerminationReportDto.Candidate::unpaidCount).reversed())
                .thenComparing(c -> String.valueOf(c.membershipNumber())));

        int pageSize = Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE);
        int pageNumber = Math.max(page, 0);
        int start = Math.min(pageNumber * pageSize, candidates.size());
        List<AcbTerminationReportDto.Candidate> content =
                List.copyOf(candidates.subList(start, Math.min(start + pageSize, candidates.size())));
        int totalPages = (candidates.size() + pageSize - 1) / pageSize;

        LocalDate firstSent = runs.firstMonthIn(AcbRuns.SENT);
        String countingFrom = firstSent == null ? null : YearMonth.from(firstSent).toString();
        String blindness = openingMembers == 0 ? BLINDNESS
                : "Counted from the opening arrears position captured at switch-over (" + openingMembers
                        + " member(s)) and every month this system has sent since. A member not on it started at nought.";
        return new AcbTerminationReportDto(countingFrom, blindness,
                new AcbPage<>(content, pageNumber, pageSize, candidates.size(), totalPages));
    }

    @Override
    public byte[] letterSpecimen(LetterTemplate template) {
        return za.co.titandynamix.sasbo_online_backend.acb.returns.LetterDocument.specimen(template,
                AcbTime.today(clock));
    }

    @Override
    public AcbMemberStatusDto terminate(Integer memberId, String actor) {
        return terminate(memberId, actor, AcbTime.instant(clock));
    }

    /**
     * The ending at {@code at} -- the seed's past, or now. A staff member confirms each one (DOD-22); the member gets
     * a short letter (DOD-24), hung on the last unpaid the count holds. Asked again it changes nothing.
     */
    @Override
    public AcbMemberStatusDto terminate(Integer memberId, String actor, Instant at) {
        return tx.execute(state -> {
            Member current = memberService.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
            AcbUnpaidCounter.Counted counted = counter.of(memberId);
            boolean ended = isTerminated(current.getStatus());
            if (!counted.count().atTheLimit() && !ended) {
                String count = String.valueOf(counted.count().unpaids());
                throw new AcbRefusal(AcbRefusalCode.TERMINATION_NOT_DUE,
                        "Three unpaid debits end a membership, and a staff member confirms each one (debit order "
                                + "rules); this member has " + count + ".",
                        Map.of("memberId", String.valueOf(memberId), "unpaidCount", count));
            }
            // R3-9: the status, through the narrow method -- never the whole-record update.
            Member member = memberService.changeStatus(memberId, new MemberStatusChangeRequest(
                            MemberStatuses.TERMINATED, MemberStatusReason.SUBS_IN_ARREARS, null, null, actor, at))
                    .orElseThrow(() -> memberNotFound(memberId));
            AcbLetter letter = null;
            Long lineId = counted.count().lastUnpaidLineId();
            AcbRunLine line = lineId == null ? null : lines.findById(lineId).orElse(null);
            if (line != null) {
                AcbRun run = runs.findById(line.getRunId()).orElseThrow(() -> AcbRuns.notFound(line.getRunId()));
                letter = letters.findByLineIdAndTemplate(line.getId(), LetterTemplate.MEMBERSHIP_ENDED)
                        .orElseGet(() -> queueLetter(run, line, LetterTemplate.MEMBERSHIP_ENDED, wordingOf(line),
                                memberIdOf -> returns.findRecipient(memberIdOf).orElse(null), actor));
            }
            if (!ended) {
                String said = "membership ended after three unpaids, confirmed"
                        + (letter == null ? "; no letter: no unpaid of this system's to write it from" : "");
                if (line != null) {
                    audit.record(line.getRunId(), line.getId(), AcbRunlessEvent.MEMBER_ENDED.name(), null, null, said,
                            actor);
                } else {
                    audit.recordRunless(AcbRunlessEvent.MEMBER_ENDED, said, actor);
                }
            }
            return new AcbMemberStatusDto(member.getId(), member.getMembershipNumber(), member.getStatus(),
                    MemberStatusReason.SUBS_IN_ARREARS.name(), null, letter == null ? null : letter.getId(),
                    letter == null ? null : letter.getStatus().name(), AcbTime.iso(counted.count().thirdUnpaidOn()),
                    null, null);
        });
    }

    @Override
    public AcbMemberStatusDto suspendMember(long runId, long lineId, SuspendMemberRequest request, String actor) {
        return suspendMember(runId, lineId, request, actor, AcbTime.instant(clock));
    }

    /** The suspension at {@code at} -- the seed's past, or now. */
    @Override
    public AcbMemberStatusDto suspendMember(long runId, long lineId, SuspendMemberRequest request, String actor,
                                            Instant at) {
        return tx.execute(state -> {
            AcbRunLine line = requireLine(runId, lineId);
            if (line.getOutcome() != AcbLineOutcome.UNPAID || line.getDecision() != UnpaidDecision.SUSPEND) {
                throw new AcbRefusal(AcbRefusalCode.UNPAID_NOT_SUSPENDED,
                        "Only an unpaid decided SUSPEND suspends its member; this line is " + line.getOutcome()
                                + (line.getDecision() == null ? ", undecided" : ", " + line.getDecision()) + ".",
                        lineFacts(line));
            }
            Member current = memberService.findById(line.getMemberId())
                    .orElseThrow(() -> new IllegalStateException("line " + lineId + " debits no member on record"));
            if (isTerminated(current.getStatus())) {
                throw new AcbRefusal(AcbRefusalCode.MEMBER_TERMINATED,
                        "This member's membership is terminated; a suspension would not undo that.", lineFacts(line));
            }
            Integer chosen = request == null ? null : request.suspensionCode();
            Integer number = chosen != null ? chosen : line.getUnpaidCode() == null ? null
                    : codes.findById(line.getUnpaidCode()).map(AcbUnpaidCode::getSuspensionCode).orElse(null);
            if (number == null) {
                Map<String, String> facts = lineFacts(line);
                throw new AcbRefusal(AcbRefusalCode.SUSPENSION_CODE_REQUIRED, "The bank's code "
                        + line.getUnpaidCode() + " names none of SASBO's suspension codes yet. Choose the code to "
                        + "suspend this member with.", facts);
            }
            SasboReferenceRepository.Code sasbo = reference.suspensionCode(number).orElseThrow(() -> new AcbRefusal(
                    AcbRefusalCode.SUSPENSION_CODE_UNKNOWN, "SASBO's suspension code " + number + " is not on the "
                    + "list. Choose one that is.", Map.of("suspensionCode", String.valueOf(number), "known",
                    reference.suspensionCodeNumbers().stream().map(String::valueOf).collect(Collectors.joining(",")))));
            String note = request == null || request.note() == null || request.note().isBlank() ? null
                    : request.note().trim();
            MemberService.StatusChangeOutcome outcome = memberService.changeStatus(line.getMemberId(),
                            new MemberService.StatusChange(MemberStatuses.SUSPENDED, null, MemberStatusReason.SUSPENSION_CODE,
                                    null, sasbo.id(), note, actor, at))
                    .orElseThrow(() -> memberNotFound(line.getMemberId()));
            if (outcome.written() != null) {
                // 🔒 The code, never the note (SD3-D).
                audit.record(runId, lineId, "MEMBER_SUSPENDED", null, null, "suspended with SASBO code " + number,
                        actor);
            }
            Member member = outcome.member();
            return new AcbMemberStatusDto(member.getId(), member.getMembershipNumber(), member.getStatus(),
                    MemberStatusReason.SUSPENSION_CODE.name(), number, null, null, null, null, null);
        });
    }

    // ------------------------------------------------------------------------------------------
    // A member's return: reactivated (DOD-25, DOD-26), or rejoining (DOD-27)
    // ------------------------------------------------------------------------------------------

    @Override
    public AcbMemberStatusDto reactivate(Integer memberId, MemberReturnRequest request, String actor) {
        return reactivate(memberId, request, actor, AcbTime.instant(clock));
    }

    /** The reactivation at {@code at} -- the seed's past, or now. */
    @Override
    public AcbMemberStatusDto reactivate(Integer memberId, MemberReturnRequest request, String actor, Instant at) {
        String note = noteOf(request);
        return tx.execute(state -> {
            Member current = memberService.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
            if (MemberStatuses.kindOf(current.getStatus()) != MemberStatusKind.SUSPENDED) {
                throw notSuspended(memberId, current.getStatus());
            }
            MemberService.StatusChangeOutcome outcome = memberService.changeStatus(memberId,
                            new MemberService.StatusChange(MemberStatuses.ACTIVE, current.getStatus(),
                                    MemberStatusReason.REINSTATED, null, null, note, actor, at))
                    .orElseThrow(() -> memberNotFound(memberId));
            if (outcome.stale()) {
                throw notSuspended(memberId, outcome.member().getStatus());
            }
            int withdrawn = withdrawReDebits(memberId, ReDebitWithdrawal.REACTIVATED,
                    LocalDate.ofInstant(at, AcbTime.ZONE), at);
            audit.recordRunless(AcbRunlessEvent.MEMBER_REACTIVATED, "a suspended member made active again; "
                    + withdrawn + " waiting re-debit(s) withdrawn: only the current month is collected", actor);
            Member member = outcome.member();
            return new AcbMemberStatusDto(member.getId(), member.getMembershipNumber(), member.getStatus(),
                    MemberStatusReason.REINSTATED.name(), null, null, null, null, null, withdrawn);
        });
    }

    @Override
    public AcbMemberStatusDto rejoin(Integer memberId, MemberReturnRequest request, String actor) {
        return rejoin(memberId, request, actor, AcbTime.instant(clock));
    }

    /** The rejoining at {@code at} -- the seed's past, or now. */
    AcbMemberStatusDto rejoin(Integer memberId, MemberReturnRequest request, String actor, Instant at) {
        LocalDate today = LocalDate.ofInstant(at, AcbTime.ZONE);
        LocalDate joinedOn = request == null || request.joinedOn() == null || request.joinedOn().isBlank()
                ? today : parseDate(request.joinedOn());
        if (joinedOn.isAfter(today)) {
            throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "A join date is never in the future.",
                    Map.of("date", joinedOn.toString()));
        }
        String note = noteOf(request);
        return tx.execute(state -> {
            Member current = memberService.findById(memberId).orElseThrow(() -> memberNotFound(memberId));
            if (MemberStatuses.kindOf(current.getStatus()) != MemberStatusKind.LEAVING) {
                throw notEnded(memberId, current.getStatus());
            }
            LocalDate before = current.getJoinDate();
            MemberService.StatusChangeOutcome outcome = memberService.changeStatus(memberId,
                            new MemberService.StatusChange(MemberStatuses.ACTIVE, current.getStatus(),
                                    MemberStatusReason.REINSTATED, null, null, note, actor, at))
                    .orElseThrow(() -> memberNotFound(memberId));
            if (outcome.stale()) {
                throw notEnded(memberId, outcome.member().getStatus());
            }
            // The new join date on the member the status change holds, so the one save carries both; the history row
            // beside it, as the member's other saves write theirs.
            Member member = outcome.member();
            member.setJoinDate(joinedOn);
            jdbc.update("INSERT INTO member_changes (member_id, save_id, field, old_value, new_value, changed_by, "
                            + "changed_at) VALUES (?, ?, 'joinDate', ?, ?, ?, ?)",
                    memberId, UUID.randomUUID().toString(), before == null ? null : before.toString(),
                    joinedOn.toString(), actor, Timestamp.from(at));
            int withdrawn = withdrawReDebits(memberId, ReDebitWithdrawal.REJOINED, joinedOn.minusDays(1), at);
            audit.recordRunless(AcbRunlessEvent.MEMBER_REJOINED, "a member whose membership ended joined again on "
                    + joinedOn + "; " + withdrawn + " re-debit(s) from before withdrawn", actor);
            return new AcbMemberStatusDto(member.getId(), member.getMembershipNumber(), member.getStatus(),
                    MemberStatusReason.REINSTATED.name(), null, null, null, null, joinedOn.toString(), withdrawn);
        });
    }

    /**
     * Withdraws the member's waiting re-debits of unpaids on or before {@code upTo} -- decided RE_PRESENT, not yet
     * presented by a live run -- in the caller's transaction. The unpaid stays UNPAID; the withdrawal says why.
     */
    private int withdrawReDebits(Integer memberId, ReDebitWithdrawal why, LocalDate upTo, Instant at) {
        return jdbc.update("UPDATE acb_run_lines SET re_debit_withdrawn = ?, re_debit_withdrawn_at = ? "
                        + "WHERE member_id = ? AND outcome = ? AND decision = ? AND re_debit_withdrawn IS NULL "
                        + "AND action_date <= ? AND id NOT IN (SELECT x.re_presents_line_id FROM acb_run_lines x "
                        + "WHERE x.re_presents_line_id IS NOT NULL AND x.run_released = FALSE)",
                why.name(), Timestamp.from(at), memberId, AcbLineOutcome.UNPAID.name(),
                UnpaidDecision.RE_PRESENT.name(), upTo);
    }

    private static String noteOf(MemberReturnRequest request) {
        return request == null || request.note() == null || request.note().isBlank() ? null : request.note().trim();
    }

    private static boolean isTerminated(String status) {
        return status != null && MemberStatuses.TERMINATED.equalsIgnoreCase(status.trim());
    }

    private static AcbRefusal memberNotFound(Integer memberId) {
        return new AcbRefusal(AcbRefusalCode.MEMBER_NOT_FOUND, "That member is no longer on record.",
                Map.of("memberId", String.valueOf(memberId)));
    }

    private static AcbRefusal notSuspended(Integer memberId, String status) {
        return new AcbRefusal(AcbRefusalCode.MEMBER_NOT_SUSPENDED, "Only a suspended member is made active again; "
                + "this member is " + status + ".", Map.of("memberId", String.valueOf(memberId),
                "status", String.valueOf(status)));
    }

    private static AcbRefusal notEnded(Integer memberId, String status) {
        return new AcbRefusal(AcbRefusalCode.MEMBER_NOT_ENDED, "Only a member whose membership ended joins again; "
                + "this member is " + status + ".", Map.of("memberId", String.valueOf(memberId),
                "status", String.valueOf(status)));
    }

    // ------------------------------------------------------------------------------------------

    /**
     * Queues {@code template}'s letter on {@code line} and renders it now if the member can be emailed. Nothing for a
     * reason that sends none, or where the line has that letter already.
     */
    private AcbLetter queueLetter(AcbRun run, AcbRunLine line, LetterTemplate template, String wording,
                                  Function<Integer, LetterRecipient> recipients, String actor) {
        if (template == null || letters.findByLineIdAndTemplate(line.getId(), template).isPresent()) {
            return null;
        }
        Instant now = AcbTime.instant(clock);
        AcbLetter letter = new AcbLetter();
        letter.setLineId(line.getId());
        letter.setTemplate(template);
        letter.setChannel(LetterChannel.EMAIL);
        letter.setAttempts(0);
        letter.setCreatedAt(now);
        letter.setQueuedBy(actor);

        LetterRecipient recipient = recipients.apply(line.getMemberId());
        String email = recipient == null ? null : recipient.email();
        if (email == null) {
            letter.setStatus(LetterStatus.QUEUED);
            letter.setStatusReason("NO_EMAIL");
        } else if (!LetterRecipient.sendable(email)) {
            // 🔒 SD3-C: an address that could carry a header or a second recipient is never used -- the
            // letter is held for someone to deal with, like one with no address at all.
            letter.setStatus(LetterStatus.QUEUED);
            letter.setStatusReason("INVALID_ADDRESS");
        } else {
            String body = LetterRenderer.render(template, letterData(run, line, recipient, wording));
            letter.setStatus(LetterStatus.RENDERED);
            letter.setRenderedAt(now);
            letter.setBodySha256(LetterRenderer.bodySha256(body));
            letter.setAddressSnapshot(email);
        }
        AcbLetter saved = letters.save(letter);
        audit.record(run.getId(), line.getId(), "LETTER_" + letter.getStatus(), null, null,
                template + (letter.getStatusReason() == null ? "" : ", " + letter.getStatusReason()), actor);
        return saved;
    }

    /** The wording an unpaid's letters print: the code's own, else its reason's. */
    private String wordingOf(AcbRunLine line) {
        UnpaidReason reason = line.getUnpaidReason() == null ? UnpaidReason.OTHER : UnpaidReason.valueOf(line.getUnpaidReason());
        return line.getUnpaidCode() == null ? reason.wording()
                : codes.findById(line.getUnpaidCode()).map(AcbUnpaidCode::getWording).orElse(reason.wording());
    }

    private LetterRenderer.LetterData letterData(AcbRun run, AcbRunLine line, LetterRecipient recipient, String wording) {
        return LetterRenderer.dataFor(AcbTime.today(clock), run, line, recipient, wording);
    }

    private AcbRun requireReturnable(long runId) {
        AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        if (!RETURNABLE.contains(run.getStatus())) {
            throw new AcbRefusal(AcbRefusalCode.RUN_NOT_IN_STATE,
                    "Unpaids are recorded against a run whose file went to the bank; this one is "
                            + run.getStatus() + ".", AcbRuns.facts(run));
        }
        return run;
    }

    private AcbRunLine requireLine(long runId, long lineId) {
        return lines.findById(lineId)
                .filter(line -> Objects.equals(line.getRunId(), runId))
                .orElseThrow(() -> new AcbRefusal(AcbRefusalCode.LINE_NOT_FOUND,
                        "Run " + runId + " has no line " + lineId + ".",
                        Map.of("runId", String.valueOf(runId), "lineId", String.valueOf(lineId))));
    }

    private static PageRequest pageRequest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE));
    }

    private static Map<String, String> lineFacts(AcbRunLine line) {
        Map<String, String> facts = new LinkedHashMap<>();
        facts.put("lineId", String.valueOf(line.getId()));
        facts.put("outcome", line.getOutcome().name());
        if (line.getUnpaidCode() != null) {
            facts.put("unpaidCode", line.getUnpaidCode());
        }
        if (line.getDecision() != null) {
            facts.put("decision", line.getDecision().name());
        }
        return facts;
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.trim());
        } catch (DateTimeParseException malformed) {
            throw new AcbRefusal(AcbRefusalCode.DATE_INVALID, "Dates are written YYYY-MM-DD, for example 2026-10-21.",
                    Map.of("date", String.valueOf(text)));
        }
    }

    private static Map<String, Integer> counts(List<Object[]> rows) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Object[] row : rows) {
            counts.put(String.valueOf(row[0]), ((Number) row[1]).intValue());
        }
        return counts;
    }
}

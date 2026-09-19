package za.co.titandynamix.sasbo_online_backend.services.impl;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRecipient;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRenderer;
import za.co.titandynamix.sasbo_online_backend.acb.returns.PresentationRow;
import za.co.titandynamix.sasbo_online_backend.acb.returns.TerminationRule;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The returns loop, recorded by hand from the bank's report until the report can be imported.
 *
 * <h2>Recording twice records once</h2>
 * The same unpaid recorded again -- same line, same code, the same decision or none -- changes
 * nothing and answers with the line as it stands. A different code or decision for a line already
 * recorded is refused with {@code UNPAID_ALREADY_RECORDED}, naming what is there: a return is a
 * fact about what the bank did, and it is corrected deliberately, not overwritten by a second click.
 *
 * <h2>The letter goes with the unpaid, the same day</h2>
 * SASBO: <i>"the letter needs to go out as soon as we have done the updates."</i> So recording an
 * unpaid queues its letter in the same transaction, and renders it at once for a member with an
 * email address -- the rendered copy's hash and the address it was rendered for are kept, never
 * the body. A member with no email address leaves the letter QUEUED with the reason NO_EMAIL, shown,
 * until SASBO says whether it is posted or held. Sending is not built behind any route while the API
 * is anonymous: a letter to a member is a real-world act.
 */
@Service
public class AcbReturnsServiceImpl implements AcbReturnsService {

    /** A run whose file went to the bank -- the only kind whose lines can come back unpaid. */
    private static final Set<AcbRunStatus> RETURNABLE = EnumSet.of(AcbRunStatus.SUBMITTED, AcbRunStatus.RETURNED);

    private static final String BLINDNESS = "Counted from the first month this system sent a file. A member "
            + "already in arrears in the old system is not counted until SASBO's opening arrears position "
            + "is imported.";

    private final AcbRunRepository runs;
    private final AcbRunLineRepository lines;
    private final AcbReturnsRepository returns;
    private final AcbUnpaidCodeRepository codes;
    private final AcbLetterRepository letters;
    private final AcbAuditTrail audit;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final MeterRegistry meters;

    public AcbReturnsServiceImpl(AcbRunRepository runs, AcbRunLineRepository lines, AcbReturnsRepository returns,
                                 AcbUnpaidCodeRepository codes, AcbLetterRepository letters, AcbAuditTrail audit,
                                 Clock clock, PlatformTransactionManager transactions, MeterRegistry meters) {
        this.runs = runs;
        this.lines = lines;
        this.returns = returns;
        this.codes = codes;
        this.letters = letters;
        this.audit = audit;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
        this.meters = meters;
    }

    @Override
    public AcbRunLineDto recordUnpaid(long runId, long lineId, RecordUnpaidRequest request, String actor) {
        LocalDate notedOn = parseDate(request.notedOn());
        String code = request.code().trim();
        Boolean recorded = tx.execute(status -> {
            AcbRun run = requireReturnable(runId);
            AcbRunLine line = requireLine(runId, lineId);
            if (line.getOutcome() == AcbLineOutcome.UNPAID) {
                boolean same = code.equals(line.getUnpaidCode())
                        && (request.decision() == null || request.decision() == line.getDecision());
                if (same) {
                    return false;
                }
                throw new AcbRefusal(AcbRefusalCode.UNPAID_ALREADY_RECORDED,
                        "This debit is already recorded as unpaid with code " + line.getUnpaidCode()
                                + (line.getDecision() == null ? ", undecided" : ", " + line.getDecision()) + ".",
                        lineFacts(line));
            }
            // PAID is only a presumption -- the report did not list the debit when the returns were
            // taken in (D28) -- so a return that arrives late overturns it, and the audit says so.
            boolean late = line.getOutcome() == AcbLineOutcome.PAID;
            Optional<AcbUnpaidCode> known = codes.findById(code);
            UnpaidReason reason = known.map(AcbUnpaidCode::getReason).orElse(UnpaidReason.OTHER);
            line.setOutcome(AcbLineOutcome.UNPAID);
            line.setUnpaidCode(code);
            line.setUnpaidReason(reason.name());
            line.setUnpaidNotedOn(notedOn);
            line.setDecision(request.decision());
            lines.saveAndFlush(line);
            audit.record(runId, lineId, "UNPAID_RECORDED", null, null,
                    "code " + code + " (" + reason + ")" + (request.decision() == null ? ", undecided"
                            : ", " + request.decision()) + (late ? ", a late return: was PAID" : ""), actor);
            queueLetter(run, line, reason, known.map(AcbUnpaidCode::getWording).orElse(reason.wording()), actor);
            return true;
        });
        if (Boolean.TRUE.equals(recorded)) {
            meters.counter("acb.unpaids.recorded").increment();
        }
        return AcbRunServiceImpl.lineDto(requireLine(runId, lineId));
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
            line.setDecision(decision);
            lines.saveAndFlush(line);
            audit.record(runId, lineId, "UNPAID_DECIDED", null, null, decision.name(), actor);
        });
        return AcbRunServiceImpl.lineDto(requireLine(runId, lineId));
    }

    @Override
    public AcbPage<AcbRunLineDto> unpaids(long runId, UnpaidReason reason, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE));
        return AcbPage.of(reason == null
                        ? returns.findUnpaids(runId, AcbLineOutcome.UNPAID, request)
                        : returns.findUnpaidsFor(runId, AcbLineOutcome.UNPAID, reason.name(), request),
                AcbRunServiceImpl::lineDto);
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

    @Override
    public AcbTerminationReportDto terminationReport() {
        List<PresentationRow> rows = returns.findHistoriesOfMembersWithUnpaids(AcbRuns.SENT, AcbLineOutcome.UNPAID);
        Map<Integer, List<PresentationRow>> byMember = rows.stream()
                .collect(Collectors.groupingBy(PresentationRow::memberId, TreeMap::new, Collectors.toList()));
        List<AcbTerminationReportDto.Candidate> candidates = new ArrayList<>();
        byMember.forEach((memberId, history) -> {
            List<TerminationRule.Presentation> presentations =
                    history.stream().map(PresentationRow::presentation).toList();
            TerminationRule.Verdict every = TerminationRule.judge(presentations, TerminationRule.Reading.EVERY_UNPAID_COUNTS);
            TerminationRule.Verdict retries = TerminationRule.judge(presentations, TerminationRule.Reading.RETRIES_ONLY);
            if (every.terminate() || retries.terminate()) {
                PresentationRow latest = history.stream()
                        .max(Comparator.comparing(PresentationRow::actionDate).thenComparing(PresentationRow::lineId))
                        .orElseThrow();
                candidates.add(new AcbTerminationReportDto.Candidate(memberId, latest.membershipNumber(),
                        latest.surname(), every.terminate(), every.count(), retries.terminate(), retries.count(),
                        every.unpaidMonths().stream().map(YearMonth::toString).toList()));
            }
        });
        LocalDate firstSent = runs.firstMonthIn(AcbRuns.SENT);
        String countingFrom = firstSent == null ? null : YearMonth.from(firstSent).toString();
        return new AcbTerminationReportDto(countingFrom, BLINDNESS, candidates);
    }

    // ------------------------------------------------------------------------------------------

    /** Queues the reason's letter and renders it now if the member can be emailed. */
    private void queueLetter(AcbRun run, AcbRunLine line, UnpaidReason reason, String wording, String actor) {
        LetterTemplate template = reason.letter();
        if (template == null || letters.findByLineId(line.getId()).isPresent()) {
            return;
        }
        Instant now = AcbTime.instant(clock);
        AcbLetter letter = new AcbLetter();
        letter.setLineId(line.getId());
        letter.setTemplate(template);
        letter.setChannel("EMAIL");
        letter.setAttempts(0);
        letter.setCreatedAt(now);

        LetterRecipient recipient = returns.findRecipient(line.getMemberId()).orElse(null);
        String email = recipient == null ? null : recipient.email();
        if (email == null) {
            letter.setStatus(LetterStatus.QUEUED);
            letter.setStatusReason("NO_EMAIL");
        } else {
            String body = LetterRenderer.render(template, letterData(run, line, recipient, wording));
            letter.setStatus(LetterStatus.RENDERED);
            letter.setRenderedAt(now);
            letter.setBodySha256(AcbFileServiceImpl.sha256(body.getBytes(StandardCharsets.UTF_8)));
            letter.setAddressSnapshot(email);
        }
        letters.save(letter);
        audit.record(run.getId(), line.getId(), "LETTER_" + letter.getStatus(), null, null,
                template + (letter.getStatusReason() == null ? "" : ", " + letter.getStatusReason()), actor);
    }

    private LetterRenderer.LetterData letterData(AcbRun run, AcbRunLine line, LetterRecipient recipient, String wording) {
        return new LetterRenderer.LetterData(
                AcbTime.today(clock), recipient.areaName(), recipient.title(), line.getInitials(), line.getSurname(),
                recipient.addressLines(), null, line.getMembershipNumber(), line.getAccountNumber(),
                line.getBankName(), YearMonth.from(run.getActionMonth()), wording);
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

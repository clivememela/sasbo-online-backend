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
import za.co.titandynamix.sasbo_online_backend.acb.returns.OpeningRow;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCode;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterChannel;
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
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
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
import java.util.HashMap;

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

    public AcbReturnsServiceImpl(AcbRunRepository runs, AcbRunLineRepository lines, AcbReturnsRepository returns,
                                 AcbUnpaidCodeRepository codes, AcbLetterRepository letters, AcbAuditTrail audit,
                                 AcbPopulationRepository population,
                                 Clock clock, PlatformTransactionManager transactions, MeterRegistry meters) {
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
        return withSuggestion(requireLine(runId, lineId));
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
        return withSuggestion(requireLine(runId, lineId));
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
            line.setDecision(UnpaidDecision.RE_PRESENT);
            line.setPermissionNote(permission);
            lines.saveAndFlush(line);
            // 🔒 THAT permission was given, never what it says (SD3-D).
            audit.record(runId, lineId, "PERMISSION_RECORDED", null, null,
                    "the member's permission for a further re-debit recorded", actor);
        });
        return withSuggestion(requireLine(runId, lineId));
    }

    @Override
    public AcbPage<AcbHeldOverDto> heldOver(long runId, int page, int size) {
        AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE));
        return AcbPage.of(returns.findHeldOver(runId, run.getActionMonth(), AcbRuns.SENT, AcbLineOutcome.UNPAID,
                        UnpaidDecision.RE_PRESENT, request),
                row -> new AcbHeldOverDto(row.lineId(), row.runId(), row.memberId(), row.membershipNumber(),
                        row.surname(), AcbTime.iso(YearMonth.from(row.monthStart())), row.amount(), row.permitted()));
    }

    /** The note is kept to this many characters -- refused beyond it, never cut (SD3-D). */
    static final int HANDLED_NOTE_LIMIT = 300;

    @Override
    public AcbPage<AcbHeldLetterDto> heldLetters(long runId, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE));
        return AcbPage.of(returns.findHeldLetters(runId, LetterStatus.QUEUED, request), row -> {
            String now = new LetterRecipient(row.memberId(), null, null, row.personalEmail(), row.workEmail(),
                    row.preferredEmail(), null).email();
            return new AcbHeldLetterDto(row.letterId(), row.lineId(), row.memberId(), row.membershipNumber(),
                    row.surname(), row.template(), row.reason(), LetterRecipient.sendable(now));
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
            if (letter.getStatus() != LetterStatus.QUEUED) {
                Map<String, String> facts = new LinkedHashMap<>();
                facts.put("letterId", String.valueOf(letterId));
                facts.put("status", letter.getStatus().name());
                throw new AcbRefusal(AcbRefusalCode.LETTER_NOT_HELD, "Only a held letter is dealt with by hand; this one is "
                        + letter.getStatus() + ".", facts);
            }
            letter.setStatus(LetterStatus.HANDLED);
            letter.setHandledNote(how);
            letter.setHandledAt(AcbTime.instant(clock));
            letters.saveAndFlush(letter);
            // 🔒 THAT it was handled, never how (SD3-D): the note can hold an address or a name.
            audit.record(runId, line.getId(), "LETTER_HANDLED", null, null, "a held letter marked handled", actor);
        });
        return heldLetterDto(letterId);
    }

    private AcbHeldLetterDto heldLetterDto(long letterId) {
        AcbLetter letter = letters.findById(letterId).orElseThrow();
        AcbRunLine line = lines.findById(letter.getLineId()).orElseThrow();
        return new AcbHeldLetterDto(letter.getId(), line.getId(), line.getMemberId(), line.getMembershipNumber(),
                line.getSurname(), letter.getTemplate(), letter.getStatusReason(), false);
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

    /** A line, with the decision its code suggests while it is an undecided unpaid (Q40, Q44). */
    private AcbRunLineDto withSuggestion(AcbRunLine line) {
        UnpaidDecision suggested = line.getUnpaidCode() == null ? null
                : codes.findById(line.getUnpaidCode()).map(AcbUnpaidCode::suggestedDecision).orElse(null);
        return AcbRunServiceImpl.lineDto(line, suggested);
    }

    @Override
    public AcbPage<AcbRunLineDto> unpaids(long runId, UnpaidReason reason, int page, int size) {
        runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), AcbRunServiceImpl.MAX_PAGE_SIZE));
        // The codes' suggestions, read once for the page -- a handful of rows, never a query per line.
        Map<String, UnpaidDecision> suggestions = new HashMap<>();
        codes.findAll().forEach(code -> suggestions.put(code.getCode(), code.suggestedDecision()));
        org.springframework.data.domain.Page<AcbRunLine> found = reason == null
                ? returns.findUnpaids(runId, AcbLineOutcome.UNPAID, request)
                : returns.findUnpaidsFor(runId, AcbLineOutcome.UNPAID, reason.name(), request);
        // Where each line's letter is -- one query for the page, never one per line.
        Map<Long, LetterStatus> letterOf = new HashMap<>();
        if (!found.isEmpty()) {
            letters.findByLineIdIn(found.getContent().stream().map(AcbRunLine::getId).toList())
                    .forEach(letter -> letterOf.put(letter.getLineId(), letter.getStatus()));
        }
        return AcbPage.of(found, line -> AcbRunServiceImpl.lineDto(line, suggestions.get(line.getUnpaidCode()),
                letterOf.get(line.getId())));
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

    /** The report reads this many months: the clock's own and the twelve before it (SD3-K). */
    static final int WINDOW_MONTHS = 13;

    /** Members listed from this many unpaids: at two, one more makes three. */
    static final int LISTED_FROM = 2;

    @Override
    public AcbTerminationReportDto terminationReport(int page, int size) {
        YearMonth current = AcbTime.currentMonth(clock);
        YearMonth from = current.minusMonths(WINDOW_MONTHS - 1L);
        YearMonth twelveFrom = current.minusMonths(11);
        Map<Integer, List<PresentationRow>> byMember = returns.findHistoriesOfMembersWithUnpaids(AcbRuns.SENT,
                        AcbLineOutcome.UNPAID, from.atDay(1)).stream()
                .collect(Collectors.groupingBy(PresentationRow::memberId, TreeMap::new, Collectors.toList()));
        Map<Integer, OpeningRow> opening = returns.findOpeningPosition().stream()
                .collect(Collectors.toMap(OpeningRow::memberId, row -> row, (a, b) -> a));
        java.util.SortedSet<Integer> members = new java.util.TreeSet<>(byMember.keySet());
        members.addAll(opening.keySet());

        List<AcbTerminationReportDto.Candidate> candidates = new ArrayList<>();
        for (Integer memberId : members) {
            List<PresentationRow> history = byMember.getOrDefault(memberId, List.of());
            List<TerminationRule.Presentation> presentations = new ArrayList<>(
                    history.stream().map(PresentationRow::presentation).toList());
            OpeningRow brought = opening.get(memberId);
            int openingUnpaids = brought == null ? 0 : brought.unpaidsStanding();
            for (int i = 0; i < openingUnpaids; i++) {
                // The old system's unpaids, as presentations dated in their last month -- before any
                // of this system's own, which begin after it (SD3-J). Negative ids sort them first.
                presentations.add(new TerminationRule.Presentation(-1L - i, brought.lastUnpaidMonth(),
                        AcbLineOutcome.UNPAID));
            }
            TerminationRule.Verdict verdict = TerminationRule.judge(presentations);
            if (verdict.count() < LISTED_FROM) {
                continue;
            }
            int twelveMonthTotal = (int) presentations.stream()
                    .filter(p -> p.outcome() == AcbLineOutcome.UNPAID && !YearMonth.from(p.actionDate()).isBefore(twelveFrom))
                    .count();
            PresentationRow latest = history.stream()
                    .max(Comparator.comparing(PresentationRow::actionDate).thenComparing(PresentationRow::lineId))
                    .orElse(null);
            String number = latest != null ? latest.membershipNumber() : brought.membershipNumber();
            String surname = latest != null ? latest.surname() : brought.surname();
            candidates.add(new AcbTerminationReportDto.Candidate(memberId, number, surname, verdict.count(),
                    verdict.terminate(), twelveMonthTotal,
                    verdict.unpaidMonths().stream().map(YearMonth::toString).toList(), openingUnpaids));
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
        String blindness = opening.isEmpty() ? BLINDNESS
                : "Counted from the opening arrears position captured at switch-over (" + opening.size()
                        + " member(s)) and every month this system has sent since. A member not on it started at nought.";
        return new AcbTerminationReportDto(countingFrom, blindness,
                new AcbPage<>(content, pageNumber, pageSize, candidates.size(), totalPages));
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
        letter.setChannel(LetterChannel.EMAIL);
        letter.setAttempts(0);
        letter.setCreatedAt(now);

        LetterRecipient recipient = returns.findRecipient(line.getMemberId()).orElse(null);
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
                line.getBankName(), YearMonth.from(run.getActionMonth()), wording, line.getDebitDay());
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

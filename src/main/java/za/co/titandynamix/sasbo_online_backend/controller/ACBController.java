package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.acb.opening.OpeningPositionSheet;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBankCodeDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBanksDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCodesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDaysDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbOpeningPositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRatesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountsResultDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CalendarSettingRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeAddRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DeclineRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.HandledRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.HolidayRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.KnownUntilRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PermissionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RateRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.SuspendMemberRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidDecisionRequest;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbCodeService;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Debit Order Deductions -- the code's ACB -- under {@code /api/acb}.
 *
 * <h2>Who may do what (Stephne, 6 October 2026; the October build's permissions)</h2>
 * The actions that once refused every caller while the API could not tell who was calling are now each a permission
 * ({@code @PreAuthorize}), and a caller without it is answered by sign-in: 401 when nobody is signed in, 403
 * {@code PERMISSION_REQUIRED} for a user without it.
 * <ul>
 *   <li>{@code DOD_PREPARE} -- an administrator prepares the run and approves it, with no second approval (DOD-09,
 *       DOD-10); records the returns, decides and suspends (DOD-12); confirms an ending (DOD-22); captures counts
 *       (DOD-28);</li>
 *   <li>{@code DOD_FILE} -- the CFO only downloads the file for the bank, and records it went or was declined
 *       (DOD-11);</li>
 *   <li>{@code DOD_SETTINGS} -- the codes, the non-working days we keep (DOD-17, DOD-18), the rates, and moving a
 *       bank's members to a new day (DOD-19);</li>
 *   <li>{@code MEMBERS_EDIT} -- a member's return: reactivated (DOD-25) or rejoining (DOD-27).</li>
 * </ul>
 * No organiser position holds any of them, so organisers never see unpaids (DOD-14). The routes that answer today
 * without a permission keep doing so while sign-in is open (the owner: everything that works today keeps working);
 * once sign-in is required the filter chain asks every caller to sign in.
 *
 * <h2>Every list is paged</h2>
 * Runs, lines and exclusions are each a page with a total. The largest page is capped whatever the
 * caller asks for.
 */
@RestController
@RequestMapping("/api/acb")
@RequiredArgsConstructor
public class ACBController {

    /** Any of the module's own permissions: the readers of its new pages. */
    static final String READ = "hasAnyAuthority('DOD_PREPARE', 'DOD_FILE', 'DOD_SETTINGS')";

    private final AcbRunService runs;
    private final AcbReturnsService returns;
    private final AcbCalendarService calendar;
    private final AcbOpeningPositionService openingPosition;
    private final AcbRateService rates;
    private final AcbCodeService codes;
    private final AcbUnpaidCountService unpaidCounts;
    private final AcbDebitDayService debitDays;

    @GetMapping("/runs")
    public AcbPage<AcbRunSummaryDto> listRuns(@RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return runs.list(page, size);
    }

    /**
     * Starts preparing a month and answers {@code 202} at once, with the run in {@code PREPARING}.
     * The screen polls {@code GET /api/acb/runs/{id}} until it is {@code PREPARED} or
     * {@code FAILED}. A body is always returned: an empty 2xx is what once made a letter send twice.
     */
    @PostMapping("/runs")
    public ResponseEntity<AcbRunDto> prepare(@Valid @RequestBody PrepareRunRequest request) {
        return ResponseEntity.accepted().body(runs.prepare(request.actionMonth(), AcbActor.current()));
    }

    @GetMapping("/runs/{id}")
    public AcbRunDto getRun(@PathVariable("id") long id) {
        return runs.get(id);
    }

    @GetMapping("/runs/{id}/lines")
    public AcbPage<AcbRunLineDto> lines(@PathVariable("id") long id,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "50") int size) {
        return runs.lines(id, page, size);
    }

    @GetMapping("/runs/{id}/exclusions")
    public AcbPage<AcbExclusionDto> exclusions(@PathVariable("id") long id,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return runs.exclusions(id, page, size);
    }

    /**
     * Approves a prepared run -- by the administrator who prepares it, as often as not: there is no second approval
     * (DOD-10), the bank is the check on the file (D14 as amended, Q29).
     */
    @PostMapping("/runs/{id}/approve")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbRunDto approve(@PathVariable("id") long id, @Valid @RequestBody RunTransitionRequest request) {
        return runs.approve(id, request.version(), AcbActor.current(), request.reason());
    }

    /**
     * The file for the bank -- every debited member's account number -- for the CFO alone (DOD-11): made at the first
     * download and kept as the evidence of what was sent; every download counted and audited, so a second one is never
     * silent. {@code sasbosub.<mon>}, as the old system named it.
     */
    @GetMapping("/runs/{id}/file")
    @PreAuthorize("hasAuthority('DOD_FILE')")
    public ResponseEntity<byte[]> file(@PathVariable("id") long id) {
        AcbRunService.Download download = runs.download(id, AcbActor.current());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.file().fileName() + "\"")
                .header("X-File-Sha256", download.file().sha256())
                .header("X-File-Downloads", String.valueOf(download.downloads()))
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(download.file().bytes());
    }

    /** Records that the CFO uploaded the file at the bank -- the system submits nothing (Q1, DOD-10). */
    @PostMapping("/runs/{id}/submitted")
    @PreAuthorize("hasAuthority('DOD_FILE')")
    public AcbRunDto submitted(@PathVariable("id") long id, @Valid @RequestBody RunTransitionRequest request) {
        return runs.recordSubmitted(id, request.version(), AcbActor.current(), request.reason());
    }

    /** Records that the bank declined the file (D29): the month is released, to be prepared again (DOD-08). */
    @PostMapping("/runs/{id}/declined")
    @PreAuthorize("hasAuthority('DOD_FILE')")
    public AcbRunDto declined(@PathVariable("id") long id, @Valid @RequestBody DeclineRequest request) {
        return runs.recordDeclined(id, request.version(), AcbActor.current(), request.reason(), request.bankWords());
    }

    // ------------------------------------------------------------------------------------------
    // The returns loop -- one report per debit day (DOD-05, DOD-06), typed in until it can be imported
    // ------------------------------------------------------------------------------------------

    /**
     * Records one unpaid from the bank's returns report against a line of a sent run -- the code, the day it was noted,
     * and the decision if one is made; a re-debit only where the rules allow it (DOD-03, DOD-04).
     */
    @PostMapping("/runs/{id}/lines/{lineId}/unpaid")
    public AcbRunLineDto recordUnpaid(@PathVariable("id") long id, @PathVariable("lineId") long lineId,
                                      @Valid @RequestBody RecordUnpaidRequest request) {
        return returns.recordUnpaid(id, lineId, request, AcbActor.current());
    }

    /** The decision on an unpaid recorded without one: present it again next month, or suspend. */
    @PostMapping("/runs/{id}/lines/{lineId}/decision")
    public AcbRunLineDto decide(@PathVariable("id") long id, @PathVariable("lineId") long lineId,
                                @Valid @RequestBody UnpaidDecisionRequest request) {
        return returns.decide(id, lineId, request.decision(), AcbActor.current());
    }

    /** A run's debit days, each with how far its returns report has come back (DOD-06). */
    @GetMapping("/runs/{id}/debit-days")
    @PreAuthorize(READ)
    public AcbDebitDaysDto debitDays(@PathVariable("id") long id) {
        return returns.debitDays(id);
    }

    /**
     * One debit day's returns report, typed in: every unpaid on it at once, all or nothing, each problem named by entry
     * ({@code RETURNS_BATCH_INVALID}); every letter queued at once (DOD-07).
     */
    @PostMapping("/runs/{id}/returns")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbReturnsBatchDto recordReturnsReport(@PathVariable("id") long id,
                                                  @Valid @RequestBody ReturnsBatchRequest request) {
        return returns.recordReturnsBatch(id, request, AcbActor.current());
    }

    /** The returns reports typed in for a run, newest debit day first. */
    @GetMapping("/runs/{id}/returns/batches")
    @PreAuthorize(READ)
    public AcbPage<AcbReturnsBatchDto> returnsReports(@PathVariable("id") long id,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "50") int size) {
        return returns.returnsBatches(id, page, size);
    }

    /**
     * Records that the bank's returns for a submitted run are in: every debit not recorded as unpaid
     * is marked paid, since the bank's report lists only what came back. A late return can still be
     * recorded afterwards.
     */
    @PostMapping("/runs/{id}/returned")
    public AcbRunDto returned(@PathVariable("id") long id, @Valid @RequestBody RunTransitionRequest request) {
        return runs.recordReturns(id, request.version(), AcbActor.current(), request.reason());
    }

    /** Closes a month whose returns are in -- refused while any unpaid in it is undecided. */
    @PostMapping("/runs/{id}/close")
    public AcbRunDto close(@PathVariable("id") long id, @Valid @RequestBody RunTransitionRequest request) {
        return runs.close(id, request.version(), AcbActor.current(), request.reason());
    }

    /**
     * Records the member's permission for a further re-debit of an unpaid (D32) -- data entry, like the
     * decision it completes. {@code PERMISSION_NOTE_REQUIRED} without a note.
     */
    @PostMapping("/runs/{id}/lines/{lineId}/permission")
    public AcbRunLineDto permit(@PathVariable("id") long id, @PathVariable("lineId") long lineId,
                                @RequestBody PermissionRequest request) {
        return returns.permit(id, lineId, request.note(), AcbActor.current());
    }

    /** The re-debits a run holds over for want of the member's permission (D32), a page at a time. */
    @GetMapping("/runs/{id}/held-over")
    public AcbPage<AcbHeldOverDto> heldOver(@PathVariable("id") long id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return returns.heldOver(id, page, size);
    }

    /** The run's held letters -- the worklist Q46 asks for, a page at a time. */
    @GetMapping("/runs/{id}/letters/held")
    public AcbPage<AcbHeldLetterDto> heldLetters(@PathVariable("id") long id,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return returns.heldLetters(id, page, size);
    }

    /** Marks a held letter dealt with by hand, with a note of how -- data entry; it sends nothing (Q46). */
    @PostMapping("/runs/{id}/letters/{letterId}/handled")
    public AcbHeldLetterDto markHandled(@PathVariable("id") long id, @PathVariable("letterId") long letterId,
                                        @RequestBody HandledRequest request) {
        return returns.markHandled(id, letterId, request.note(), AcbActor.current());
    }

    /**
     * Sends a letter that could not go -- held until the member has an address, or a send that FAILED. Nothing reaches
     * a member while sending is switched off for the letters review.
     */
    @PostMapping("/runs/{id}/letters/{letterId}/send")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbHeldLetterDto sendLetter(@PathVariable("id") long id, @PathVariable("letterId") long letterId) {
        return returns.sendHeldLetter(id, letterId, AcbActor.current());
    }

    /**
     * The Debits screen's member search, server-side and paged (04.1, R-SEC-B, R-PERF-D) -- a POST, so an
     * identity number never travels in a URL. Answers masked accounts and no identity numbers.
     */
    @PostMapping("/members/search")
    public AcbPage<AcbMemberDto> searchMembers(@RequestBody MemberSearchRequest request) {
        return returns.searchMembers(request);
    }

    /** A run's unpaids, a page at a time -- all of them, or one reason's ("Invalid Accounts"). */
    @GetMapping("/runs/{id}/unpaids")
    public AcbPage<AcbRunLineDto> unpaids(@PathVariable("id") long id,
                                          @RequestParam(required = false) UnpaidReason reason,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "50") int size) {
        return returns.unpaids(id, reason, page, size);
    }

    /** What the bank sent back for a run, counted by outcome, by reason and by letter. */
    @GetMapping("/runs/{id}/returns")
    public AcbReturnsSummaryDto returnsSummary(@PathVariable("id") long id) {
        return returns.summary(id);
    }

    // ------------------------------------------------------------------------------------------
    // Suspensions, endings and a member's return
    // ------------------------------------------------------------------------------------------

    /**
     * Confirms the ending of a membership three unpaids have reached -- a staff member confirms each one (DOD-22) --
     * and queues the member's short letter (DOD-24).
     */
    @PostMapping("/termination-report/{memberId}/terminate")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbMemberStatusDto terminate(@PathVariable("memberId") int memberId) {
        return returns.terminate(memberId, AcbActor.current());
    }

    /**
     * Suspends the member of an unpaid decided SUSPEND, with SASBO's suspension code -- the one the bank's code records
     * on her sheet, unless the body names another (DOD-12).
     */
    @PostMapping("/runs/{id}/lines/{lineId}/suspend-member")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbMemberStatusDto suspendMember(@PathVariable("id") long id, @PathVariable("lineId") long lineId,
                                            @Valid @RequestBody(required = false) SuspendMemberRequest request) {
        return returns.suspendMember(id, lineId, request, AcbActor.current());
    }

    /** A suspended member made active again: only the current month is collected (DOD-25, DOD-26). */
    @PostMapping("/members/{memberId}/reactivate")
    @PreAuthorize("hasAuthority('MEMBERS_EDIT')")
    public AcbMemberStatusDto reactivate(@PathVariable("memberId") int memberId,
                                         @Valid @RequestBody(required = false) MemberReturnRequest request) {
        return returns.reactivate(memberId, request, AcbActor.current());
    }

    /** A member whose membership ended joins again, freely, with a new join date (DOD-27). */
    @PostMapping("/members/{memberId}/rejoin")
    @PreAuthorize("hasAuthority('MEMBERS_EDIT')")
    public AcbMemberStatusDto rejoin(@PathVariable("memberId") int memberId,
                                     @Valid @RequestBody(required = false) MemberReturnRequest request) {
        return returns.rejoin(memberId, request, AcbActor.current());
    }

    /**
     * 03.3 -- a specimen of one of the union's letters, as a PDF on the letterhead, for the letters review
     * SASBO asked for (question 32 c). Synthetic from the address to the account, so it reads openly, like the
     * holiday calendar: no member's letter is ever served here.
     */
    // No `produces` on the mapping: a template that is not one of the six must still answer the JSON
    // envelope (REQUEST_MALFORMED), and a PDF-only mapping would refuse to write it.
    @GetMapping("/letters/specimen/{template}")
    public ResponseEntity<byte[]> letterSpecimen(
            @PathVariable("template") za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate template) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"specimen-" + template.name().toLowerCase(java.util.Locale.ROOT) + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(returns.letterSpecimen(template));
    }

    /**
     * Who three unpaid presentations would end -- every unpaid counts, a re-debit included (question 27) -- and who is
     * at two, over the last thirteen months, a page at a time. A report: it ends nobody.
     */
    @GetMapping("/termination-report")
    public AcbTerminationReportDto terminationReport(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return returns.terminationReport(page, size);
    }

    // ------------------------------------------------------------------------------------------
    // Unpaid counts captured by hand (DOD-28) -- any month, not only at switch-over
    // ------------------------------------------------------------------------------------------

    /** The counts on record, a page at a time. */
    @GetMapping("/unpaid-counts")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbPage<AcbUnpaidCountDto> unpaidCounts(@RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return unpaidCounts.list(page, size);
    }

    /** Captures a list of counts, 0 to 3, all or nothing ({@code UNPAID_COUNTS_INVALID}, named by row). */
    @PutMapping("/unpaid-counts")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbUnpaidCountsResultDto captureUnpaidCounts(@Valid @RequestBody UnpaidCountsRequest request) {
        return unpaidCounts.capture(request, AcbActor.current());
    }

    // ------------------------------------------------------------------------------------------
    // Settings: the codes, the rates, the non-working days, moving a bank's members (DOD_SETTINGS)
    // ------------------------------------------------------------------------------------------

    /** The bank's return codes and SASBO's suspension codes, both ways round (her sheet, DOD-01). */
    @GetMapping("/codes")
    @PreAuthorize(READ)
    public AcbCodesDto codes() {
        return codes.codes();
    }

    /** Changes one of the bank's codes -- its re-debit rule, SASBO's code, its wording, provisional or settled. */
    @PutMapping("/codes/{bankCode}")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbBankCodeDto changeCode(@PathVariable("bankCode") String bankCode,
                                     @Valid @RequestBody CodeChangeRequest request) {
        return codes.change(bankCode, request, AcbActor.current());
    }

    /** Adds a bank code the table does not know yet. */
    @PostMapping("/codes")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public ResponseEntity<AcbBankCodeDto> addCode(@Valid @RequestBody CodeAddRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(codes.add(request, AcbActor.current()));
    }

    /** Every dated rate, and -- from December -- the January notice. Open, like every read. */
    @GetMapping("/rates")
    public AcbRatesDto rates() {
        return rates.rates();
    }

    /**
     * Next year's flat rate, ahead of its month (Q43) -- a debit order setting, and a change of subscription amount,
     * so either permission adds it.
     */
    @PostMapping("/rates")
    @PreAuthorize("hasAnyAuthority('DOD_SETTINGS', 'SUBSCRIPTIONS_MAINTAIN')")
    public AcbRatesDto addRate(@Valid @RequestBody RateRequest request) {
        return rates.add(request.effectiveFrom(), request.amount(), request.source(), AcbActor.current());
    }

    /** The opening position as it stands: how many members, who entered it, whether it can still change. */
    @GetMapping("/opening-position")
    public AcbOpeningPositionDto openingPosition() {
        return openingPosition.current();
    }

    /**
     * The opening-position sheet (D24), as the request's raw body -- read no further than a byte past its limit, so an
     * oversized upload is refused without being held in memory (SD3-B).
     */
    @PostMapping("/opening-position")
    @PreAuthorize("hasAuthority('DOD_PREPARE')")
    public AcbOpeningPositionDto captureOpeningPosition(HttpServletRequest request) {
        byte[] sheet;
        try (InputStream body = request.getInputStream()) {
            sheet = body.readNBytes(OpeningPositionSheet.MAX_BYTES + 1);
        } catch (IOException unreadable) {
            throw new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED,
                    "The sheet could not be read. Send the .xlsx file as the request's body.", Map.of());
        }
        return openingPosition.capture(sheet, AcbActor.current());
    }

    /**
     * The non-working days -- the calendar's name and the date it is known until -- and one year of them, this year's by
     * default: public holidays and the days the bank is closed (DOD-17). Public knowledge, so open.
     */
    @GetMapping("/holidays")
    public AcbCalendarDto holidays(@RequestParam(required = false) Integer year) {
        return calendar.calendar(year);
    }

    /** Adds a non-working day -- a declared public holiday, or a day the bank is closed. We keep the list (DOD-18). */
    @PostMapping("/holidays")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbHolidayDto addHoliday(@Valid @RequestBody HolidayRequest request) {
        return calendar.add(request.date(), request.name(), request.kind(), AcbActor.current());
    }

    /** Removes a day an administrator added; the statutory days are the Act's. */
    @DeleteMapping("/holidays/{date}")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbCalendarDto removeHoliday(@PathVariable("date") String date) {
        return calendar.remove(date, AcbActor.current());
    }

    /** Moves the date the calendar is known until -- later, never earlier. */
    @PostMapping("/calendar/known-until")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbCalendarDto extendCalendar(@Valid @RequestBody KnownUntilRequest request) {
        return calendar.extend(request.knownUntil(), AcbActor.current());
    }

    /** Whether a day the bank is closed moves the debits on it -- DOD-17's open choice, on by default. */
    @PutMapping("/calendar/settings")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbCalendarDto calendarSettings(@Valid @RequestBody CalendarSettingRequest request) {
        return calendar.setting(request.bankClosuresCount(), AcbActor.current());
    }

    /** The banks, each with its debit-order members counted by day -- what a move chooses from (DOD-19). */
    @GetMapping("/banks")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbBanksDto banks() {
        return debitDays.banks();
    }

    /** What moving a bank's members to {@code day} would do: nothing changes. */
    @GetMapping("/banks/{bankId}/debit-day-move")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbDebitDayMoveDto previewDebitDayMove(@PathVariable("bankId") int bankId,
                                                  @RequestParam("day") Integer day) {
        return debitDays.preview(bankId, day);
    }

    /** Moves every debit-order member of a bank to a new debit day, in one step (DOD-19). */
    @PostMapping("/banks/{bankId}/debit-day-move")
    @PreAuthorize("hasAuthority('DOD_SETTINGS')")
    public AcbDebitDayMoveDto moveDebitDay(@PathVariable("bankId") int bankId,
                                           @Valid @RequestBody DebitDayMoveRequest request) {
        return debitDays.move(bankId, request, AcbActor.current());
    }

    @PostMapping("/runs/{id}/cancel")
    public AcbRunDto cancel(@PathVariable("id") long id,
                            @Valid @RequestBody RunTransitionRequest request) {
        return runs.cancel(id, request.version(), AcbActor.current(), request.reason());
    }
}

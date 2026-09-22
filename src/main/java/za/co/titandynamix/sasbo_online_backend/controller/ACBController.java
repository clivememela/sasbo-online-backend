package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbOpeningPositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRatesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.HandledRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PermissionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidDecisionRequest;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

import java.util.Map;

/**
 * The debit-order run, under {@code /api/acb/runs}.
 *
 * <h2>What ships while the API is anonymous, and what refuses</h2>
 * Every route here answers anyone who can reach the API (register B1). So the routes that only
 * READ, or that PROPOSE -- preparing a run is the dry run, and nothing leaves the building until a
 * run is approved -- are open, like the rest of the API. Approving a run authorises the union to
 * debit thousands of members, so it is built and proven in the service and REFUSED here, to every
 * caller, with {@code IDENTITY_REQUIRED}, until the API knows who is calling -- and so are the
 * bureau file, which carries every debited member's account number, and recording its submission.
 * Cancelling is the undo for a proposal and moves no money, so it is open. The holiday calendar is
 * public knowledge and reads openly; changing it moves every debit in a month, so its writes refuse
 * like approval does (D13, D30) -- until then a day is added by migration.
 *
 * <h2>Every list is paged</h2>
 * Runs, lines and exclusions are each a page with a total. The largest page is capped whatever the
 * caller asks for.
 */
@RestController
@RequestMapping("/api/acb")
@RequiredArgsConstructor
public class ACBController {

    private final AcbRunService runs;
    private final AcbReturnsService returns;
    private final AcbCalendarService calendar;
    private final AcbOpeningPositionService openingPosition;
    private final AcbRateService rates;

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
     * 🔒 Refused to every caller, before anything is read: the run is not even looked up, so the
     * refusal says nothing about which runs exist. The day the API authenticates, this calls
     * {@code runs.approve} with the signed-in administrator -- who may be the one who prepared it:
     * the bank, not a second person here, checks the file (D14 as amended, Q29).
     */
    @PostMapping("/runs/{id}/approve")
    public AcbRunDto approve(@PathVariable("id") long id) {
        throw identityRequired(id, "Approving a run authorises the union to debit its members, so it "
                + "waits until administrators sign in to this system. Nothing has been approved.");
    }

    /**
     * 🔒 The run's bureau file -- every debited member's account number -- refused to every caller
     * until the API knows who is asking. Built and proven in {@code AcbFileService}; the day the
     * API authenticates, this serves its bytes as {@code sasbosub.<mon>}.
     */
    @GetMapping("/runs/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable("id") long id) {
        throw identityRequired(id, "A bureau file carries every debited member's account number, so it "
                + "is served only once administrators sign in to this system.");
    }

    /**
     * 🔒 Recording that the file went to the bank moves the run past its last chance of being
     * cancelled -- refused to every caller until the API knows who is calling.
     */
    @PostMapping("/runs/{id}/submitted")
    public AcbRunDto submitted(@PathVariable("id") long id) {
        throw identityRequired(id, "Recording a submission to the bank waits until administrators sign "
                + "in to this system. Nothing has been recorded.");
    }

    /**
     * 🔒 Recording that the bank declined a run's file releases its month for a new file -- refused to
     * every caller until the API knows who is calling, like recording its submission (D13, D29).
     * Built and proven in {@code AcbRunService.recordDeclined}.
     */
    @PostMapping("/runs/{id}/declined")
    public AcbRunDto declined(@PathVariable("id") long id) {
        throw identityRequired(id, "Recording that the bank declined a file waits until administrators sign "
                + "in to this system. Nothing has been recorded.");
    }

    // ------------------------------------------------------------------------------------------
    // The returns loop -- recorded by hand from the bank's report until the report can be imported
    // ------------------------------------------------------------------------------------------

    /**
     * Records one unpaid from the bank's returns report against a line of a sent run -- the code,
     * the day it was noted, and the decision if one is made. Data entry, like capturing a member:
     * the letter it queues is rendered, never sent, while the API is anonymous.
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

    /**
     * Records that the bank's returns for a submitted run are in: every debit not recorded as unpaid
     * is marked paid, since the bank's report lists only what came back. Data entry, like recording
     * an unpaid -- it moves no money and sends nothing -- so it is open. A late return can still be
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
     * 🔒 Sending a held letter once the member has an address -- mail to a member, so refused to every
     * caller until the API knows who is calling (D13, B1).
     */
    @PostMapping("/runs/{id}/letters/{letterId}/send")
    public AcbHeldLetterDto sendLetter(@PathVariable("id") long id) {
        throw identityRequired(id, "Sending a letter to a member waits until administrators sign in to this "
                + "system. Nothing has been sent.");
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

    /**
     * Who three unpaid presentations would terminate -- every unpaid counts, a re-debit included
     * (question 27) -- and who is at two, over the last thirteen months, a page at a time. A report:
     * it terminates nobody.
     */
    /**
     * 🔒 03.5 -- terminating a member the rule says three unpaids have reached (SUBS_IN_ARREARS) ends a membership,
     * so it is refused to every caller until the API knows who is calling (B1), like approval. Built and proven in
     * {@code AcbReturnsService.terminate}.
     */
    @PostMapping("/termination-report/{memberId}/terminate")
    public za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto terminate(
            @PathVariable("memberId") int memberId) {
        throw new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, "Terminating a membership waits until administrators "
                + "sign in to this system. Nobody has been terminated.", Map.of("memberId", String.valueOf(memberId)));
    }

    /**
     * 🔒 03.5 -- suspending the member of an unpaid decided SUSPEND changes a member's status, so it is refused to
     * every caller until B1, like terminating. Built and proven in {@code AcbReturnsService.suspendMember}.
     */
    @PostMapping("/runs/{id}/lines/{lineId}/suspend-member")
    public za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto suspendMember(
            @PathVariable("id") long id, @PathVariable("lineId") long lineId) {
        throw new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, "Suspending a member waits until administrators "
                + "sign in to this system. The member's status is unchanged.",
                Map.of("runId", String.valueOf(id), "lineId", String.valueOf(lineId)));
    }

    @GetMapping("/termination-report")
    public AcbTerminationReportDto terminationReport(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "50") int size) {
        return returns.terminationReport(page, size);
    }

    // ------------------------------------------------------------------------------------------
    // The flat rate, dated -- captured in December for January (Q43)
    // ------------------------------------------------------------------------------------------

    /** Every dated rate, and -- from December -- the January notice. Open, like every read. */
    @GetMapping("/rates")
    public AcbRatesDto rates() {
        return rates.rates();
    }

    /**
     * 🔒 Adding a rate sets what thousands of members are charged -- refused to every caller until the
     * API knows who is calling (D13). Built and proven in {@code AcbRateService.add}.
     */
    @PostMapping("/rates")
    public AcbRatesDto addRate() {
        throw new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, "Adding a subscription rate waits until "
                + "administrators sign in to this system. Nothing has been added.");
    }

    // ------------------------------------------------------------------------------------------
    // The opening arrears position -- captured by hand once, at switch-over (D24)
    // ------------------------------------------------------------------------------------------

    /** The opening position as it stands: how many members, who entered it, whether it can still change. */
    @GetMapping("/opening-position")
    public AcbOpeningPositionDto openingPosition() {
        return openingPosition.current();
    }

    /**
     * 🔒 The upload of the opening-position sheet -- refused to every caller, before a byte of it is
     * read, until the API knows who is calling (SD3-B): an .xlsx is a zip of XML, and this would be an
     * anonymous endpoint parsing one from the internet, for an upload used once. Built and proven in
     * {@code AcbOpeningPositionService.capture}.
     */
    @PostMapping("/opening-position")
    public AcbOpeningPositionDto captureOpeningPosition() {
        throw new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, "The opening arrears position is captured once "
                + "administrators sign in to this system. Nothing has been read or stored.");
    }

    // ------------------------------------------------------------------------------------------
    // The holiday calendar -- data, because the bank declines a file with a holiday in it (D30)
    // ------------------------------------------------------------------------------------------

    /**
     * The calendar -- its name and the date it is known until -- and one year's holidays, this year's
     * by default. Public holidays are public, so this is open.
     */
    @GetMapping("/holidays")
    public AcbCalendarDto holidays(@RequestParam(required = false) Integer year) {
        return calendar.calendar(year);
    }

    /**
     * 🔒 Adding a day moves every debit on it -- refused to every caller until the API knows who is
     * calling. Built and proven in {@code AcbCalendarService}; until then a day is added by migration.
     */
    @PostMapping("/holidays")
    public AcbHolidayDto addHoliday() {
        throw calendarChangeRefused("Adding a public holiday moves the debits on it, so it waits until "
                + "administrators sign in to this system. Nothing has been added.");
    }

    /** 🔒 Removing a day an administrator added -- refused to every caller until B1, like adding one. */
    @DeleteMapping("/holidays/{date}")
    public AcbCalendarDto removeHoliday() {
        throw calendarChangeRefused("Removing a public holiday moves the debits around it, so it waits "
                + "until administrators sign in to this system. Nothing has been removed.");
    }

    /** 🔒 Moving the date the calendar is known until -- refused to every caller until B1. */
    @PostMapping("/calendar/known-until")
    public AcbCalendarDto extendCalendar() {
        throw calendarChangeRefused("Extending the holiday calendar decides which months can be "
                + "prepared, so it waits until administrators sign in to this system. Nothing has changed.");
    }

    /** Refused before anything is read: the answer says nothing about which runs exist. */
    private static AcbRefusal identityRequired(long runId, String message) {
        return new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, message,
                Map.of("runId", String.valueOf(runId)));
    }

    /** Refused before anything is read or parsed. */
    private static AcbRefusal calendarChangeRefused(String message) {
        return new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, message);
    }

    @PostMapping("/runs/{id}/cancel")
    public AcbRunDto cancel(@PathVariable("id") long id,
                            @Valid @RequestBody RunTransitionRequest request) {
        return runs.cancel(id, request.version(), AcbActor.current(), request.reason());
    }
}

package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidDecisionRequest;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
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
 * Cancelling is the undo for a proposal and moves no money, so it is open.
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
     * {@code runs.approve} with the signed-in administrator -- whom the service will refuse if they
     * prepared the run themselves.
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
     * Who each reading of "three unsuccessful retries" would terminate -- a report, both readings
     * side by side until SASBO says which is theirs (question 27). It terminates nobody.
     */
    @GetMapping("/termination-report")
    public AcbTerminationReportDto terminationReport() {
        return returns.terminationReport();
    }

    /** Refused before anything is read: the answer says nothing about which runs exist. */
    private static AcbRefusal identityRequired(long runId, String message) {
        return new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED, message,
                Map.of("runId", String.valueOf(runId)));
    }

    @PostMapping("/runs/{id}/cancel")
    public AcbRunDto cancel(@PathVariable("id") long id,
                            @Valid @RequestBody RunTransitionRequest request) {
        return runs.cancel(id, request.version(), AcbActor.current(), request.reason());
    }
}

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
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
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
 * caller, with {@code IDENTITY_REQUIRED}, until the API knows who is calling. Cancelling is the undo
 * for a proposal and moves no money, so it is open.
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
        throw new AcbRefusal(AcbRefusalCode.IDENTITY_REQUIRED,
                "Approving a run authorises the union to debit its members, so it waits until "
                        + "administrators sign in to this system. Nothing has been approved.",
                Map.of("runId", String.valueOf(id)));
    }

    @PostMapping("/runs/{id}/cancel")
    public AcbRunDto cancel(@PathVariable("id") long id,
                            @Valid @RequestBody RunTransitionRequest request) {
        return runs.cancel(id, request.version(), AcbActor.current(), request.reason());
    }
}

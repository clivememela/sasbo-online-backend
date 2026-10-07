package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.http.HttpStatus;
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
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftRequest;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftSubmitRequest;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceVisitDraftService;

/**
 * The visit forms, saving and submitting (October build, 5 Oct Show &amp; Tell): a person's own visit drafts -- saved,
 * saved again, submitted. Every route needs {@code VISITS_CAPTURE}; a draft is only ever its owner's.
 */
@MonthEndApi
@RestController
@RequestMapping("/api/workplaces/visit-drafts")
public class VisitDraftController {

    private final WorkplaceVisitDraftService drafts;

    public VisitDraftController(WorkplaceVisitDraftService drafts) {
        this.drafts = drafts;
    }

    /** A page of my drafts -- {@code ?status=DRAFT} (where none is named) or {@code SUBMITTED} -- newest saved first. */
    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @GetMapping
    public VisitDraftPageDto mine(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        return drafts.mine(status, page, size);
    }

    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @GetMapping("/{id}")
    public VisitDraftDto one(@PathVariable Long id) {
        return drafts.one(id);
    }

    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @PostMapping
    public ResponseEntity<VisitDraftDto> create(@RequestBody VisitDraftRequest draft) {
        return ResponseEntity.status(HttpStatus.CREATED).body(drafts.create(draft));
    }

    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @PutMapping("/{id}")
    public VisitDraftDto update(@PathVariable Long id, @RequestBody VisitDraftRequest draft) {
        return drafts.update(id, draft);
    }

    /** Discards the draft -- {@code ?version=}, the version the person saw. A submitted visit is never discarded. */
    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> discard(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        drafts.discard(id, version);
        return ResponseEntity.noContent().build();
    }

    /** Submits the draft: the visit goes on record and the draft becomes read-only. */
    @PreAuthorize("hasAuthority('VISITS_CAPTURE')")
    @PostMapping("/{id}/submit")
    public VisitDraftDto submit(@PathVariable Long id, @RequestBody VisitDraftSubmitRequest submission) {
        return drafts.submit(id, submission);
    }
}

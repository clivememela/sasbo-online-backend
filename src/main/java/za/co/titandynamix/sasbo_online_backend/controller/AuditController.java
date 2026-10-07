package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.SignInApi;
import za.co.titandynamix.sasbo_online_backend.dto.signin.AuditEventDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
import za.co.titandynamix.sasbo_online_backend.services.AuditTrail;

/**
 * The record of who did what, under {@code /api/audit} -- {@code AUDIT_VIEW} (signin.md; the 5 Oct ask, "a record of who
 * did what"). A page at a time, newest first, at most 100 lines a page; {@code from} and {@code to} are days in SASBO's
 * zone ({@code 2026-10-07}), both included.
 */
@SignInApi
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditTrail audit;

    public AuditController(AuditTrail audit) {
        this.audit = audit;
    }

    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    @GetMapping
    public SignInPage<AuditEventDto> search(@RequestParam(required = false) String entityType,
                                            @RequestParam(required = false) String entityId,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) Integer page,
                                            @RequestParam(required = false) Integer size) {
        return audit.search(entityType, entityId, username, from, to, page, size);
    }
}

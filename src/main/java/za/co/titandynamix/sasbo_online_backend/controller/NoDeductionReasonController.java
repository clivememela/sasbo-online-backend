package za.co.titandynamix.sasbo_online_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonsDto;
import za.co.titandynamix.sasbo_online_backend.services.NoDeductionReasons;

/**
 * The reasons for no payroll deduction (Stephne, MEN-17), under {@code /api/reference/no-deduction-reasons}: read by
 * anyone, as every reference list is -- words and SASBO's codes, and nobody -- and kept by an administrator who
 * maintains SASBO's lists. A controller of the arc, so an unreadable request is {@code REQUEST_MALFORMED} (MD49).
 */
@MonthEndApi
@RestController
@RequestMapping("/api/reference/no-deduction-reasons")
public class NoDeductionReasonController {

    private final NoDeductionReasons reasons;

    public NoDeductionReasonController(NoDeductionReasons reasons) {
        this.reasons = reasons;
    }

    @GetMapping
    public NoDeductionReasonsDto list() {
        return reasons.list();
    }

    @PreAuthorize("hasAuthority('REFERENCE_MAINTAIN')")
    @PostMapping
    public ResponseEntity<NoDeductionReasonDto> create(@RequestBody NoDeductionReasonRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reasons.create(request));
    }

    @PreAuthorize("hasAuthority('REFERENCE_MAINTAIN')")
    @PutMapping("/{id}")
    public NoDeductionReasonDto update(@PathVariable("id") Long id, @RequestBody NoDeductionReasonRequest request) {
        return reasons.update(id, request);
    }
}

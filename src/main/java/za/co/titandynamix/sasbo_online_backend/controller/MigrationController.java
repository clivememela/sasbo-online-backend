package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.MigrationApi;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationBatchPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationJobRequests;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMappingPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationMonthTotalsDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReconciliationDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.migration.MigrationRowPageDto;
import za.co.titandynamix.sasbo_online_backend.services.MigrationBatches;
import za.co.titandynamix.sasbo_online_backend.services.MigrationCommits;
import za.co.titandynamix.sasbo_online_backend.services.MigrationMappings;
import za.co.titandynamix.sasbo_online_backend.services.MigrationReconciliations;
import za.co.titandynamix.sasbo_online_backend.services.MigrationValidation;

import java.io.InputStream;

/**
 * The data migration's routes -- every one behind {@code MIGRATION_RUN} (the October build's permission catalogue:
 * "run legacy data imports and their checks"): a file holds every member's identity number, and a move writes the
 * register. No screen is built yet; these are what one would call, and what the build runs a rehearsal with.
 *
 * <p>A file is sent as the request's body, raw, with its name, its mapping and the date it represents as parameters: a
 * multipart upload would be held whole by the container first, and an export may be hundreds of megabytes. Its content
 * type is not trusted, any more than its name: the mapping says what the file should be, and its bytes are checked
 * against that. A mapping's definition is the body too, read by the migration's own strict parser. No body is read until
 * the permission has been checked.
 */
@RestController
@MigrationApi
@RequestMapping("/api/migration")
public class MigrationController {

    private final MigrationBatches batches;
    private final MigrationValidation validation;
    private final MigrationCommits commits;
    private final MigrationMappings mappings;
    private final MigrationReconciliations reconciliations;

    public MigrationController(MigrationBatches batches, MigrationValidation validation, MigrationCommits commits,
                               MigrationMappings mappings, MigrationReconciliations reconciliations) {
        this.batches = batches;
        this.validation = validation;
        this.commits = commits;
        this.mappings = mappings;
        this.reconciliations = reconciliations;
    }

    // ------------------------------------------------------------------------------------------------ mappings

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/mappings")
    public MigrationMappingPageDto mappings(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return mappings.page(page, size);
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/mappings/{key}")
    public MigrationMappingDto mapping(@PathVariable String key) {
        return mappings.detail(key);
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PostMapping("/mappings")
    public ResponseEntity<MigrationMappingDto> defineMapping(InputStream body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mappings.define(body));
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PutMapping("/mappings/{key}")
    public MigrationMappingDto changeMapping(@PathVariable String key, @RequestParam int version, InputStream body) {
        return mappings.update(key, version, body);
    }

    // ------------------------------------------------------------------------------------------------ batches

    /** 201 for a file registered now; 200, {@code alreadyRegistered}, for a file that already was -- nothing read twice. */
    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PostMapping("/batches")
    public ResponseEntity<MigrationBatchDto> register(@RequestParam String fileName, @RequestParam String mapping,
                                                      @RequestParam String asAt,
                                                      @RequestParam(required = false) String sha256,
                                                      InputStream body) {
        MigrationBatches.Registered registered = batches.register(fileName, mapping, asAt, sha256, body);
        return ResponseEntity.status(registered.created() ? HttpStatus.CREATED : HttpStatus.OK).body(registered.batch());
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/batches")
    public MigrationBatchPageDto batches(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return batches.page(page, size);
    }

    /** A batch as a screen polls it while its job runs. */
    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/batches/{id}")
    public MigrationBatchDto batch(@PathVariable long id) {
        return batches.find(id);
    }

    /** 202: the check runs as a job; poll the batch. */
    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PostMapping("/batches/{id}/validate")
    public ResponseEntity<MigrationBatchDto> validate(@PathVariable long id,
                                                      @Valid @RequestBody(required = false) MigrationJobRequests.Validate request) {
        return ResponseEntity.accepted().body(validation.validate(id, request == null ? null : request.mappingKey()));
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/batches/{id}/report")
    public MigrationReportDto report(@PathVariable long id) {
        return batches.report(id);
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/batches/{id}/rows")
    public MigrationRowPageDto rows(@PathVariable long id, @RequestParam(required = false) String outcome,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size) {
        return batches.rows(id, outcome, code, page, size);
    }

    /** 202: the move runs as a job; poll the batch. */
    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PostMapping("/batches/{id}/commit")
    public ResponseEntity<MigrationBatchDto> commit(@PathVariable long id,
                                                    @RequestBody(required = false) MigrationJobRequests.Commit request) {
        return ResponseEntity.accepted().body(commits.commit(id, request != null && request.holdBackErrors()));
    }

    /** 202: the undo runs as a job; poll the batch. */
    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PostMapping("/batches/{id}/rollback")
    public ResponseEntity<MigrationBatchDto> rollback(@PathVariable long id,
                                                      @Valid @RequestBody MigrationJobRequests.Rollback request) {
        return ResponseEntity.accepted().body(commits.rollback(id, request.reason()));
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/batches/{id}/reconciliation")
    public MigrationReconciliationDto reconciliation(@PathVariable long id) {
        return reconciliations.reconcile(id);
    }

    // ------------------------------------------------------------------------------------------------ month-end totals

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @GetMapping("/months/{month}/totals")
    public MigrationMonthTotalsDto totals(@PathVariable String month) {
        return reconciliations.totals(month);
    }

    @PreAuthorize("hasAuthority('MIGRATION_RUN')")
    @PutMapping("/months/{month}/totals")
    public MigrationMonthTotalsDto enterTotals(@PathVariable String month,
                                               @Valid @RequestBody MigrationMonthTotalsDto.Request request) {
        return reconciliations.replace(month, request);
    }
}

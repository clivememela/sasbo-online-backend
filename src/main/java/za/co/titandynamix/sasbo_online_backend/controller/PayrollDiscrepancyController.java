package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.config.PayrollApi;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollNoteRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDownloadRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollResolveRequest;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollAccess;
import za.co.titandynamix.sasbo_online_backend.services.PayrollDiscrepancies;
import za.co.titandynamix.sasbo_online_backend.services.PayrollQueries;

import java.util.List;

/**
 * Payroll discrepancies and the queries sent to employers, under {@code /api/payroll}: every discrepancy across every
 * file -- the one place (PAY-32) -- its notes, its resolution (it stays open until resolved, PAY-20), and the lists of
 * queries raised with the employer (PAY-21). The same permissions as {@link PayrollController}.
 */
@PayrollApi
@RestController
@RequestMapping("/api/payroll")
public class PayrollDiscrepancyController {

    private final PayrollDiscrepancies discrepancies;
    private final PayrollQueries queries;

    public PayrollDiscrepancyController(PayrollDiscrepancies discrepancies, PayrollQueries queries) {
        this.discrepancies = discrepancies;
        this.queries = queries;
    }

    /** A file's discrepancies, every status unless one is asked for. */
    @PreAuthorize(PayrollController.READ)
    @GetMapping("/files/{id}/discrepancies")
    public PayrollPage<PayrollDiscrepancyDto> ofFile(@PathVariable("id") long id,
                                                     @RequestParam(value = "type", required = false) String type,
                                                     @RequestParam(value = "status", required = false) String status,
                                                     @RequestParam(value = "page", required = false) Integer page,
                                                     @RequestParam(value = "size", required = false) Integer size) {
        PayrollAccess.headOffice();
        return discrepancies.ofFile(id, type, status, page, size);
    }

    /** A query raised for the file: every open discrepancy that asks the employer to add, delete or correct. */
    @PreAuthorize(PayrollController.WRITE)
    @PostMapping("/files/{id}/queries")
    public ResponseEntity<PayrollQueryDto> raise(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        return ResponseEntity.status(HttpStatus.CREATED).body(queries.raise(id, AcbActor.current()));
    }

    /** A file's queries -- a few a file at most, newest first. */
    @PreAuthorize(PayrollController.READ)
    @GetMapping("/files/{id}/queries")
    public List<PayrollQueryDto> queriesOf(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        return queries.ofFile(id);
    }

    @PreAuthorize(PayrollController.READ)
    @GetMapping("/queries/{id}")
    public PayrollQueryDto query(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        return queries.get(id);
    }

    /** The employer's list, protected by the password the administrator chooses -- it leaves SASBO (POPIA). */
    @PreAuthorize(PayrollController.WRITE)
    @PostMapping("/queries/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("id") long id,
                                           @Valid @RequestBody PayrollQueryDownloadRequest request) {
        PayrollAccess.headOffice();
        return PayrollController.download(queries.download(id, request.format(), request.password(),
                AcbActor.current()));
    }

    /** Every discrepancy across every file, filtered; by default the ones still to be resolved. */
    @PreAuthorize(PayrollController.READ)
    @GetMapping("/discrepancies")
    public PayrollPage<PayrollDiscrepancyDto> list(@RequestParam(value = "employerId", required = false) Long employerId,
                                                   @RequestParam(value = "period", required = false) String period,
                                                   @RequestParam(value = "fileId", required = false) Long fileId,
                                                   @RequestParam(value = "type", required = false) String type,
                                                   @RequestParam(value = "status", required = false) String status,
                                                   @RequestParam(value = "page", required = false) Integer page,
                                                   @RequestParam(value = "size", required = false) Integer size) {
        PayrollAccess.headOffice();
        return discrepancies.list(employerId, period, fileId, type, status, page, size);
    }

    @PreAuthorize(PayrollController.READ)
    @GetMapping("/discrepancies/{id}")
    public PayrollDiscrepancyDto discrepancy(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        return discrepancies.get(id);
    }

    @PreAuthorize(PayrollController.WRITE)
    @PostMapping("/discrepancies/{id}/notes")
    public ResponseEntity<PayrollDiscrepancyDto> addNote(@PathVariable("id") long id,
                                                         @Valid @RequestBody PayrollNoteRequest request) {
        PayrollAccess.headOffice();
        return ResponseEntity.status(HttpStatus.CREATED).body(discrepancies.addNote(id, request.text(),
                AcbActor.current()));
    }

    /** Resolved with an outcome that fits its kind and a note of what was done, on the version the screen read. */
    @PreAuthorize(PayrollController.WRITE)
    @PostMapping("/discrepancies/{id}/resolve")
    public PayrollDiscrepancyDto resolve(@PathVariable("id") long id,
                                         @Valid @RequestBody PayrollResolveRequest request) {
        PayrollAccess.headOffice();
        return discrepancies.resolve(id, request.outcome(), request.note(), request.version(), AcbActor.current());
    }
}

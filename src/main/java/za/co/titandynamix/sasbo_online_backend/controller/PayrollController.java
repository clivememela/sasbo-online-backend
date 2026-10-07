package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.co.titandynamix.sasbo_online_backend.acb.AcbActor;
import za.co.titandynamix.sasbo_online_backend.config.PayrollApi;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollReconcileRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollRowDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollSettingsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollVocabularyDto;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollAccess;
import za.co.titandynamix.sasbo_online_backend.services.PayrollEmployers;
import za.co.titandynamix.sasbo_online_backend.services.PayrollFiles;
import za.co.titandynamix.sasbo_online_backend.services.PayrollPayments;
import za.co.titandynamix.sasbo_online_backend.services.PayrollReports;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Payroll Deductions and Reconciliation, under {@code /api/payroll}: the employers and their files, loading and
 * comparing them, their lines and reports, and the money paid in (Stephne's PAY answers, 6 Oct; the contract is
 * {@code contracts/payroll-api.md}).
 *
 * <p>🔒 Every route needs a payroll permission: reading {@code PAYROLL_VIEW} or {@code PAYROLL_PROCESS}, writing
 * {@code PAYROLL_PROCESS} -- head-office administrators (PAY-32); no organiser holds either (PAY-33). And every route is
 * head office's alone ({@code HEAD_OFFICE_ONLY}): an employer's file covers every region.
 */
@PayrollApi
@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    static final String READ = "hasAnyAuthority('PAYROLL_VIEW', 'PAYROLL_PROCESS')";
    static final String WRITE = "hasAuthority('PAYROLL_PROCESS')";

    private final PayrollEmployers employers;
    private final PayrollFiles files;
    private final PayrollPayments payments;
    private final PayrollReports reports;

    public PayrollController(PayrollEmployers employers, PayrollFiles files, PayrollPayments payments,
                             PayrollReports reports) {
        this.employers = employers;
        this.files = files;
        this.payments = payments;
        this.reports = reports;
    }

    /** The module's words: kinds of discrepancy, outcomes, statuses, limits. */
    @PreAuthorize(READ)
    @GetMapping("/vocabulary")
    public PayrollVocabularyDto vocabulary() {
        PayrollAccess.headOffice();
        return employers.vocabulary();
    }

    /** The sixteen employers that send a file (PAY-01) -- bounded by nature, in Stephne's order. */
    @PreAuthorize(READ)
    @GetMapping("/employers")
    public List<PayrollEmployerDto> employers() {
        PayrollAccess.headOffice();
        return employers.list();
    }

    @PreAuthorize(WRITE)
    @PutMapping("/employers/{id}")
    public PayrollEmployerDto updateEmployer(@PathVariable("id") long id,
                                             @Valid @RequestBody PayrollSettingsRequest request) {
        PayrollAccess.headOffice();
        return employers.update(id, request);
    }

    @PreAuthorize(READ)
    @GetMapping("/files")
    public PayrollPage<PayrollFileDto> files(@RequestParam(value = "employerId", required = false) Long employerId,
                                             @RequestParam(value = "period", required = false) String period,
                                             @RequestParam(value = "status", required = false) String status,
                                             @RequestParam(value = "page", required = false) Integer page,
                                             @RequestParam(value = "size", required = false) Integer size) {
        PayrollAccess.headOffice();
        return files.list(employerId, period, status, page, size);
    }

    /**
     * An employer's file loaded and compared: {@code 201} where this call loaded it, {@code 200} with
     * {@code alreadyLoaded} where these exact bytes were loaded already for the employer and month.
     */
    @PreAuthorize(WRITE)
    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PayrollFileSummaryDto> upload(@RequestPart("file") MultipartFile file,
                                                        @RequestParam("employerId") Long employerId,
                                                        @RequestParam("period") String period,
                                                        @RequestParam(value = "replace", defaultValue = "false")
                                                        boolean replace,
                                                        @RequestParam(value = "columnMapping", required = false)
                                                        String columnMapping) throws IOException {
        PayrollAccess.headOffice();
        PayrollFiles.Loaded loaded = files.upload(employerId, period, file.getOriginalFilename(),
                file.isEmpty() ? null : file.getBytes(), replace, columnMapping, AcbActor.current());
        return ResponseEntity.status(loaded.created() ? HttpStatus.CREATED : HttpStatus.OK).body(loaded.summary());
    }

    /** Rows typed in from a file the system cannot read -- GBS's PDF, SA Reserve Bank's picture (PAY-03). */
    @PreAuthorize(WRITE)
    @PostMapping("/files/typed")
    public ResponseEntity<PayrollFileSummaryDto> typed(@Valid @RequestBody PayrollTypedEntryRequest request) {
        PayrollAccess.headOffice();
        PayrollFiles.Loaded loaded = files.typed(request, AcbActor.current());
        return ResponseEntity.status(loaded.created() ? HttpStatus.CREATED : HttpStatus.OK).body(loaded.summary());
    }

    @PreAuthorize(READ)
    @GetMapping("/files/{id}")
    public PayrollFileSummaryDto file(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        return files.summary(id);
    }

    @PreAuthorize(READ)
    @GetMapping("/files/{id}/rows")
    public PayrollPage<PayrollRowDto> rows(@PathVariable("id") long id,
                                           @RequestParam(value = "show", required = false) String show,
                                           @RequestParam(value = "page", required = false) Integer page,
                                           @RequestParam(value = "size", required = false) Integer size) {
        PayrollAccess.headOffice();
        return files.rows(id, show, page, size);
    }

    /**
     * The file as it arrived -- the one place administrators reach every employer's file (PAY-32). Its lines are
     * unmasked, so only a holder of PAYROLL_PROCESS downloads it, and each download is recorded.
     */
    @PreAuthorize(WRITE)
    @GetMapping("/files/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable("id") long id) {
        PayrollAccess.headOffice();
        PayrollFiles.Content content = files.content(id, AcbActor.current());
        MediaType type = switch (content.format()) {
            case XLSX -> MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case XLS -> MediaType.parseMediaType("application/vnd.ms-excel");
            case CSV -> MediaType.parseMediaType("text/csv");
        };
        return download(content.fileName(), type, content.bytes());
    }

    @PreAuthorize(WRITE)
    @PostMapping("/files/{id}/reconcile")
    public PayrollFileSummaryDto reconcile(@PathVariable("id") long id,
                                           @RequestBody(required = false) PayrollReconcileRequest request) {
        PayrollAccess.headOffice();
        return files.reconcile(id, request, AcbActor.current());
    }

    /** The discrepancies list, as a spreadsheet or a PDF (PAY-35, PAY-37). */
    @PreAuthorize(READ)
    @GetMapping("/files/{id}/reports/discrepancies")
    public ResponseEntity<byte[]> discrepanciesReport(@PathVariable("id") long id,
                                                      @RequestParam(value = "format", required = false) String format) {
        PayrollAccess.headOffice();
        return download(reports.discrepancies(id, format, AcbActor.current()));
    }

    /** The full report -- matched, not matched, missing and totals -- as a spreadsheet or a PDF (PAY-35, PAY-37). */
    @PreAuthorize(READ)
    @GetMapping("/files/{id}/reports/full")
    public ResponseEntity<byte[]> fullReport(@PathVariable("id") long id,
                                             @RequestParam(value = "format", required = false) String format) {
        PayrollAccess.headOffice();
        return download(reports.full(id, format, AcbActor.current()));
    }

    /** Money an employer paid over, and the month's file checked again (PAY-28). */
    @PreAuthorize(WRITE)
    @PostMapping("/payments")
    public ResponseEntity<PayrollPaymentDto> recordPayment(@Valid @RequestBody PayrollPaymentRequest request) {
        PayrollAccess.headOffice();
        PayrollPaymentDto payment = payments.record(request, AcbActor.current());
        return ResponseEntity.status(payment.alreadyRecorded() ? HttpStatus.OK : HttpStatus.CREATED).body(payment);
    }

    @PreAuthorize(READ)
    @GetMapping("/payments")
    public PayrollPage<PayrollPaymentDto> payments(@RequestParam(value = "employerId", required = false) Long employerId,
                                                   @RequestParam(value = "period", required = false) String period,
                                                   @RequestParam(value = "page", required = false) Integer page,
                                                   @RequestParam(value = "size", required = false) Integer size) {
        PayrollAccess.headOffice();
        return payments.list(employerId, period, page, size);
    }

    static ResponseEntity<byte[]> download(PayrollReports.Report report) {
        return download(report.fileName(), MediaType.parseMediaType(report.mediaType()), report.bytes());
    }

    static ResponseEntity<byte[]> download(String name, MediaType type, byte[] bytes) {
        // A plain name where it is plain ASCII, as the reports' are; RFC 6266's filename* only where it is not -- given
        // a charset, Spring writes even an ASCII name into filename as an RFC 2047 word ("=?UTF-8?Q?...").
        ContentDisposition disposition = StandardCharsets.US_ASCII.newEncoder().canEncode(name)
                ? ContentDisposition.attachment().filename(name).build()
                : ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(bytes);
    }
}

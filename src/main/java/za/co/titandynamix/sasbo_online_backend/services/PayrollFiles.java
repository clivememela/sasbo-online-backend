package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollReconcileRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollRowDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEntry;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.payroll.FileSniffer;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLayouts;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMoney;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollParser;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollRequests;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The payroll files: loaded, typed, compared again, listed and read -- the one place administrators reach every
 * employer's file (Stephne, PAY-32).
 *
 * <h2>Loading a file</h2>
 * What the file IS is judged by its bytes, never its name. It is read and compared BEFORE anything is stored, so a file
 * that cannot be read leaves nothing behind; then the file, its lines, matches and discrepancies are written in one
 * transaction. The same bytes for the same employer and month load once -- a double click or a retry answers the file
 * already loaded. A different file for a month that has one is refused unless it replaces it (PAY-07: one file a
 * month): the old one is then SUPERSEDED, kept, and its open discrepancies resolved as superseded.
 *
 * <p>🔒 Nothing of a file's content is logged: ids, counts and codes only.
 */
@Slf4j
@Service
public class PayrollFiles {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final PayrollEmployers employers;
    private final PayrollFileRepository files;
    private final PayrollReconciler reconciler;
    private final PayrollStore store;
    private final PayrollReads reads;
    private final PayrollAudit audit;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public PayrollFiles(PayrollEmployers employers, PayrollFileRepository files, PayrollReconciler reconciler,
                        PayrollStore store, PayrollReads reads, PayrollAudit audit, Clock clock,
                        PlatformTransactionManager transactionManager) {
        this.employers = employers;
        this.files = files;
        this.reconciler = reconciler;
        this.store = store;
        this.reads = reads;
        this.audit = audit;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** A file loaded: its summary, and whether this call loaded it (or found the same bytes loaded already). */
    public record Loaded(PayrollFileSummaryDto summary, boolean created) {
    }

    /** A file's bytes as they arrived, to download. */
    public record Content(String fileName, PayrollFormat format, byte[] bytes) {
    }

    // ---------------------------------------------------------------------------------------------------- loading

    public Loaded upload(Long employerId, String period, String originalName, byte[] bytes, boolean replace,
                         String columnMapping, String actor) {
        if (employerId == null) {
            throw new PayrollRefusal(PayrollRefusalCode.REQUEST_MALFORMED, "Choose the employer whose file this is.",
                    Map.of("field", "employerId"));
        }
        PayrollEmployer employer = employers.named(employerId);
        YearMonth month = PayrollRequests.period(period, clock);
        if (bytes == null || bytes.length == 0) {
            throw new PayrollRefusal(PayrollRefusalCode.FILE_MISSING, "Choose the employer's file to load.");
        }
        if (bytes.length > PayrollLimits.MAX_BYTES) {
            throw new PayrollRefusal(PayrollRefusalCode.FILE_TOO_LARGE, "This file is larger than an employer's "
                    + "payroll file may be (10 MB).", Map.of("limitBytes", String.valueOf(PayrollLimits.MAX_BYTES)));
        }
        PayrollFormat format = formatOf(FileSniffer.sniff(bytes));
        Map<String, String> mapping = mappingOf(columnMapping);
        return load(employer, month, PayrollEntry.UPLOAD, format, cleanName(originalName, employer, month, format),
                bytes, replace, mapping, null, actor);
    }

    /** Rows typed in by hand from a file the system cannot read (PAY-03), loaded and compared like any file. */
    public Loaded typed(PayrollTypedEntryRequest request, String actor) {
        PayrollEmployer employer = employers.named(request.employerId());
        YearMonth month = PayrollRequests.period(request.period(), clock);
        BigDecimal stated = null;
        if (request.statedTotal() != null && !request.statedTotal().isBlank()) {
            stated = PayrollKeys.amount(request.statedTotal());
            if (stated == null) {
                throw new PayrollRefusal(PayrollRefusalCode.TYPED_ROWS_INVALID, "The total stated could not be read "
                        + "as an amount.", Map.of("field", "statedTotal"));
            }
        }
        StringBuilder csv = new StringBuilder("staffNumber,idNumber,initials,surname,amount\r\n");
        List<PayrollTypedEntryRequest.Row> rows = request.rows();
        for (int i = 0; i < rows.size(); i++) {
            PayrollTypedEntryRequest.Row row = rows.get(i);
            String problem = PayrollKeys.amount(row.amount()) == null ? "amount"
                    : blank(row.staffNumber()) && blank(row.idNumber()) && blank(row.surname()) ? "surname" : null;
            if (problem != null) {
                throw new PayrollRefusal(PayrollRefusalCode.TYPED_ROWS_INVALID, problem.equals("amount")
                        ? "A typed row's amount could not be read." : "Each typed row needs a staff number, an ID "
                        + "number or a surname.", Map.of("row", String.valueOf(i + 1), "field", problem));
            }
            csv.append(csvCell(row.staffNumber())).append(',').append(csvCell(row.idNumber())).append(',')
                    .append(csvCell(row.initials())).append(',').append(csvCell(row.surname())).append(',')
                    .append(csvCell(row.amount())).append("\r\n");
        }
        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String name = "Typed entry - " + employer.getName() + " - " + PayrollMatcher.monthName(month) + ".csv";
        return load(employer, month, PayrollEntry.TYPED, PayrollFormat.CSV, name, bytes, false, Map.of(), stated, actor);
    }

    private Loaded load(PayrollEmployer employer, YearMonth month, PayrollEntry entry, PayrollFormat format,
                        String name, byte[] bytes, boolean replace, Map<String, String> mapping,
                        BigDecimal statedTotal, String actor) {
        String sha = sha256(bytes);
        LocalDate period = month.atDay(1);
        Optional<PayrollFile> same = files.findByPayrollEmployerIdAndPeriodAndSha256(employer.getId(), period, sha);
        if (same.isPresent()) {
            return new Loaded(summary(same.get().getId(), true), false);
        }
        Optional<PayrollFile> live = files.findFirstByPayrollEmployerIdAndPeriodAndStatus(employer.getId(), period,
                PayrollFileStatus.RECONCILED);
        if (live.isPresent() && !replace) {
            throw exists(live.get());
        }
        Map<String, String> effective = mapping.isEmpty() ? PayrollLayouts.mapping(employer.getColumnMapping())
                : mapping;
        boolean typed = entry == PayrollEntry.TYPED;
        PayrollParser.Parsed parsed = reconciler.parse(format, bytes, employers.layoutOf(employer), effective, month,
                typed);
        PayrollReconciler.Compared compared = reconciler.compare(employer.getId(), employer.getName(), month, parsed,
                statedTotal);
        try {
            Long id = transactions.execute(status -> {
                Instant now = clock.instant();
                PayrollFile replaced = null;
                if (live.isPresent()) {
                    // The old file steps aside first: the partial unique index holds one live file a month.
                    PayrollFile old = live.get();
                    old.setStatus(PayrollFileStatus.SUPERSEDED);
                    replaced = files.saveAndFlush(old);
                }
                PayrollFile file = new PayrollFile();
                file.setPayrollEmployerId(employer.getId());
                file.setPeriod(period);
                file.setEntry(entry);
                file.setFormat(format);
                file.setOriginalName(name);
                file.setSizeBytes(bytes.length);
                file.setSha256(sha);
                file.setContent(bytes);
                file.setStatus(PayrollFileStatus.RECONCILED);
                file.setUploadedBy(actor);
                file.setUploadedAt(now);
                file.setRetainUntil(LocalDate.now(clock.withZone(za.co.titandynamix.sasbo_online_backend.utils
                        .MonthRange.ZONE)).plusYears(PayrollLimits.RETAIN_YEARS));
                file.setStatedTotal(statedTotal);
                file.setRowCount(0);
                file.setUnreadableCount(0);
                file.setRowsNotRead(0);
                file.setMatchedCount(0);
                file.setDiscrepancyCount(0);
                file.setOpenCount(0);
                file.setFileTotal(BigDecimal.ZERO.setScale(2));
                file.setAdminFeePercent(employer.getAdminFeePercent());
                file.setRuns(0);
                file = files.saveAndFlush(file);
                reconciler.write(file.getId(), employer.getId(), month, compared, employer.getAdminFeePercent(),
                        PayrollLayouts.mappingJson(mapping), statedTotal, actor);
                audit.record(PayrollEventKind.FILE_LOADED, file.getId(), null, entry + " " + format + " for "
                        + employer.getCode() + " " + month + ": " + parsed.lines().size() + " lines, "
                        + compared.result().matched() + " matched, " + (compared.result().found().size()
                        + compared.fileLevel().size()) + " discrepancies", actor);
                if (replaced != null) {
                    replaced.setSupersededBy(file.getId());
                    files.saveAndFlush(replaced);
                    List<Long> open = store.discrepanciesOf(replaced.getId()).stream()
                            .filter(d -> !"RESOLVED".equals(d.status())).map(PayrollStore.Existing::id).toList();
                    store.resolveBySystem(open, PayrollOutcome.SUPERSEDED.name(), PayrollReconciler.SYSTEM, now);
                    store.recount(replaced.getId());
                    audit.record(PayrollEventKind.FILE_SUPERSEDED, replaced.getId(), null, "Replaced by file "
                            + file.getId() + "; " + open.size() + " open discrepancies resolved as superseded", actor);
                }
                return file.getId();
            });
            log.info("Payroll file {} loaded for employer {}: {} lines", id, employer.getId(), parsed.lines().size());
            return new Loaded(summary(id, false), true);
        } catch (DataIntegrityViolationException raced) {
            Optional<PayrollFile> again = files.findByPayrollEmployerIdAndPeriodAndSha256(employer.getId(), period, sha);
            if (again.isPresent()) {
                return new Loaded(summary(again.get().getId(), true), false);
            }
            Optional<PayrollFile> liveNow = files.findFirstByPayrollEmployerIdAndPeriodAndStatus(employer.getId(),
                    period, PayrollFileStatus.RECONCILED);
            if (liveNow.isPresent()) {
                throw exists(liveNow.get());
            }
            throw raced;
        }
    }

    private static PayrollRefusal exists(PayrollFile live) {
        return new PayrollRefusal(PayrollRefusalCode.FILE_EXISTS_FOR_PERIOD, "A different file is already loaded for "
                + "this employer and month. Replace it, or choose another month.", PayrollRefusal.details(
                "fileId", String.valueOf(live.getId()), "uploadedAt", live.getUploadedAt().toString(),
                "uploadedBy", live.getUploadedBy()));
    }

    // ------------------------------------------------------------------------------------------- comparing again

    /**
     * Compares a file again -- against the members as they are now and, where a column mapping is given, reading it
     * again by that mapping. A discrepancy found again keeps its notes and status; one no longer found is resolved
     * NO_LONGER_APPLIES.
     */
    public PayrollFileSummaryDto reconcile(long id, PayrollReconcileRequest request, String actor) {
        PayrollFile file = live(id);
        if (request != null && request.version() != null && !request.version().equals(file.getVersion())) {
            throw changedSince(file);
        }
        PayrollEmployer employer = employers.named(file.getPayrollEmployerId());
        Map<String, String> mapping = request != null && request.columnMapping() != null
                ? request.columnMapping() : PayrollLayouts.mapping(file.getColumnMapping());
        Map<String, String> effective = mapping.isEmpty() ? PayrollLayouts.mapping(employer.getColumnMapping())
                : mapping;
        YearMonth month = YearMonth.from(file.getPeriod());
        boolean typed = file.getEntry() == PayrollEntry.TYPED;
        PayrollParser.Parsed parsed = reconciler.parse(file.getFormat(), file.getContent(), employers.layoutOf(employer),
                effective, month, typed);
        BigDecimal stated = typed ? file.getStatedTotal() : null;
        PayrollReconciler.Compared compared = reconciler.compare(employer.getId(), employer.getName(), month, parsed,
                stated);
        int version = file.getVersion();
        transactions.executeWithoutResult(status -> {
            if (!store.claim(id, version)) {
                throw changedSince(file);
            }
            reconciler.write(id, employer.getId(), month, compared, employer.getAdminFeePercent(),
                    PayrollLayouts.mappingJson(mapping), stated, actor);
            audit.record(PayrollEventKind.FILE_RECONCILED, id, null, "Compared again: " + parsed.lines().size()
                    + " lines, " + compared.result().matched() + " matched, " + (compared.result().found().size()
                    + compared.fileLevel().size()) + " discrepancies", actor);
        });
        return summary(id, false);
    }

    private PayrollFile live(long id) {
        PayrollFile file = files.findById(id).orElseThrow(PayrollFiles::notFound);
        if (file.getStatus() == PayrollFileStatus.PURGED) {
            throw purged();
        }
        if (file.getStatus() == PayrollFileStatus.SUPERSEDED) {
            throw new PayrollRefusal(PayrollRefusalCode.FILE_SUPERSEDED, "This file was replaced by a later file for "
                    + "the same employer and month.", PayrollRefusal.details("supersededBy",
                    file.getSupersededBy() == null ? null : String.valueOf(file.getSupersededBy())));
        }
        return file;
    }

    private static PayrollRefusal changedSince(PayrollFile file) {
        return new PayrollRefusal(PayrollRefusalCode.FILE_CHANGED_SINCE, "This file was compared again after you "
                + "opened it. Reload it to see the latest comparison.", Map.of("version",
                String.valueOf(file.getVersion())));
    }

    static PayrollRefusal notFound() {
        return new PayrollRefusal(PayrollRefusalCode.FILE_NOT_FOUND, "No such payroll file.");
    }

    static PayrollRefusal purged() {
        return new PayrollRefusal(PayrollRefusalCode.FILE_PURGED, "This file's contents were deleted a year after it "
                + "was loaded. What was found, and what was done about it, is kept.");
    }

    // ---------------------------------------------------------------------------------------------------- reading

    public PayrollPage<PayrollFileDto> list(Long employerId, String period, String status, Integer page,
                                            Integer size) {
        int[] paging = PayrollRequests.page(page, size);
        PayrollFileStatus wanted = PayrollRequests.word(status, PayrollFileStatus.class, "status");
        return reads.files(employerId, PayrollRequests.periodFilter(period, clock),
                wanted == null ? null : wanted.name(), paging[0], paging[1]);
    }

    public PayrollFileSummaryDto summary(long id) {
        return summary(id, false);
    }

    PayrollFileSummaryDto summary(long id, boolean alreadyLoaded) {
        PayrollReads.FileRow f = reads.file(id).orElseThrow(PayrollFiles::notFound);
        Map<PayrollDiscrepancyType, int[]> byType = reads.typeCounts(id);
        List<PayrollFileSummaryDto.TypeCount> types = new ArrayList<>();
        byType.forEach((type, counts) -> types.add(new PayrollFileSummaryDto.TypeCount(type.name(), counts[0],
                counts[1], counts[2])));
        BigDecimal expectedPayment = PayrollMoney.expectedPayment(f.fileTotal(), f.adminFeePercent());
        boolean paid = f.payments() > 0;
        PayrollFileSummaryDto.Totals totals = new PayrollFileSummaryDto.Totals(PayrollKeys.money(f.fileTotal()),
                PayrollKeys.money(f.statedTotal()),
                f.statedTotal() == null ? null : f.statedTotal().compareTo(f.fileTotal()) == 0,
                f.rateAmount() == null ? null : new PayrollFileSummaryDto.Subscription(PayrollKeys.money(f.rateAmount()),
                        f.rateEffectiveFrom() == null ? null : f.rateEffectiveFrom().toString()),
                PayrollKeys.money(f.expectedTotal()), PayrollKeys.money(f.adminFeePercent()),
                PayrollKeys.money(PayrollMoney.adminFee(f.fileTotal(), f.adminFeePercent())),
                PayrollKeys.money(expectedPayment), paid ? PayrollKeys.money(f.received()) : null,
                paid ? PayrollKeys.money(f.received().subtract(expectedPayment)) : null, f.paymentStatus().name());
        PayrollFileSummaryDto.Layout layout = new PayrollFileSummaryDto.Layout(
                "TYPED".equals(f.entry()) ? PayrollLayouts.TYPED : f.employerLayoutKey(), f.sheetName(), f.headingRow(),
                PayrollLayouts.list(f.headingCells()), PayrollLayouts.mapping(f.headings()));
        return new PayrollFileSummaryDto(PayrollReads.fileDto(f), alreadyLoaded, layout, counts(f), types, totals,
                reads.payments(f.employer().id(), f.period()), reads.queriesOf(id), f.runs(), f.lastRunBy());
    }

    private static PayrollFileSummaryDto.Counts counts(PayrollReads.FileRow f) {
        Map<String, Integer> c = new LinkedHashMap<>();
        if (f.counts() != null) {
            try {
                c.putAll(JSON.readValue(f.counts(), new TypeReference<Map<String, Integer>>() { }));
            } catch (JsonProcessingException unreadable) {
                // Figures written by this code; if one could not be read the summary shows noughts, not a failure.
                c.clear();
            }
        }
        return new PayrollFileSummaryDto.Counts(c.getOrDefault("rowsRead", f.rowCount()),
                c.getOrDefault("rowsUnreadable", f.unreadableCount()), c.getOrDefault("matched", f.matchedCount()),
                c.getOrDefault("matchedByStaffNumber", 0), c.getOrDefault("matchedByIdNumber", 0),
                c.getOrDefault("matchedByName", 0), c.getOrDefault("notMembers", 0),
                c.getOrDefault("missingMembers", 0), c.getOrDefault("catchUps", 0), c.getOrDefault("listedTwice", 0),
                c.getOrDefault("rateUnknown", 0), c.getOrDefault("linesNotRead", f.rowsNotRead()));
    }

    /** A file's lines, each with its match; {@code show} is ALL, MATCHED, NOT_MATCHED or UNREADABLE. */
    public PayrollPage<PayrollRowDto> rows(long id, String show, Integer page, Integer size) {
        int[] paging = PayrollRequests.page(page, size);
        RowFilter filter = PayrollRequests.word(show, RowFilter.class, "show");
        PayrollReads.FileRow f = reads.file(id).orElseThrow(PayrollFiles::notFound);
        if ("PURGED".equals(f.status())) {
            throw purged();
        }
        return reads.rows(id, (filter == null ? RowFilter.ALL : filter).name(), paging[0], paging[1]);
    }

    /** The lines a file's page can show. */
    public enum RowFilter { ALL, MATCHED, NOT_MATCHED, UNREADABLE }

    /** The file as it arrived, for an administrator to open (PAY-32: one place where they can all reach the files). */
    public Content content(long id) {
        PayrollFile file = files.findById(id).orElseThrow(PayrollFiles::notFound);
        if (file.getContent() == null) {
            throw purged();
        }
        return new Content(file.getOriginalName(), file.getFormat(), file.getContent());
    }

    // ---------------------------------------------------------------------------------------------------- helpers

    private static PayrollFormat formatOf(FileSniffer.Kind kind) {
        return switch (kind) {
            case XLSX -> PayrollFormat.XLSX;
            case XLS -> PayrollFormat.XLS;
            case CSV -> PayrollFormat.CSV;
            case PDF, PICTURE -> throw new PayrollRefusal(PayrollRefusalCode.TYPED_ENTRY_REQUIRED, "This is a PDF or "
                    + "a picture, which the system cannot read. Type its rows in instead.");
            case UNKNOWN -> throw new PayrollRefusal(PayrollRefusalCode.FILE_TYPE_NOT_ACCEPTED, "This is not a "
                    + "spreadsheet the system can read. Load the employer's file as Excel (.xlsx or .xls) or CSV.");
        };
    }

    private static Map<String, String> mappingOf(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        if (json.length() > 4000) {
            throw new PayrollRefusal(PayrollRefusalCode.COLUMN_MAPPING_INVALID, "The column mapping is longer than "
                    + "any mapping could be.", Map.of("field", "columnMapping"));
        }
        try {
            Map<String, String> mapping = JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
            return mapping == null ? Map.of() : mapping;
        } catch (JsonProcessingException unreadable) {
            throw new PayrollRefusal(PayrollRefusalCode.COLUMN_MAPPING_INVALID, "The column mapping could not be read: "
                    + "send each field with the heading it is printed under.", Map.of("field", "columnMapping"));
        }
    }

    /** The name the file arrived with, without a path, without control characters, capped; never trusted for its type. */
    static String cleanName(String original, PayrollEmployer employer, YearMonth month, PayrollFormat format) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>|*?]", "").strip();
        if (name.isEmpty()) {
            name = employer.getName() + " payroll " + PayrollMatcher.monthName(month) + "."
                    + format.name().toLowerCase(java.util.Locale.ROOT);
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static String csvCell(String text) {
        if (text == null) {
            return "";
        }
        String clean = text.replace("\r", " ").replace("\n", " ");
        return clean.contains(",") || clean.contains("\"") ? "\"" + clean.replace("\"", "\"\"") + "\"" : clean;
    }
}

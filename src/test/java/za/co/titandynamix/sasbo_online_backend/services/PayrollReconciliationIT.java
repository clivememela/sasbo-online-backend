package za.co.titandynamix.sasbo_online_backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollReconcileRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollRowDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.PayrollItSupport;
import za.co.titandynamix.sasbo_online_backend.utils.PayrollSeeder;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Payroll on PostgreSQL, end to end: each of the fixtures' invented samples in SASBO's real layouts loaded through the
 * service as an upload would be, against members put on record as its manifest says -- and the discrepancies found are
 * EXACTLY the ones the manifest expects, no more and no fewer (GENERATE.md), each named for Stephne's rule. Then what
 * only a database can show: a re-upload loading nothing, a month's other file replacing it, a comparison again keeping
 * notes and statuses, the payment check, queries, the year's deletion, the constraints by name, and the repositories'
 * own statements (PayrollStore, PayrollReads, PayrollFileRepository, PayrollEmployerRepository).
 */
@DisplayName("Payroll on PostgreSQL: the fixtures' samples, reconciled end to end")
class PayrollReconciliationIT extends AbstractSwitchedOnIT {

    /** Layouts that print an initials column -- the only files on which the generator's changed initials show. */
    private static final Set<String> PRINTS_INITIALS = Set.of("access-bank", "african-bank", "national-housing",
            "nedbank", "standard-bank", "bankserv", "sbv", "sarb");

    @Autowired
    private PayrollSeeder seeder;

    @Autowired
    private PayrollFiles files;

    @Autowired
    private PayrollDiscrepancies discrepancies;

    @Autowired
    private PayrollQueries queries;

    @Autowired
    private PayrollPayments payments;

    @Autowired
    private PayrollReports reports;

    @Autowired
    private PayrollRetention retention;

    @Autowired
    private PayrollReads reads;

    @Autowired
    private PayrollStore store;

    @Autowired
    private PayrollFileRepository fileRepository;

    @Autowired
    private PayrollEmployerRepository employerRepository;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository discrepancyRepository;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyNoteRepository noteRepository;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryRepository queryRepository;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.PayrollPaymentRepository paymentRepository;

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.PayrollEventRepository eventRepository;

    @BeforeEach
    void theEmployers() {
        seeder.seedEmployers();
    }

    // --------------------------------------------------------------------------------------- every layout, exactly

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "absa, ABSA, ABSA BANK", "access-bank, ACCESS_BANK, ACCESS BANK SA", "african-bank, AFRICAN_BANK, AFRICAN BANK",
            "bankserv, BANKSERV, BANKSERV", "capitec, CAPITEC, CAPITEC BANK", "fnb-wesbank, FNB_WESBANK, FIRSTRAND BANK",
            "landbank, LANDBANK, LANDBANK", "metropolitan-health, METROPOLITAN_HEALTH, METROPOLITAN HEALTH",
            "national-housing, NATIONAL_HOUSING, NATIONAL HOUSING", "nedbank, NEDBANK, NEDBANK",
            "old-mutual, OLD_MUTUAL, OLD MUTUAL", "sbv, SBV, SBV", "standard-bank, STANDARD_BANK, STANDARD BANK",
            "sarb, SARB, SA RESERVE BANK"})
    @DisplayName("Stephne's rules on each layout: the manifest's expected discrepancies, exactly")
    void eachLayoutExactly(String slug, String code, String bankName) {
        JsonNode manifest = PayrollItSupport.manifest(slug);
        PayrollItSupport.seedManifest(jdbc, manifest, bankName);
        long employerId = employerRepository.findByCode(code).orElseThrow().getId();

        PayrollFileSummaryDto summary = files.upload(employerId, "2026-09", slug + ".xlsx",
                PayrollItSupport.resource(slug + (slug.equals("sarb") ? ".csv" : ".xlsx")), false, null, "an IT")
                .summary();

        assertThat(summary.counts().rowsRead()).isEqualTo(manifest.path("totals").path("rowsOnFile").asInt());
        assertThat(summary.totals().fileTotal()).isEqualTo(manifest.path("totals").path("sumOfAmounts").asText());

        Map<String, String> keyOfNumber = new HashMap<>();
        int n = 0;
        for (JsonNode person : manifest.path("people")) {
            if (person.path("seedAsMember").asBoolean()) {
                keyOfNumber.put(String.format("T-%05d", ++n), person.path("key").asText());
            }
        }
        Map<Integer, String> keyOfLine = new HashMap<>();
        manifest.path("rows").forEach(r -> keyOfLine.put(r.path("excelRow").asInt(), r.path("key").asText()));

        Set<String> found = new TreeSet<>();
        for (PayrollDiscrepancyDto d : discrepancies.ofFile(summary.file().id(), null, null, 0, 100).content()) {
            String who = d.member() != null ? keyOfNumber.get(d.member().membershipNumber())
                    : d.row() != null ? (slug.equals("sarb") ? "line" : keyOfLine.get(d.row().rowNumber())) : "file";
            found.add(d.type() + ":" + who);
        }
        Set<String> expected = new TreeSet<>();
        for (JsonNode e : manifest.path("expectedDiscrepancies")) {
            String key = e.path("key").asText();
            switch (e.path("type").asText()) {
                case "duplicate" -> expected.add("DEDUCTED_TWICE:" + key);
                case "staff_number_mismatch" -> expected.add("EMPLOYEE_NUMBER_DIFFERS:" + key);
                case "old_rate", "zero_deduction" -> expected.add("AMOUNT_DIFFERS:" + key);
                case "not_a_member" -> expected.add("NOT_A_MEMBER:" + (slug.equals("sarb") ? "line" : key));
                case "left_but_deducted" -> expected.add("MEMBERSHIP_ENDED:" + key);
                case "missing_from_file" -> expected.add("MISSING_FROM_FILE:" + key);
                case "initial_mismatch" -> {
                    if (PRINTS_INITIALS.contains(slug)) {
                        expected.add("NAME_DIFFERS:" + key);
                    }
                }
                // A catch-up -- one larger amount for a member with a month owed, or a line the file marks for an
                // earlier month -- is no discrepancy (PAY-25, PAY-26).
                case "catch_up", "retro_line" -> { }
                default -> throw new AssertionError("an oddity this test does not know: " + e.path("type").asText());
            }
        }
        assertThat(found).as(slug + ": the discrepancies found").isEqualTo(expected);
    }

    // ------------------------------------------------------------------------------------- loading once, replacing

    @Test
    @DisplayName("the same bytes again load nothing; a month's other file is refused, then replaces it when asked")
    void loadingOnceAndReplacing() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        byte[] sample = PayrollItSupport.resource("standard-bank.xlsx");

        PayrollFiles.Loaded first = files.upload(employer, "2026-09", "a.xlsx", sample, false, null, "an IT");
        PayrollFiles.Loaded again = files.upload(employer, "2026-09", "a again.xlsx", sample, false, null, "an IT");
        assertThat(first.created()).isTrue();
        assertThat(again.created()).isFalse();
        assertThat(again.summary().alreadyLoaded()).isTrue();
        assertThat(again.summary().file().id()).isEqualTo(first.summary().file().id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_files", Integer.class)).isEqualTo(1);

        byte[] other = za.co.titandynamix.sasbo_online_backend.payroll.PayrollSampleWriter.write("standard-bank",
                java.time.YearMonth.of(2026, 9), List.of(new za.co.titandynamix.sasbo_online_backend.payroll
                        .PayrollSampleWriter.Line("991001", null, "Mr", "A", null, "Elsewhere", new BigDecimal("174.00"),
                        null, null, null)));
        assertThatThrownBy(() -> files.upload(employer, "2026-09", "b.xlsx", other, false, null, "an IT"))
                .isInstanceOfSatisfying(PayrollRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(PayrollRefusalCode.FILE_EXISTS_FOR_PERIOD);
                    assertThat(r.details()).containsEntry("fileId", String.valueOf(first.summary().file().id()));
                });
        PayrollFiles.Loaded replaced = files.upload(employer, "2026-09", "b.xlsx", other, true, null, "an IT");
        assertThat(replaced.created()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM payroll_files WHERE id = ?", String.class,
                first.summary().file().id())).isEqualTo("SUPERSEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ? AND status <> "
                + "'RESOLVED'", Integer.class, first.summary().file().id())).as("the old file's open ones").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ? AND outcome = "
                + "'SUPERSEDED'", Integer.class, first.summary().file().id())).isPositive();
    }

    // ------------------------------------------------------------------------------- comparing again keeps the work

    @Test
    @DisplayName("Stephne, PAY-20: compared again, a discrepancy keeps its notes and outcome; one no longer found is closed")
    void comparingAgain() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "sb.xlsx", PayrollItSupport.resource("standard-bank.xlsx"), false,
                null, "an IT").summary().file().id();
        List<PayrollDiscrepancyDto> before = discrepancies.ofFile(file, null, null, 0, 100).content();
        PayrollDiscrepancyDto amount = before.stream().filter(d -> d.type().equals("AMOUNT_DIFFERS")).findFirst()
                .orElseThrow();
        PayrollDiscrepancyDto ended = before.stream().filter(d -> d.type().equals("MEMBERSHIP_ENDED")).findFirst()
                .orElseThrow();
        discrepancies.addNote(amount.id(), "Asked Standard Bank to correct the amount.", "an IT");
        discrepancies.resolve(amount.id(), "ARREARS_RECORDED", "R7.35 recorded as arrears.", amount.version(), "an IT");
        discrepancies.addNote(ended.id(), "Checking whether the member rejoined.", "an IT");

        // The resigned member turns out to have rejoined: their record is corrected, and the file compared again.
        jdbc.update("UPDATE members SET status = 'Active' WHERE id = ?", ended.member().id());
        PayrollFileSummaryDto again = files.reconcile(file, new PayrollReconcileRequest(null, null), "an IT");

        assertThat(again.runs()).isEqualTo(2);
        PayrollDiscrepancyDto amountAfter = discrepancies.get(amount.id());
        assertThat(amountAfter.status()).isEqualTo("RESOLVED");
        assertThat(amountAfter.resolution().outcome()).isEqualTo("ARREARS_RECORDED");
        assertThat(amountAfter.notes()).hasSize(2);
        PayrollDiscrepancyDto endedAfter = discrepancies.get(ended.id());
        assertThat(endedAfter.status()).isEqualTo("RESOLVED");
        assertThat(endedAfter.resolution().outcome()).isEqualTo("NO_LONGER_APPLIES");
        assertThat(endedAfter.notes()).as("its note is kept").hasSize(1);

        assertThatThrownBy(() -> discrepancies.resolve(amount.id(), "OTHER", "again", amountAfter.version(), "an IT"))
                .isInstanceOfSatisfying(PayrollRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.DISCREPANCY_ALREADY_RESOLVED));
        PayrollDiscrepancyDto missing = discrepancies.ofFile(file, "MISSING_FROM_FILE", null, 0, 10).content().get(0);
        assertThatThrownBy(() -> discrepancies.resolve(missing.id(), "REFUNDED", "wrong outcome", missing.version(),
                "an IT")).isInstanceOfSatisfying(PayrollRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(PayrollRefusalCode.OUTCOME_NOT_FOR_TYPE);
                    assertThat(r.details().get("allowed")).contains("DEDUCTION_RESTARTED");
                });
        assertThatThrownBy(() -> discrepancies.resolve(missing.id(), "MEMBER_SUSPENDED", "stale", missing.version() + 5,
                "an IT")).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.DISCREPANCY_CHANGED_SINCE));
    }

    // -------------------------------------------------------------------------------------------- the money check

    @Test
    @DisplayName("Stephne, PAY-27, PAY-28: Absa's payment checked against the total less 3% -- short, then made up")
    void thePaymentCheck() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("absa"), "ABSA BANK");
        long employer = employerRepository.findByCode("ABSA").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "absa.xlsx", PayrollItSupport.resource("absa.xlsx"), false, null,
                "an IT").summary().file().id();
        PayrollFileSummaryDto summary = files.summary(file);
        assertThat(summary.totals().adminFeePercent()).isEqualTo("3.00");
        assertThat(summary.totals().adminFee()).isEqualTo("125.06");
        assertThat(summary.totals().expectedPayment()).as("the fixtures' Absa total less 3%").isEqualTo("4043.59");
        assertThat(summary.totals().paymentStatus()).isEqualTo("NOT_RECORDED");

        payments.record(new PayrollPaymentRequest(employer, "2026-09", "3869.59", "2026-09-30", "EFT 1"), "an IT");
        assertThat(files.summary(file).totals().paymentStatus()).isEqualTo("SHORT");
        PayrollDiscrepancyDto payment = discrepancies.ofFile(file, "PAYMENT_DIFFERS", null, 0, 10).content().get(0);
        assertThat(payment.detail()).isEqualTo("R3869.59 received; R4043.59 expected after the employer's 3% fee: "
                + "R174.00 short.");

        assertThat(payments.record(new PayrollPaymentRequest(employer, "2026-09", "3869.59", "2026-09-30", "EFT 1"),
                "an IT").alreadyRecorded()).as("a double click records it once").isTrue();
        payments.record(new PayrollPaymentRequest(employer, "2026-09", "174.00", "2026-09-30", "EFT 2"), "an IT");
        assertThat(files.summary(file).totals().paymentStatus()).isEqualTo("EXACT");
        PayrollDiscrepancyDto made = discrepancies.get(payment.id());
        assertThat(made.status()).isEqualTo("RESOLVED");
        assertThat(made.resolution().outcome()).isEqualTo("BALANCE_RECEIVED");
    }

    // ---------------------------------------------------------------------------------------------------- queries

    @Test
    @DisplayName("Stephne, PAY-21: a query lists what the employer is to add, delete or correct; raised once per discrepancy")
    void queries() throws Exception {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "sb.xlsx", PayrollItSupport.resource("standard-bank.xlsx"), false,
                null, "an IT").summary().file().id();

        PayrollQueryDto query = queries.raise(file, "an IT");
        assertThat(query.counts().add()).as("the member missing").isEqualTo(1);
        assertThat(query.counts().delete()).as("not a member; membership ended").isEqualTo(2);
        assertThat(query.counts().correct()).as("the old rate; deducted twice").isEqualTo(2);
        assertThat(query.lines()).extracting(PayrollQueryDto.Line::reason)
                .contains("Not a member of SASBO: please stop this deduction.");
        assertThat(discrepancies.ofFile(file, null, "QUERIED", 0, 100).totalElements()).isEqualTo(5);
        assertThatThrownBy(() -> queries.raise(file, "an IT")).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.NOTHING_TO_QUERY));

        PayrollReports.Report locked = queries.download(query.id(), "xlsx", "Correct horse 9", "an IT");
        assertThat(locked.fileName()).isEqualTo("Payroll queries - Standard Bank - September 2026.xlsx");
        assertThat(za.co.titandynamix.sasbo_online_backend.payroll.FileSniffer.sniff(locked.bytes()).name())
                .as("an encrypted workbook").isEqualTo("XLS");
    }

    // ---------------------------------------------------------------------------------------------------- reports

    @Test
    @DisplayName("Stephne, PAY-35, PAY-37: the discrepancies list and the full report, as a workbook and a PDF, read back")
    void reports() throws Exception {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("nedbank"), "NEDBANK");
        long employer = employerRepository.findByCode("NEDBANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "ned.xlsx", PayrollItSupport.resource("nedbank.xlsx"), false,
                null, "an IT").summary().file().id();

        PayrollReports.Report list = reports.discrepancies(file, "xlsx", "an IT");
        try (var workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(list.bytes()))) {
            assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
            var sheet = workbook.getSheet("Discrepancies");
            Set<String> types = new HashSet<>();
            sheet.forEach(row -> {
                if (row.getCell(0) != null && row.getCell(0).getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                    types.add(row.getCell(0).getStringCellValue());
                }
            });
            assertThat(types).contains("Not a member", "Membership ended", "Missing from the file", "Amount differs",
                    "Deducted twice", "Different employee number", "Name does not match");
        }
        PayrollReports.Report full = reports.full(file, "pdf", "an IT");
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(full.bytes())) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertThat(text).contains("Payroll reconciliation", "Nedbank", "September 2026", "Every line of the file",
                    "Members on record who are not on the file", "Staff number");
        }
    }

    // -------------------------------------------------------------------------------------------- one year's keep

    @Test
    @DisplayName("Stephne, PAY-39: a year on, the file and its lines are deleted; its discrepancies and outcomes stay")
    void oneYear() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "sb.xlsx", PayrollItSupport.resource("standard-bank.xlsx"), false,
                null, "an IT").summary().file().id();
        int found = jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ?", Integer.class, file);
        jdbc.update("UPDATE payroll_files SET retain_until = DATE '2026-09-14' WHERE id = ?", file);

        assertThat(retention.purgeDue()).isEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM payroll_files WHERE id = ?", String.class, file)).isEqualTo("PURGED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_files WHERE id = ? AND content IS NULL",
                Integer.class, file)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_rows WHERE file_id = ?", Integer.class, file)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ?", Integer.class,
                file)).as("what was found is kept").isEqualTo(found);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ? AND row_id IS NOT "
                + "NULL", Integer.class, file)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies WHERE file_id = ? AND line_number IS "
                + "NOT NULL", Integer.class, file)).isPositive();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_events WHERE file_id = ? AND kind = 'FILE_PURGED'",
                Integer.class, file)).isEqualTo(1);
        assertThatThrownBy(() -> files.rows(file, null, null, null)).isInstanceOfSatisfying(PayrollRefusal.class,
                r -> assertThat(r.code()).isEqualTo(PayrollRefusalCode.FILE_PURGED));
        assertThat(files.summary(file).counts().rowsRead()).as("the summary still says what was read")
                .isEqualTo(25);
        assertThat(retention.purgeDue()).as("nothing twice").isZero();
    }

    // -------------------------------------------------------------------------------------------- typed, and 2025

    @Test
    @DisplayName("PAY-03: typed rows load like a file; a stated total that is not the lines' sum is FILE_TOTAL_DIFFERS")
    void typedRows() {
        PayrollItSupport.insert(jdbc, List.of(new PayrollItSupport.MemberRow("T-00001", null, null, "Mr", "RT", "Remo Taxo",
                "Ledgerwood", "Active", "Payroll Deduction", "GBS MUTUAL BANK", BigDecimal.ZERO)));
        long employer = employerRepository.findByCode("GBS").orElseThrow().getId();
        PayrollFileSummaryDto summary = files.typed(new PayrollTypedEntryRequest(employer, "2026-09", "348.00",
                List.of(new PayrollTypedEntryRequest.Row(null, null, "RT", "LEDGERWOOD", "174.00"))), "an IT").summary();
        assertThat(summary.file().entry()).isEqualTo("TYPED");
        assertThat(summary.layout().key()).isEqualTo("typed");
        assertThat(summary.counts().matchedByName()).isEqualTo(1);
        assertThat(summary.totals().statedTotal()).isEqualTo("348.00");
        assertThat(summary.totals().statedTotalAgrees()).isFalse();
        assertThat(discrepancies.ofFile(summary.file().id(), null, null, 0, 10).content())
                .extracting(PayrollDiscrepancyDto::type).containsExactly("FILE_TOTAL_DIFFERS");
        PayrollRowDto row = files.rows(summary.file().id(), null, null, null).content().get(0);
        assertThat(row.rowNumber()).as("typed rows are numbered as typed").isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 a catch-up in January 2026 reaching December 2025 -- no rate on record -- is RATE_UNKNOWN, never R174.00")
    void theRateOf2025() {
        PayrollItSupport.insert(jdbc, List.of(new PayrollItSupport.MemberRow("T-00001", "1", null, "Mr", "RT", "Remo Taxo",
                "Ledgerwood", "Active", "Payroll Deduction", "GBS MUTUAL BANK", new BigDecimal("174.00"))));
        long employer = employerRepository.findByCode("GBS").orElseThrow().getId();
        long file = files.typed(new PayrollTypedEntryRequest(employer, "2026-01", null,
                List.of(new PayrollTypedEntryRequest.Row("1", null, "RT", "Ledgerwood", "348.00"))), "an IT")
                .summary().file().id();
        PayrollRowDto.Match match = files.rows(file, null, null, null).content().get(0).match();
        assertThat(match.amountStatus()).isEqualTo("RATE_UNKNOWN");
        assertThat(match.rateUnknownFor()).isEqualTo("2025-12");
        assertThat(match.expectedAmount()).isNull();
        assertThat(files.summary(file).counts().rateUnknown()).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------- the database's own rules

    @Test
    @DisplayName("the constraints, by name: the same bytes once, one live file a month, a resolution says how, notes append-only")
    void constraintsByName() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "sb.xlsx", PayrollItSupport.resource("standard-bank.xlsx"), false,
                null, "an IT").summary().file().id();
        String copy = "INSERT INTO payroll_files (payroll_employer_id, period, entry, format, original_name, size_bytes, "
                + "sha256, content, status, uploaded_by, uploaded_at, retain_until) SELECT payroll_employer_id, period, "
                + "entry, format, original_name, size_bytes, %s, content, 'RECONCILED', uploaded_by, uploaded_at, "
                + "retain_until FROM payroll_files WHERE id = " + file;
        assertThatThrownBy(() -> jdbc.update(copy.formatted("sha256"))).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("payroll_files_same_bytes_once");
        assertThatThrownBy(() -> jdbc.update(copy.formatted("repeat('a', 64)")))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("payroll_files_one_live_per_month");
        long discrepancy = jdbc.queryForObject("SELECT min(id) FROM payroll_discrepancies WHERE file_id = ?", Long.class,
                file);
        assertThatThrownBy(() -> jdbc.update("UPDATE payroll_discrepancies SET status = 'RESOLVED' WHERE id = ?",
                discrepancy)).isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("payroll_discrepancies_resolved_says_how");
        discrepancies.addNote(discrepancy, "A note.", "an IT");
        assertThatThrownBy(() -> jdbc.update("UPDATE payroll_discrepancy_notes SET note = 'rewritten'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payroll_events")).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("the store's statements: an employer's members by workplace or name, the history of missed months")
    void theStoresStatements() {
        PayrollItSupport.insert(jdbc, List.of(
                new PayrollItSupport.MemberRow("T-00001", "1", "8106145689086", "Mr", "RT", null, "Ledgerwood", "Active",
                        "Payroll Deduction", " absa bank ", BigDecimal.ZERO),
                new PayrollItSupport.MemberRow("T-00002", "2", null, "Mr", "BK", null, "Tallyburn", "Active",
                        "Payroll Deduction", "NEDBANK", BigDecimal.ZERO)));
        long absa = employerRepository.findByCode("ABSA").orElseThrow().getId();
        var members = store.employerMembers(absa);
        assertThat(members).extracting(m -> m.membershipNumber()).containsExactly("T-00001");
        assertThat(store.membersByIdKey(List.of("8106145689086"), List.of())).containsKey("8106145689086");
        assertThat(store.rates()).isNotEmpty();
        assertThat(store.history(absa, java.time.YearMonth.of(2026, 9))).isEmpty();
        assertThat(fileRepository.idsToPurge(java.time.LocalDate.of(2026, 9, 15),
                org.springframework.data.domain.PageRequest.of(0, 10))).isEmpty();
        assertThat(reads.files(null, null, null, 0, 20).totalElements()).isZero();
    }

    @Test
    @DisplayName("the repositories' own queries: a file's discrepancies, notes, queries, payments and record, read back")
    void theRepositoriesQueries() {
        PayrollItSupport.seedManifest(jdbc, PayrollItSupport.manifest("standard-bank"), "STANDARD BANK");
        long employer = employerRepository.findByCode("STANDARD_BANK").orElseThrow().getId();
        long file = files.upload(employer, "2026-09", "sb.xlsx", PayrollItSupport.resource("standard-bank.xlsx"), false,
                null, "an IT").summary().file().id();
        var found = discrepancyRepository.findAllByFileId(file);
        assertThat(found).hasSize(7);
        String missingSubject = found.stream().filter(d -> d.getType().name().equals("MISSING_FROM_FILE")).findFirst()
                .orElseThrow().getSubjectKey();
        assertThat(discrepancyRepository.findByFileIdAndTypeAndSubjectKey(file,
                za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType.MISSING_FROM_FILE, missingSubject))
                .isPresent();
        discrepancies.addNote(found.get(0).getId(), "First.", "an IT");
        discrepancies.addNote(found.get(0).getId(), "Second.", "an IT");
        assertThat(noteRepository.findAllByDiscrepancyIdOrderByWrittenAtAscIdAsc(found.get(0).getId()))
                .extracting(n -> n.getNote()).containsExactly("First.", "Second.");
        long query = queries.raise(file, "an IT").id();
        assertThat(queryRepository.findAllByFileIdOrderByRaisedAtDescIdDesc(file)).hasSize(1);
        assertThat(discrepancyRepository.findAllByQueryIdOrderByTypeAscLineNumberAscIdAsc(query)).hasSize(5);
        assertThat(discrepancyRepository.findAllByFileIdAndStatusIn(file, java.util.EnumSet.of(
                za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus.QUERIED))).hasSize(5);
        payments.record(new PayrollPaymentRequest(employer, "2026-09", "100.00", "2026-09-14", null), "an IT");
        assertThat(paymentRepository.findSame(employer, java.time.LocalDate.of(2026, 9, 1), new BigDecimal("100.00"),
                java.time.LocalDate.of(2026, 9, 14), null)).hasSize(1);
        assertThat(paymentRepository.findAllByPayrollEmployerIdAndPeriodOrderByReceivedOnAscIdAsc(employer,
                java.time.LocalDate.of(2026, 9, 1))).hasSize(1);
        assertThat(eventRepository.findAllByFileIdOrderByOccurredAtAscIdAsc(file)).extracting(e -> e.getKind().name())
                .contains("FILE_LOADED", "NOTE_ADDED", "QUERY_RAISED", "PAYMENT_RECORDED");
        assertThat(employerRepository.findAllByOrderByOrdinalAsc()).hasSize(16);
        assertThat(fileRepository.findByPayrollEmployerIdAndPeriodAndSha256(employer, java.time.LocalDate.of(2026, 9, 1),
                files.summary(file).file().sha256())).isPresent();
    }
}

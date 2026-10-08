package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollSettingsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollVocabularyDto;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEmployer;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEvent;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollPayment;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDelivery;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyNoteRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollDiscrepancyRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEmployerRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEventRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollFileState;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollPaymentRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryRepository;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollReads;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollStore;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The payroll services' own checks, each service built here as the application builds it: what a request may name,
 * refused by its stable code before anything is read or written. What they do with a database is
 * {@code PayrollReconciliationIT}'s.
 */
@DisplayName("The payroll services: their checks, before anything is written")
class PayrollServicesTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T08:00:00Z"), ZoneId.of("Africa/Johannesburg"));

    private final PayrollEventRepository events = mock(PayrollEventRepository.class);
    private final PayrollEmployerRepository employerRepository = mock(PayrollEmployerRepository.class);
    private final PayrollFileRepository fileRepository = mock(PayrollFileRepository.class);
    private final PayrollDiscrepancyRepository discrepancyRepository = mock(PayrollDiscrepancyRepository.class);
    private final PayrollDiscrepancyNoteRepository noteRepository = mock(PayrollDiscrepancyNoteRepository.class);
    private final PayrollQueryRepository queryRepository = mock(PayrollQueryRepository.class);
    private final za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryFileRepository queryFileRepository =
            mock(za.co.titandynamix.sasbo_online_backend.repository.PayrollQueryFileRepository.class);
    private final PayrollPaymentRepository paymentRepository = mock(PayrollPaymentRepository.class);
    private final PayrollReads reads = mock(PayrollReads.class);
    private final PayrollStore store = mock(PayrollStore.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);

    private PayrollAudit audit;
    private PayrollEmployers employers;
    private PayrollReconciler reconciler;
    private PayrollFiles files;
    private PayrollDiscrepancies discrepancies;
    private PayrollReports reports;
    private PayrollQueries queries;
    private PayrollPayments payments;
    private PayrollRetention retention;

    @BeforeEach
    void build() {
        when(transactions.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        audit = new PayrollAudit(events, CLOCK);
        employers = new PayrollEmployers(employerRepository, reads, audit);
        reconciler = new PayrollReconciler(store, CLOCK);
        files = new PayrollFiles(employers, fileRepository, reconciler, store, reads, audit, CLOCK, transactions);
        discrepancies = new PayrollDiscrepancies(discrepancyRepository, noteRepository, reads, store, audit, CLOCK,
                transactions);
        reports = new PayrollReports(files, reads, CLOCK);
        queries = new PayrollQueries(fileRepository, discrepancyRepository, queryRepository, queryFileRepository, reads,
                store, reports, audit, CLOCK, transactions);
        payments = new PayrollPayments(employers, paymentRepository, fileRepository, reconciler, store, reads, audit,
                CLOCK, transactions);
        retention = new PayrollRetention(fileRepository, store, audit, CLOCK, transactions);
        PayrollEmployer absa = absa();
        when(employerRepository.findById(1L)).thenReturn(Optional.of(absa));
        when(store.received(anyLong(), any())).thenReturn(Map.entry(BigDecimal.ZERO, 0));
    }

    private static PayrollEmployer absa() {
        PayrollEmployer absa = new PayrollEmployer();
        absa.setId(1L);
        absa.setCode("ABSA");
        absa.setName("Absa");
        absa.setOrdinal(1);
        absa.setDelivery(PayrollDelivery.EMAIL);
        absa.setReadable(true);
        absa.setLayoutKey("absa");
        absa.setLayoutVersion(1);
        absa.setLayout("{\"key\":\"absa\",\"version\":1,\"nameStyle\":\"SEPARATE\",\"headings\":{}}");
        absa.setAdminFeePercent(new BigDecimal("3.00"));
        absa.setVersion(0);
        return absa;
    }

    private static PayrollRefusalCode code(Runnable call) {
        try {
            call.run();
        } catch (PayrollRefusal refusal) {
            return refusal.code();
        }
        throw new AssertionError("no refusal");
    }

    // ------------------------------------------------------------------------------------------------- files

    @Test
    @DisplayName("an upload is refused by code -- no employer, a month unread or to come, no bytes, too many, a PDF, no spreadsheet")
    void uploadChecks() {
        byte[] csv = "a,b\n1,2\n".getBytes(StandardCharsets.UTF_8);
        assertThat(code(() -> files.upload(null, "2026-09", "f.csv", csv, false, null, "t")))
                .isEqualTo(PayrollRefusalCode.REQUEST_MALFORMED);
        assertThat(code(() -> files.upload(9L, "2026-09", "f.csv", csv, false, null, "t")))
                .isEqualTo(PayrollRefusalCode.EMPLOYER_UNKNOWN);
        assertThat(code(() -> files.upload(1L, "Sept", "f.csv", csv, false, null, "t")))
                .isEqualTo(PayrollRefusalCode.PERIOD_INVALID);
        assertThat(code(() -> files.upload(1L, "2026-11", "f.csv", csv, false, null, "t")))
                .isEqualTo(PayrollRefusalCode.PERIOD_IN_FUTURE);
        assertThat(code(() -> files.upload(1L, "2026-09", "f.csv", new byte[0], false, null, "t")))
                .isEqualTo(PayrollRefusalCode.FILE_MISSING);
        assertThat(code(() -> files.upload(1L, "2026-09", "f.csv", new byte[(int) (10L * 1024 * 1024) + 1], false,
                null, "t"))).isEqualTo(PayrollRefusalCode.FILE_TOO_LARGE);
        assertThat(code(() -> files.upload(1L, "2026-09", "f.xlsx", "%PDF-1.7 a letter".getBytes(StandardCharsets.US_ASCII),
                false, null, "t"))).as("a PDF, whatever its name says").isEqualTo(PayrollRefusalCode.TYPED_ENTRY_REQUIRED);
        assertThat(code(() -> files.upload(1L, "2026-09", "f.xlsx", new byte[] {0, 1, 2, 3, 4, 5}, false, null, "t")))
                .isEqualTo(PayrollRefusalCode.FILE_TYPE_NOT_ACCEPTED);
        assertThat(code(() -> files.upload(1L, "2026-09", "f.csv", csv, false, "{not json", "t")))
                .isEqualTo(PayrollRefusalCode.COLUMN_MAPPING_INVALID);
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a file's name keeps no path and no control character, and its hash is its SHA-256")
    void namesAndHashes() {
        assertThat(PayrollFiles.cleanName("C:\\fakepath\\Absa Sept.xlsx", absa(), YearMonth.of(2026, 9),
                za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat.XLSX)).isEqualTo("Absa Sept.xlsx");
        assertThat(PayrollFiles.cleanName("  ", absa(), YearMonth.of(2026, 9),
                za.co.titandynamix.sasbo_online_backend.enums.PayrollFormat.CSV)).isEqualTo("Absa payroll September 2026.csv");
        assertThat(PayrollFiles.sha256("abc".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("typed rows are refused naming the row and the field: an amount unread, or nobody to know the person by")
    void typedChecks() {
        PayrollRefusal unreadable = catching(() -> files.typed(new PayrollTypedEntryRequest(1L, "2026-09", null, List.of(
                new PayrollTypedEntryRequest.Row(null, null, "RT", "Ledgerwood", "174.00"),
                new PayrollTypedEntryRequest.Row(null, null, "BK", "Tallyburn", "lots"))), "t"));
        assertThat(unreadable.code()).isEqualTo(PayrollRefusalCode.TYPED_ROWS_INVALID);
        assertThat(unreadable.details()).containsEntry("row", "2").containsEntry("field", "amount");
        PayrollRefusal nobody = catching(() -> files.typed(new PayrollTypedEntryRequest(1L, "2026-09", null, List.of(
                new PayrollTypedEntryRequest.Row(null, null, "RT", " ", "174.00"))), "t"));
        assertThat(nobody.details()).containsEntry("row", "1").containsEntry("field", "surname");
        assertThat(code(() -> files.typed(new PayrollTypedEntryRequest(1L, "2026-09", "plenty", List.of(
                new PayrollTypedEntryRequest.Row(null, null, "RT", "Ledgerwood", "174.00"))), "t")))
                .isEqualTo(PayrollRefusalCode.TYPED_ROWS_INVALID);
    }

    private static PayrollRefusal catching(Runnable call) {
        try {
            call.run();
        } catch (PayrollRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("no refusal");
    }

    // -------------------------------------------------------------------------------- loading again, replacing

    private static final byte[] SEPTEMBER = "EMP NO,SURNAME,INITIALS,AMOUNT\n1,Testerson,BK,174.00\n2,Ledgerwood,R,174.00\n"
            .getBytes(StandardCharsets.UTF_8);

    private static PayrollFileState state(long id, PayrollFileStatus status) {
        return new PayrollFileState(id, 1L, LocalDate.of(2026, 9, 1), status, null, new BigDecimal("348.00"),
                new BigDecimal("3.00"), Instant.parse("2026-10-01T08:00:00Z"), "t");
    }

    private static PayrollReads.FileRow fileRow(long id, String status) {
        return new PayrollReads.FileRow(id, new za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerRef(1L,
                "ABSA", "Absa"), "absa", LocalDate.of(2026, 9, 1), "absa.csv", "UPLOAD", "CSV", SEPTEMBER.length,
                "0".repeat(64), status, "t", Instant.parse("2026-10-01T08:00:00Z"), LocalDate.of(2027, 10, 1), null, 1,
                null, null, null, null, 2, 0, 0, null, 0, 0, 0, new BigDecimal("348.00"), null, null, null,
                new BigDecimal("3.00"), 1, null, null, 1, BigDecimal.ZERO, 0);
    }

    @Test
    @DisplayName("must-pass: the same bytes, live, are answered as loaded already -- nothing read, nothing written")
    void theSameBytesLive() {
        when(fileRepository.sameBytes(any(), any(), any())).thenReturn(Optional.of(state(7L, PayrollFileStatus.RECONCILED)));
        when(reads.file(7L)).thenReturn(Optional.of(fileRow(7L, "RECONCILED")));
        PayrollFiles.Loaded again = files.upload(1L, "2026-09", "absa.csv", SEPTEMBER, true, null, "t");
        assertThat(again.created()).isFalse();
        assertThat(again.summary().alreadyLoaded()).isTrue();
        verify(fileRepository, never()).saveAndFlush(any());
        verify(store, never()).stepAside(anyLong());
    }

    @Test
    @DisplayName("a file replaced, then loaded again with Replace, is the month's file again: compared afresh, the "
            + "other stepped aside")
    void aReplacedFileLoadedAgain() {
        when(fileRepository.sameBytes(any(), any(), any())).thenReturn(Optional.of(state(7L, PayrollFileStatus.SUPERSEDED)));
        when(fileRepository.liveStateOf(any(), any())).thenReturn(Optional.of(state(8L, PayrollFileStatus.RECONCILED)));
        when(store.stepAside(8L)).thenReturn(true);
        when(store.loadAgain(org.mockito.ArgumentMatchers.eq(7L), any(), any(), any(), any(), any())).thenReturn(true);
        when(reads.file(7L)).thenReturn(Optional.of(fileRow(7L, "RECONCILED")));
        PayrollFiles.Loaded loaded = files.upload(1L, "2026-09", "absa.csv", SEPTEMBER, true, null, "t");
        assertThat(loaded.created()).as("loaded, not answered as loaded already").isTrue();
        assertThat(loaded.summary().alreadyLoaded()).isFalse();
        assertThat(loaded.summary().file().id()).isEqualTo(7L);
        verify(store).supersededBy(8L, 7L);
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a file replaced, loaded again without Replace while another is live: FILE_EXISTS_FOR_PERIOD, naming it")
    void aReplacedFileWithoutReplace() {
        when(fileRepository.sameBytes(any(), any(), any())).thenReturn(Optional.of(state(7L, PayrollFileStatus.SUPERSEDED)));
        when(fileRepository.liveStateOf(any(), any())).thenReturn(Optional.of(state(8L, PayrollFileStatus.RECONCILED)));
        when(reads.file(7L)).thenReturn(Optional.of(fileRow(7L, "SUPERSEDED")));
        PayrollRefusal refused = catching(() -> files.upload(1L, "2026-09", "absa.csv", SEPTEMBER, false, null, "t"));
        assertThat(refused.code()).isEqualTo(PayrollRefusalCode.FILE_EXISTS_FOR_PERIOD);
        assertThat(refused.details()).containsEntry("fileId", "8");
        verify(store, never()).stepAside(anyLong());
        verify(store, never()).loadAgain(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("the live file replaced by someone else meanwhile: FILE_EXISTS_FOR_PERIOD naming the file live now, "
            + "nothing loaded")
    void theLiveFileMovedOnMeanwhile() {
        when(fileRepository.liveStateOf(any(), any())).thenReturn(Optional.of(state(8L, PayrollFileStatus.RECONCILED)))
                .thenReturn(Optional.of(state(10L, PayrollFileStatus.RECONCILED)));
        when(store.stepAside(8L)).thenReturn(false);
        PayrollRefusal refused = catching(() -> files.upload(1L, "2026-09", "absa.csv", SEPTEMBER, true, null, "t"));
        assertThat(refused.code()).isEqualTo(PayrollRefusalCode.FILE_EXISTS_FOR_PERIOD);
        assertThat(refused.details()).containsEntry("fileId", "10");
        verify(fileRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("replacing never saves the live file as read before the comparison: one compared at the same moment "
            + "is replaced, never a 500")
    void aReplaceRacingACompare() {
        when(fileRepository.liveStateOf(any(), any())).thenReturn(Optional.of(state(8L, PayrollFileStatus.RECONCILED)));
        when(store.stepAside(8L)).thenReturn(true);
        when(fileRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile saved = invocation.getArgument(0);
            if (saved.getStatus() != za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus.RECONCILED) {
                // The comparison moved the row's version on since it was read: merging the copy read before is refused.
                throw new org.springframework.orm.ObjectOptimisticLockingFailureException(
                        za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile.class, saved.getId());
            }
            saved.setId(9L);
            return saved;
        });
        when(reads.file(9L)).thenReturn(Optional.of(fileRow(9L, "RECONCILED")));
        PayrollFiles.Loaded replaced = files.upload(1L, "2026-09", "absa.csv", SEPTEMBER, true, null, "t");
        assertThat(replaced.created()).isTrue();
        assertThat(replaced.summary().file().id()).isEqualTo(9L);
        verify(store).supersededBy(8L, 9L);
        verify(fileRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("a query raised for a file, or a file's queries listed, read the file's state -- never its bytes")
    void aStatusReadLeavesTheBytes() {
        when(fileRepository.stateOf(5L)).thenReturn(Optional.of(state(5L, PayrollFileStatus.SUPERSEDED)));
        assertThat(code(() -> queries.raise(5L, "t"))).isEqualTo(PayrollRefusalCode.FILE_SUPERSEDED);
        when(fileRepository.existsById(6L)).thenReturn(false);
        assertThat(code(() -> queries.ofFile(6L))).isEqualTo(PayrollRefusalCode.FILE_NOT_FOUND);
        verify(fileRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------------------------------------- employers

    @Test
    @DisplayName("an employer's settings: on the version read, a fee from 0 to 99.99, a mapping of real fields with an amount")
    void settingsChecks() {
        assertThat(code(() -> employers.update(9, new PayrollSettingsRequest("3.00", null, 0))))
                .isEqualTo(PayrollRefusalCode.EMPLOYER_NOT_FOUND);
        assertThat(code(() -> employers.update(1, new PayrollSettingsRequest("3.00", null, 4))))
                .isEqualTo(PayrollRefusalCode.SETTINGS_CHANGED_SINCE);
        assertThat(code(() -> employers.update(1, new PayrollSettingsRequest("120", null, 0))))
                .isEqualTo(PayrollRefusalCode.SETTINGS_INVALID);
        assertThat(code(() -> employers.update(1, new PayrollSettingsRequest("3.00", Map.of("bogus", "X"), 0))))
                .isEqualTo(PayrollRefusalCode.SETTINGS_INVALID);
        assertThat(code(() -> employers.update(1, new PayrollSettingsRequest("3.00", Map.of("staffNumber", "EMP NO"), 0))))
                .isEqualTo(PayrollRefusalCode.SETTINGS_INVALID);
        verify(employerRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the vocabulary: thirteen kinds with labels and outcomes, the system's outcomes marked, no NONE among the rules")
    void theVocabulary() {
        PayrollVocabularyDto vocabulary = employers.vocabulary();
        assertThat(vocabulary.discrepancyTypes()).hasSize(13).allSatisfy(t -> {
            assertThat(t.label()).isNotBlank();
            assertThat(t.outcomes()).contains("OTHER");
        });
        assertThat(vocabulary.discrepancyTypes()).filteredOn(t -> t.type().equals("UNREADABLE_ROW"))
                .singleElement().satisfies(t -> assertThat(t.rule()).isNull());
        assertThat(vocabulary.outcomes()).filteredOn(PayrollVocabularyDto.Outcome::systemOnly)
                .extracting(PayrollVocabularyDto.Outcome::outcome).containsExactly("NO_LONGER_APPLIES", "SUPERSEDED");
        assertThat(vocabulary.matchRules()).containsExactly("STAFF_NUMBER", "ID_NUMBER", "NAME");
        assertThat(vocabulary.limits().maxBytes()).isEqualTo(10L * 1024 * 1024);
    }

    // ------------------------------------------------------------------------------ discrepancies, queries, money

    @Test
    @DisplayName("a note is 1 to 1,000 characters; an unknown filter or discrepancy is refused by code")
    void discrepancyChecks() {
        assertThat(code(() -> discrepancies.addNote(1, "   ", "t"))).isEqualTo(PayrollRefusalCode.NOTE_INVALID);
        assertThat(code(() -> discrepancies.addNote(1, "x".repeat(1001), "t"))).isEqualTo(PayrollRefusalCode.NOTE_INVALID);
        assertThat(code(() -> discrepancies.list(null, null, null, null, "SOMEDAY", null, null)))
                .isEqualTo(PayrollRefusalCode.FILTER_UNKNOWN);
        when(reads.discrepancy(anyLong())).thenReturn(Optional.empty());
        assertThat(code(() -> discrepancies.get(5))).isEqualTo(PayrollRefusalCode.DISCREPANCY_NOT_FOUND);
        when(discrepancyRepository.findById(5L)).thenReturn(Optional.empty());
        assertThat(code(() -> discrepancies.addNote(5, "A note.", "t"))).isEqualTo(PayrollRefusalCode.DISCREPANCY_NOT_FOUND);
    }

    @Test
    @DisplayName("the employer's list is produced only as xlsx or pdf, for a query on record; a file not on record is refused")
    void queryChecks() {
        assertThat(code(() -> queries.produce(2, "docx", "t"))).isEqualTo(PayrollRefusalCode.REPORT_FORMAT_UNKNOWN);
        when(reads.query(anyLong())).thenReturn(Optional.empty());
        assertThat(code(() -> queries.produce(2, "pdf", "t"))).isEqualTo(PayrollRefusalCode.QUERY_NOT_FOUND);
        when(queryFileRepository.findById(9L)).thenReturn(Optional.empty());
        assertThat(code(() -> queries.fileContent(9, "t"))).isEqualTo(PayrollRefusalCode.QUERY_FILE_NOT_FOUND);
        za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQueryFile purged =
                new za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQueryFile();
        purged.setFileId(3L);
        when(queryFileRepository.findById(10L)).thenReturn(Optional.of(purged));
        assertThat(code(() -> queries.fileContent(10, "t"))).isEqualTo(PayrollRefusalCode.FILE_PURGED);
        assertThat(new PayrollQueries.Produced(null, "K7MW-Q3ZR-T8XH-N4CP").toString()).doesNotContain("K7MW");
        assertThat(PayrollReports.format(null)).isEqualTo(PayrollReports.Format.XLSX);
        assertThat(PayrollReports.format("PDF")).isEqualTo(PayrollReports.Format.PDF);
    }

    @Test
    @DisplayName("a payment is an amount above nought on a day that has come; the same one again is answered, not recorded")
    void paymentChecks() {
        assertThat(code(() -> payments.record(new PayrollPaymentRequest(1L, "2026-09", "0.00", "2026-09-30", null), "t")))
                .isEqualTo(PayrollRefusalCode.PAYMENT_INVALID);
        assertThat(code(() -> payments.record(new PayrollPaymentRequest(1L, "2026-09", "100.00", "2026-12-01", null), "t")))
                .isEqualTo(PayrollRefusalCode.PAYMENT_INVALID);
        PayrollPayment existing = new PayrollPayment();
        existing.setId(3L);
        existing.setPayrollEmployerId(1L);
        existing.setPeriod(LocalDate.of(2026, 9, 1));
        existing.setAmount(new BigDecimal("100.00"));
        existing.setReceivedOn(LocalDate.of(2026, 9, 30));
        existing.setRecordedBy("t");
        existing.setRecordedAt(Instant.parse("2026-10-01T08:00:00Z"));
        when(paymentRepository.findSame(any(), any(), any(), any(), any())).thenReturn(List.of(existing));
        assertThat(payments.record(new PayrollPaymentRequest(1L, "2026-09", "100.00", "2026-09-30", null), "t")
                .alreadyRecorded()).isTrue();
        verify(paymentRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------------------------- the record, the year

    @Test
    @DisplayName("an entry of the record carries the kind, the actor and a summary capped at 500 characters")
    void theRecord() {
        audit.record(PayrollEventKind.FILE_LOADED, 7L, null, "x".repeat(600), "system");
        ArgumentCaptor<PayrollEvent> saved = ArgumentCaptor.forClass(PayrollEvent.class);
        verify(events).save(saved.capture());
        assertThat(saved.getValue().getSummary()).hasSize(500);
        assertThat(saved.getValue().getKind()).isEqualTo(PayrollEventKind.FILE_LOADED);
        assertThat(saved.getValue().getOccurredAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    @DisplayName("Stephne, PAY-39: the year's deletion takes each file due in its own transaction; one that fails leaves the rest")
    void theYearsDeletion() {
        when(fileRepository.idsToPurge(any(), any())).thenReturn(List.of(7L, 8L)).thenReturn(List.of(8L))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("a damaged file")).when(store).purge(org.mockito.ArgumentMatchers.eq(8L), any());
        when(store.purge(org.mockito.ArgumentMatchers.eq(7L), any())).thenReturn(25);
        assertThat(retention.purgeDue()).isEqualTo(1);
        verify(store, times(1)).purge(org.mockito.ArgumentMatchers.eq(7L), any());
        verify(events, times(1)).save(any());
    }

    @Test
    @DisplayName("the rate for a month is the latest dated rate on or before it -- and none before the first")
    void theRateForAMonth() {
        var rate = PayrollReconciler.rateFunction(Map.of(YearMonth.of(2026, 1), new BigDecimal("174.00")));
        assertThat(rate.apply(YearMonth.of(2026, 9))).contains(new BigDecimal("174.00"));
        assertThat(rate.apply(YearMonth.of(2025, 12))).isEmpty();
    }
}

package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 🔒 R2-6, D29: a file the bank declines. The submitted run becomes {@code DECLINED} -- terminal, its
 * month and its re-presentations released -- and the month is prepared again as a new run that names
 * the one it replaces, offering the same unpaids. The declined run keeps its lines and its file's hash.
 *
 * <p>The union's identifiers are the file service's test class's, so the two share one context.
 */
@TestPropertySource(properties = {
        "acb.file.user-code=9876",
        "acb.file.user-branch=123456",
        "acb.file.nominated-account=10020030040",
        "acb.file.bank-identifiers=1111122222"
})
@DisplayName("A file the bank declines (D29)")
class AcbDeclineIT extends AbstractAcbIT {

    private static final String OCTOBER = "2026-10";

    /** Synthetic, and the kind of thing an administrator in a hurry types (SD3-D). */
    private static final String BANK_WORDS = "Heritage Day date on 31 lines -- call the Synthetic Desk on 000 000 0000";

    @Autowired
    private AcbRunService runs;
    @Autowired
    private AcbFileService files;

    /** An August unpaid decided present-again, so October re-presents it. Answers the August line's id. */
    private long anAugustUnpaid() {
        long august = insertRun(LocalDate.of(2026, 8, 1), "CLOSED", FROZEN, "system");
        return insertLine(august, AcbRunFixture.FIRST_MEMBER_ID, null, "UNPAID", "RE_PRESENT", new BigDecimal("174.00"));
    }

    /** October prepared, approved, made into a file and recorded as submitted. */
    private AcbRunDto aSubmittedOctober() {
        AcbRunDto prepared = runs.get(runs.prepare(OCTOBER, "system").id());
        jdbc.update("UPDATE acb_run_lines SET account_type = 1 WHERE run_id = ?", prepared.id());
        AcbRunDto approved = runs.approve(prepared.id(), prepared.version(), "approver", null);
        files.render(approved.id(), "approver");
        return runs.recordSubmitted(approved.id(), approved.version(), "cfo", null);
    }

    private Long reDebitOf(long runId, long unpaidLineId) {
        return runs.lines(runId, 0, 200).content().stream()
                .map(AcbRunLineDto::rePresentsLineId).filter(Objects::nonNull)
                .filter(id -> id == unpaidLineId).findFirst().orElse(null);
    }

    @Test
    @DisplayName("🔒 R2-6: declined releases the month and its re-presentations -- October prepares again, re-presents August's unpaid, and names the run it replaces")
    void aDeclinedMonthPreparesAgain() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        long augustUnpaid = anAugustUnpaid();
        AcbRunDto submitted = aSubmittedOctober();
        assertThat(reDebitOf(submitted.id(), augustUnpaid)).as("October re-presents August's unpaid").isEqualTo(augustUnpaid);
        int linesSent = submitted.lineCount();
        String sha = jdbc.queryForObject("SELECT sha256 FROM acb_run_files WHERE run_id = ?", String.class, submitted.id());

        AcbRunDto declined = runs.recordDeclined(submitted.id(), submitted.version(), "cfo",
                AcbDeclineReason.WRONG_DEBIT_DATE, BANK_WORDS);

        assertThat(declined.status()).isEqualTo(AcbRunStatus.DECLINED);
        assertThat(declined.declinedReason()).isEqualTo(AcbDeclineReason.WRONG_DEBIT_DATE);
        assertThat(declined.declinedWords()).isEqualTo(BANK_WORDS);
        assertThat(declined.lineCount()).as("what was sent is kept").isEqualTo(linesSent);
        assertThat(jdbc.queryForObject("SELECT sha256 FROM acb_run_files WHERE run_id = ?", String.class, submitted.id()))
                .isEqualTo(sha);
        assertThat(files.render(submitted.id(), "cfo").sha256()).as("the declined file stays downloadable").isEqualTo(sha);

        AcbRunDto again = runs.get(runs.prepare(OCTOBER, "system").id());
        assertThat(again.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(again.replacesRunId()).isEqualTo(submitted.id());
        assertThat(reDebitOf(again.id(), augustUnpaid)).as("the same unpaid, offered again").isEqualTo(augustUnpaid);
    }

    @Test
    @DisplayName("the must-pass twins: a PREPARED run is not declined (RUN_NOT_IN_STATE), and without a decline the month stays held")
    void onlyASubmittedRunIsDeclined() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto prepared = runs.get(runs.prepare(OCTOBER, "system").id());
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> runs.recordDeclined(prepared.id(), prepared.version(),
                "cfo", AcbDeclineReason.LAYOUT, null)).code()).isEqualTo(AcbRefusalCode.RUN_NOT_IN_STATE);
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> runs.prepare(OCTOBER, "system")).code())
                .as("a live run still holds October").isEqualTo(AcbRefusalCode.RUN_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("🔬 F45: a submitted run with a line already returned is refused RUN_HAS_RETURNS -- and stays SUBMITTED")
    void aRunWithReturnsIsNotDeclined() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto submitted = aSubmittedOctober();
        jdbc.update("UPDATE acb_run_lines SET outcome = 'UNPAID', unpaid_code = '2' WHERE id = "
                + "(SELECT min(id) FROM acb_run_lines WHERE run_id = ?)", submitted.id());

        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, () -> runs.recordDeclined(submitted.id(),
                submitted.version(), "cfo", AcbDeclineReason.OTHER, null));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_HAS_RETURNS);
        assertThat(refused.details()).containsEntry("returnedLines", "1");
        assertThat(runs.get(submitted.id()).status()).isEqualTo(AcbRunStatus.SUBMITTED);
    }

    @Test
    @DisplayName("🔒 SD3-D: the bank's words are kept on the run alone -- never in a log line, never in an audit row")
    void theBanksWordsGoNowhereElse() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto submitted = aSubmittedOctober();
        try (LogCapture service = LogCapture.on(AcbRunServiceImpl.class);
             LogCapture trail = LogCapture.on(AcbAuditTrail.class)) {
            runs.recordDeclined(submitted.id(), submitted.version(), "cfo", AcbDeclineReason.WRONG_DEBIT_DATE, BANK_WORDS);
            assertThat(service.rendered()).doesNotContain("Synthetic Desk").doesNotContain("000 000 0000");
            assertThat(trail.rendered()).doesNotContain("Synthetic Desk");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE reason LIKE '%Synthetic Desk%'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE run_id = ? AND kind = 'RECORD_DECLINED'",
                String.class, submitted.id())).isEqualTo("declined by the bank: WRONG_DEBIT_DATE");
        // And the cap: one character over is refused, never cut.
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> runs.recordDeclined(submitted.id(), 99,
                "cfo", AcbDeclineReason.OTHER, "x".repeat(301))).code()).isEqualTo(AcbRefusalCode.REQUEST_MALFORMED);
    }

    @Test
    @DisplayName("🧪 T2-F: a decline racing the returns -- exactly one wins, and the other is told the run moved")
    void aDeclineRacingTheReturns() throws Exception {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto submitted = aSubmittedOctober();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> outcomes = new ArrayList<>();
        try {
            outcomes.add(pool.submit(() -> attempt(start, () -> runs.recordDeclined(submitted.id(), submitted.version(),
                    "cfo", AcbDeclineReason.LAYOUT, null))));
            outcomes.add(pool.submit(() -> attempt(start, () -> runs.recordReturns(submitted.id(), submitted.version(),
                    "cfo", null))));
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(60, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(AcbRunDto.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(AcbRefusalCode.RUN_VERSION_CONFLICT::equals).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static Object attempt(CountDownLatch start, java.util.function.Supplier<AcbRunDto> write) throws InterruptedException {
        start.await();
        try {
            return write.get();
        } catch (AcbRefusal refused) {
            return refused.code();
        }
    }
}

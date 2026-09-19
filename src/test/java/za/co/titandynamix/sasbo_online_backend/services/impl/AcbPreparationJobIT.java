package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbAuditEvent;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbAuditEventRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The run, end to end on real PostgreSQL: prepared by the service, written by the job, read back.
 *
 * <p>The job runs on the caller's thread in every test lane, so each preparation below has finished
 * -- PREPARED or FAILED -- by the time {@code prepare} returns. Concurrency is proved with a latch,
 * never a sleep: two threads are released at the same instant and exactly one may win.
 */
@DisplayName("Preparing a run, end to end, on PostgreSQL")
class AcbPreparationJobIT extends AbstractAcbIT {

    @Autowired
    private AcbRunService service;
    @Autowired
    private AcbRunRepository runs;
    @Autowired
    private AcbAuditEventRepository auditEvents;

    private static final String OCTOBER = "2026-10";

    @Test
    @DisplayName("a month is prepared: who, on which date, for how much -- and everyone left out, with why")
    void aMonthIsPrepared() {
        AcbRunFixture.Built built = AcbRunFixture.insert(jdbc, 15, 2).assertSize();

        AcbRunDto accepted = service.prepare(OCTOBER, "system");
        assertThat(accepted.status()).as("the 202's body: the run as it was created").isEqualTo(AcbRunStatus.PREPARING);

        AcbRunDto run = service.get(accepted.id());
        assertThat(run.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(run.lineCount()).isEqualTo(built.debitableMembers());
        assertThat(run.totalAmount()).isEqualByComparingTo(new BigDecimal("174.00").multiply(BigDecimal.valueOf(built.debitableMembers())));
        assertThat(run.totalsAgree()).isTrue();
        assertThat(run.rateAmount()).isEqualByComparingTo("174.00");
        assertThat(run.rateEffectiveFrom()).isEqualTo("2026-01-01");
        assertThat(run.holidaySet()).isEqualTo("weekends-only");
        assertThat(run.holidaysKnown()).isFalse();

        // The five header dates: the first and last action dates, and SASBO's three-and-ten rule.
        LocalDate first = LocalDate.parse(run.firstDueDate());
        LocalDate last = LocalDate.parse(run.lastDueDate());
        assertThat(first.getDayOfMonth()).isGreaterThanOrEqualTo(20);
        assertThat(LocalDate.parse(run.creationDate())).isEqualTo(first.minusDays(3));
        assertThat(LocalDate.parse(run.purgeDate())).isEqualTo(last.plusDays(10));

        // Everyone left out, with the reason: four named, three counted by the value that excluded them.
        assertThat(run.exclusions()).extracting(AcbExclusionSummaryDto::reason, AcbExclusionSummaryDto::listed,
                        AcbExclusionSummaryDto::detail, AcbExclusionSummaryDto::memberCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.NOT_DEBIT_ORDER, false, "Payroll Deduction", 2),
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.STATUS_NOT_ACTIVE, false, "Suspended", 1),
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.NO_BANK_ON_RECORD, true, null, 1),
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.NO_SUBSCRIPTION_ROW, true, null, 1),
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.DAY_OUT_OF_WINDOW, true, null, 1),
                        org.assertj.core.groups.Tuple.tuple(AcbExclusionReason.ACCOUNT_SHAPE_INVALID, true, null, 1));
        assertThat(service.exclusions(run.id(), 0, 50).totalElements()).isEqualTo(built.excludedByName());

        // Every line in the window, in the month, at the rate, and masked on the sheet.
        List<AcbRunLineDto> lines = service.lines(run.id(), 0, 200).content();
        assertThat(lines).hasSize(built.debitableMembers()).allSatisfy(line -> {
            LocalDate date = LocalDate.parse(line.actionDate());
            assertThat(date.getMonthValue()).isEqualTo(10);
            assertThat(date.getDayOfMonth()).isGreaterThanOrEqualTo(20);
            assertThat(line.amount()).isEqualByComparingTo("174.00");
            assertThat(line.accountLastFour()).hasSize(4);
        });

        // The trail: started, then completed -- each written with the change it records.
        assertThat(auditEvents.findByRunIdOrderByOccurredAtAscIdAsc(run.id()))
                .extracting(AcbAuditEvent::getKind, AcbAuditEvent::getToState)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("PREPARE", "PREPARING"),
                        org.assertj.core.groups.Tuple.tuple("COMPLETE_PREPARATION", "PREPARED"));
    }

    @Test
    @DisplayName("a bank with two ACTIVE subscription rows refuses the run, and names the bank -- no run is made")
    void twoActiveRowsRefuseTheRun() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        secondSubscriptionRow(AcbRunFixture.FIRST_BANK_ID, "ACTIVE");

        AcbRefusal refused = refusal(() -> service.prepare(OCTOBER, "system"));

        assertThat(refused.code()).isEqualTo(AcbRefusalCode.BANK_SUBSCRIPTION_AMBIGUOUS);
        assertThat(refused.details()).containsEntry("banks", AcbRunFixture.bankName(0));
        assertThat(runs.count()).as("refused before a run exists").isZero();
    }

    @Test
    @DisplayName("must-pass twin: one ACTIVE row beside one CANCELLED row is not ambiguous -- the bank is collected")
    void oneActiveBesideOneCancelledIsCollected() {
        AcbRunFixture.Built built = AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        secondSubscriptionRow(AcbRunFixture.FIRST_BANK_ID, "CANCELLED");

        AcbRunDto run = service.get(service.prepare(OCTOBER, "system").id());

        assertThat(run.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(run.lineCount()).isEqualTo(built.debitableMembers());
        assertThat(service.lines(run.id(), 0, 200).content()).extracting(AcbRunLineDto::bankName)
                .contains(AcbRunFixture.bankName(0));
    }

    /** A second subscription row for one fixture bank, in {@code status}. */
    private void secondSubscriptionRow(int bankId, String status) {
        jdbc.update("INSERT INTO subscriptions (id, bank_id, acb_debit_day, subscription_amount, core_banking, "
                        + "status, registration_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
                5000L, bankId, 25, new BigDecimal("174.00"), false, status, AcbRunFixture.CREATED);
    }

    @Test
    @DisplayName("preparing the month again is refused while its run is live, and says which run and in what state")
    void aSecondPreparationIsRefused() {
        AcbRunFixture.insert(jdbc, 15, 2);
        AcbRunDto run = service.prepare(OCTOBER, "system");

        AcbRefusal refused = refusal(() -> service.prepare(OCTOBER, "system"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_ALREADY_EXISTS);
        assertThat(refused.details()).containsEntry("runId", String.valueOf(run.id()))
                .containsEntry("status", "PREPARED");
        assertThat(runs.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("every live state holds its month; only FAILED and CANCELLED release it")
    void everyLiveStateHoldsTheMonth() {
        AcbRunFixture.insert(jdbc, 15, 2);
        for (AcbRunStatus status : AcbRunStatus.values()) {
            DatabaseResetBetween.clearRuns(jdbc);
            insertRun(NEXT_MONTH, status.name(), FROZEN, "system");
            if (status.holdsItsMonth()) {
                assertThat(refusal(() -> service.prepare(OCTOBER, "system")).code())
                        .as("a %s run holds the month", status).isEqualTo(AcbRefusalCode.RUN_ALREADY_EXISTS);
            } else {
                assertThat(service.prepare(OCTOBER, "system").status())
                        .as("a %s run releases the month", status).isEqualTo(AcbRunStatus.PREPARING);
            }
        }
    }

    @Test
    @DisplayName("a cancelled run releases its month, and stays on record, cancelled")
    void aCancelledRunReleasesItsMonth() {
        AcbRunFixture.insert(jdbc, 15, 2);
        AcbRunDto first = service.get(service.prepare(OCTOBER, "system").id());

        AcbRunDto cancelled = service.cancel(first.id(), first.version(), "system", "prepared by mistake");
        AcbRunDto second = service.get(service.prepare(OCTOBER, "system").id());

        assertThat(cancelled.status()).isEqualTo(AcbRunStatus.CANCELLED);
        assertThat(second.status()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(runs.findById(first.id())).map(AcbRun::getStatus).contains(AcbRunStatus.CANCELLED);
    }

    @Test
    @DisplayName("a preparation killed mid-way is swept to FAILED on the next read, audited, and its month released")
    void aKilledPreparationIsSwept() {
        AcbRunFixture.insert(jdbc, 15, 2);
        // What a redeploy leaves behind: PREPARING, no lines, and a heartbeat ten minutes old.
        long killed = insertRun(NEXT_MONTH, "PREPARING", FROZEN.minusSeconds(600), "system");

        service.list(0, 50);

        AcbRun swept = runs.findById(killed).orElseThrow();
        assertThat(swept.getStatus()).isEqualTo(AcbRunStatus.FAILED);
        assertThat(swept.getFailureReason()).startsWith("interrupted");
        assertThat(auditEvents.findByRunIdOrderByOccurredAtAscIdAsc(killed))
                .extracting(AcbAuditEvent::getKind).containsExactly("SWEPT");
        // The month is free again.
        assertThat(service.get(service.prepare(OCTOBER, "system").id()).status()).isEqualTo(AcbRunStatus.PREPARED);
    }

    @Test
    @DisplayName("a preparation whose heartbeat is fresh is NOT swept -- the twin that proves the sweep can tell")
    void aLivePreparationIsNotSwept() {
        long alive = insertRun(NEXT_MONTH, "PREPARING", FROZEN, "system");
        assertThat(service.sweepInterrupted()).isZero();
        assertThat(runs.findById(alive)).map(AcbRun::getStatus).contains(AcbRunStatus.PREPARING);
    }

    @Test
    @DisplayName("two approvals of one run at the same instant: exactly one wins, the other is told why")
    void twoConcurrentApprovals() throws Exception {
        AcbRunFixture.insert(jdbc, 15, 2);
        AcbRunDto run = service.get(service.prepare(OCTOBER, "system").id());

        List<Object> outcomes = race(
                () -> service.approve(run.id(), run.version(), "approver-a", null),
                () -> service.approve(run.id(), run.version(), "approver-b", null));

        assertThat(outcomes).filteredOn(o -> o instanceof AcbRunDto).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof AcbRefusal)
                .singleElement().satisfies(o -> assertThat(((AcbRefusal) o).code())
                        .isEqualTo(AcbRefusalCode.RUN_VERSION_CONFLICT));
        assertThat(runs.findById(run.id())).map(AcbRun::getStatus).contains(AcbRunStatus.APPROVED);
    }

    @Test
    @DisplayName("two preparations of one month at the same instant: one run, and one RUN_ALREADY_EXISTS")
    void twoConcurrentPreparations() throws Exception {
        AcbRunFixture.insert(jdbc, 15, 2);

        List<Object> outcomes = race(
                () -> service.prepare(OCTOBER, "system"),
                () -> service.prepare(OCTOBER, "system"));

        assertThat(outcomes).filteredOn(o -> o instanceof AcbRunDto).hasSize(1);
        assertThat(outcomes).filteredOn(o -> o instanceof AcbRefusal)
                .singleElement().satisfies(o -> assertThat(((AcbRefusal) o).code())
                        .isEqualTo(AcbRefusalCode.RUN_ALREADY_EXISTS));
        assertThat(runs.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("an unpaid chosen to present again is a second line next month -- and cancelling gives it back")
    void aRePresentationIsASecondLine() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int member = AcbRunFixture.FIRST_MEMBER_ID;
        long september = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long unpaid = insertLine(september, member, null, "UNPAID", "RE_PRESENT", new BigDecimal("170.00"));

        AcbRunDto october = service.get(service.prepare(OCTOBER, "system").id());
        List<AcbRunLineDto> theirs = linesOf(october.id(), member);
        assertThat(theirs).hasSize(2);
        assertThat(theirs).filteredOn(l -> l.rePresentsLineId() == null).singleElement()
                .satisfies(l -> assertThat(l.amount()).isEqualByComparingTo("174.00"));
        assertThat(theirs).filteredOn(l -> l.rePresentsLineId() != null).singleElement()
                .satisfies(l -> {
                    assertThat(l.rePresentsLineId()).isEqualTo(unpaid);
                    assertThat(l.amount()).as("the amount of the line it re-presents").isEqualByComparingTo("170.00");
                });

        // Cancel October and prepare it again: the unpaid is offered again, not stranded.
        service.cancel(october.id(), october.version(), "system", "again");
        AcbRunDto again = service.get(service.prepare(OCTOBER, "system").id());
        assertThat(linesOf(again.id(), member)).filteredOn(l -> l.rePresentsLineId() != null).hasSize(1);
    }

    // ------------------------------------------------------------------------------------------

    private List<AcbRunLineDto> linesOf(long runId, int memberId) {
        return service.lines(runId, 0, 200).content().stream().filter(l -> l.memberId() == memberId).toList();
    }

    /** Releases both calls at once and collects what each returned or threw. */
    private static List<Object> race(Callable<Object> first, Callable<Object> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> call : List.of(first, second)) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        return call.call();
                    } catch (AcbRefusal refusal) {
                        return refusal;
                    }
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).as("both threads ready").isTrue();
            go.countDown();
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        return fail("expected an AcbRefusal and the call returned normally");
    }

    /** Clears the run tables between iterations of one test, leaving the fixture's members. */
    private static final class DatabaseResetBetween {
        static void clearRuns(org.springframework.jdbc.core.JdbcTemplate jdbc) {
            jdbc.execute("TRUNCATE TABLE acb_audit_events, acb_run_exclusion_counts, acb_run_exclusions, "
                    + "acb_run_lines, acb_runs RESTART IDENTITY CASCADE");
        }
    }
}

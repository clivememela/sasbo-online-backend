package za.co.titandynamix.sasbo_online_backend.services.impl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.BankRow;
import za.co.titandynamix.sasbo_online_backend.acb.AcbCalendars;
import za.co.titandynamix.sasbo_online_backend.acb.MemberDebitRow;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.repository.AcbAuditEventRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the preparation job writes to the log, over a run whose member carries a synthetic account
 * number: the run's id and its counts, and on failure the exception's class and stack -- never the
 * account, and never a driver's message, which is where a failed insert prints its bound values.
 *
 * <p>{@code @Isolated} for the reason {@code PiiInLogsTest} gives at length: Logback is
 * process-global and Spring Boot resets it whenever a context starts, so a capture running beside a
 * booting slice can come back empty and pass having asserted nothing. The canary proves it did not.
 */
@Isolated
@DisplayName("AcbPreparationJob -- what it logs")
class AcbPreparationJobLogsTest {

    private static final String SYNTHETIC_ACCOUNT = "6200999911";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC);

    private LogCapture captured;
    private AcbRunRepository runs;
    private AcbPopulationRepository population;
    private AcbRun run;
    private AcbPreparationJob job;

    @BeforeEach
    void wire() {
        captured = LogCapture.on(AcbPreparationJob.class);

        runs = mock(AcbRunRepository.class);
        AcbRunLineRepository lines = mock(AcbRunLineRepository.class);
        AcbRateRepository rates = mock(AcbRateRepository.class);
        population = mock(AcbPopulationRepository.class);

        run = new AcbRun();
        run.setId(1L);
        run.setVersion(0);
        run.setActionMonth(LocalDate.of(2026, 10, 1));
        run.setStatus(AcbRunStatus.PREPARING);
        run.setSetupDate(LocalDate.of(2026, 9, 18));
        run.setHolidaySet("weekends-only");
        run.setLineCount(0);
        run.setTotalAmount(new BigDecimal("0.00"));
        run.setCreatedBy("system");
        run.setStartedAt(CLOCK.instant());
        run.setHeartbeatAt(CLOCK.instant());
        when(runs.findById(1L)).thenReturn(Optional.of(run));
        when(runs.saveAndFlush(any(AcbRun.class))).thenAnswer(call -> call.getArgument(0));

        AcbRate rate = new AcbRate();
        rate.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        rate.setAmount(new BigDecimal("174.00"));
        when(rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any())).thenReturn(Optional.of(rate));
        when(population.findBanks()).thenReturn(List.of(new BankRow(1, "Alpha Bank", "632005")));
        when(population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.ACTIVE)).thenReturn(List.of(1));
        when(lines.findRePresentationCandidates(any(), any(), any(), any())).thenReturn(List.of());

        job = new AcbPreparationJob(runs, lines, rates, population,
                new AcbAuditTrail(mock(AcbAuditEventRepository.class), CLOCK),
                () -> AcbCalendars.ZA_STATUTORY, CLOCK, mock(PlatformTransactionManager.class),
                mock(JdbcTemplate.class), new SimpleMeterRegistry());
    }

    @AfterEach
    void release() {
        captured.close();
    }

    @Test
    @DisplayName("a preparation logs the run's id and its counts -- the member's account is nowhere")
    void aPreparationLogsNoAccount() {
        when(population.findPageAfter(eq(AcbPreparationJob.BEFORE_FIRST_MEMBER), any())).thenReturn(List.of(new MemberDebitRow(
                7, "M-200001", "Botha", "AB", "Active", "Debit Order", "Alpha Bank", SYNTHETIC_ACCOUNT, 25)));

        job.prepare(1L);

        assertThat(run.getStatus()).isEqualTo(AcbRunStatus.PREPARED);
        assertThat(run.getLineCount()).isEqualTo(1);
        String logged = captured.rendered();
        assertThat(logged).contains("ACB run 1: prepared 1 lines");
        assertThat(logged).doesNotContain(SYNTHETIC_ACCOUNT);
    }

    @Test
    @DisplayName("a failure is logged by class and stack, and the run's reason carries no account either")
    void aFailureIsLoggedScrubbed() {
        when(population.findPageAfter(eq(AcbPreparationJob.BEFORE_FIRST_MEMBER), any())).thenThrow(new DataIntegrityViolationException(
                "Batch entry 0 INSERT INTO acb_run_lines ... VALUES (1, 7, '" + SYNTHETIC_ACCOUNT + "') was aborted"));

        job.prepare(1L);

        assertThat(run.getStatus()).isEqualTo(AcbRunStatus.FAILED);
        assertThat(run.getFailureReason()).contains("DataIntegrityViolationException")
                .doesNotContain(SYNTHETIC_ACCOUNT);
        String logged = captured.rendered();
        assertThat(logged).contains("ACB run 1: preparation failed")
                .contains(DataIntegrityViolationException.class.getName());
        assertThat(logged).doesNotContain(SYNTHETIC_ACCOUNT);
    }

    @Test
    @DisplayName("the capture works -- otherwise the assertions above are vacuous")
    void theCaptureCaptures() {
        captured.emitCanary("canary for the ACB job's log capture");
        assertThat(captured.rendered()).contains("canary for the ACB job's log capture");
    }
}

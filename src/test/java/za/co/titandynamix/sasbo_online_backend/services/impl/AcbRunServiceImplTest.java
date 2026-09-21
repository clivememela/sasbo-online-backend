package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher;
import za.co.titandynamix.sasbo_online_backend.acb.BankRow;
import za.co.titandynamix.sasbo_online_backend.acb.AcbCalendars;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendar;
import za.co.titandynamix.sasbo_online_backend.acb.LineTotals;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbAuditEvent;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbAuditEventRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbPopulationRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunExclusionCountRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunExclusionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The run service's own rules, with the database mocked away: what may be prepared, what is
 * refused and with which code, and what a transition checks before it moves a run.
 *
 * <p>What the DATABASE decides -- the partial unique indexes, a lost race, the real transaction --
 * is the integration lane's, in {@code AcbPreparationJobIT}. What is here is the branching the
 * service owns, and each refusal is asserted by its CODE, never by its message.
 */
@DisplayName("AcbRunServiceImpl -- the service's own rules")
class AcbRunServiceImplTest {

    /** 10:00 on 18 September 2026 in Johannesburg: this month is 2026-09, next is 2026-10. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), ZoneOffset.UTC);

    private AcbRunRepository runs;
    private AcbRunLineRepository lines;
    private AcbRunExclusionRepository exclusions;
    private AcbRunExclusionCountRepository exclusionCounts;
    private AcbRateRepository rates;
    private AcbPopulationRepository population;
    private AcbAuditEventRepository auditEvents;
    private AcbPreparationJob job;
    private AcbRunServiceImpl service;
    /** What the calendar table holds, as the service reads it -- V47's, unless a test moves it. */
    private HolidayCalendar calendar = AcbCalendars.ZA_STATUTORY;

    @BeforeEach
    void wire() {
        runs = mock(AcbRunRepository.class);
        // A transition reads its run locked (T2-F); in this unit a locked read is the same read.
        when(runs.findByIdForUpdate(anyLong())).thenAnswer(read -> runs.findById(read.getArgument(0)));
        lines = mock(AcbRunLineRepository.class);
        exclusions = mock(AcbRunExclusionRepository.class);
        exclusionCounts = mock(AcbRunExclusionCountRepository.class);
        rates = mock(AcbRateRepository.class);
        population = mock(AcbPopulationRepository.class);
        auditEvents = mock(AcbAuditEventRepository.class);
        job = mock(AcbPreparationJob.class);
        // A mocked transaction manager: TransactionTemplate runs the callback and commits nothing.
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        service = new AcbRunServiceImpl(runs, lines, exclusions, exclusionCounts, rates, population,
                new AcbAuditTrail(auditEvents, CLOCK), job, mock(AcbFileService.class), AcbJobLauncher.inline(),
                () -> calendar,
                new SeededMembersGuard(new SasboEnvironment(null), new SeedMemberNumbers(), lines),
                CLOCK, transactions, Duration.ofMinutes(5));

        AcbRate rate = new AcbRate();
        rate.setEffectiveFrom(LocalDate.of(2026, 1, 1));
        rate.setAmount(new BigDecimal("174.00"));
        when(rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                .thenReturn(Optional.of(rate));
        when(population.findBanks()).thenReturn(List.of(new BankRow(1, "Alpha Bank", "632005")));
        when(population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.ACTIVE)).thenReturn(List.of(1));
        when(runs.findLive(any(), any())).thenReturn(Optional.empty());
        when(runs.saveAndFlush(any(AcbRun.class))).thenAnswer(call -> {
            AcbRun run = call.getArgument(0);
            if (run.getId() == null) {
                run.setId(41L);
                run.setVersion(0);
            }
            return run;
        });
        when(lines.totalsFor(anyLong())).thenReturn(new LineTotals(0L, null));
    }

    @Nested
    @DisplayName("preparing a month")
    class Preparing {

        @Test
        @DisplayName("next month is accepted: the run is created PREPARING and its job is started")
        void nextMonthIsPrepared() {
            AcbRunDto created = service.prepare("2026-10", "system");

            assertThat(created.status()).isEqualTo(AcbRunStatus.PREPARING);
            assertThat(created.actionMonth()).isEqualTo("2026-10");
            assertThat(created.setupDate()).as("today, in Johannesburg").isEqualTo("2026-09-18");
            assertThat(created.holidaySet()).as("the table's calendar, D30").isEqualTo("za-statutory");
            assertThat(created.holidaysKnown()).isTrue();
            verify(job).prepare(41L);

            ArgumentCaptor<AcbAuditEvent> audited = ArgumentCaptor.forClass(AcbAuditEvent.class);
            verify(auditEvents).save(audited.capture());
            assertThat(audited.getValue().getToState()).isEqualTo("PREPARING");
            assertThat(audited.getValue().getActor()).isEqualTo("system");
        }

        @Test
        @DisplayName("🔒 a month the holiday calendar does not reach is refused HOLIDAYS_NOT_KNOWN, naming the date -- and no run is made (R1-18)")
        void aMonthBeyondTheCalendar() {
            calendar = HolidayCalendar.of("za-statutory", LocalDate.of(2026, 9, 30), AcbCalendars.ZA_STATUTORY_2026_2027);
            AcbRefusal refused = catchRefusal(() -> service.prepare("2026-10", "system"));
            assertThat(refused.code()).isEqualTo(AcbRefusalCode.HOLIDAYS_NOT_KNOWN);
            assertThat(refused.details()).containsEntry("knownUntil", "2026-09-30").containsEntry("actionMonth", "2026-10");
            verify(runs, never()).saveAndFlush(any(AcbRun.class));
            // The must-pass twin: the last month it does reach prepares.
            assertThat(service.prepare("2026-09", "system").status()).isEqualTo(AcbRunStatus.PREPARING);
        }

        @Test
        @DisplayName("a database with no calendar at all prepares nothing -- never weekends alone")
        void noCalendarAtAll() {
            calendar = HolidayCalendar.of("none", null, List.of());
            assertThat(refusal(() -> service.prepare("2026-10", "system"))).isEqualTo(AcbRefusalCode.HOLIDAYS_NOT_KNOWN);
        }

        @Test
        @DisplayName("this month is accepted too; last month and the month after next are refused")
        void theMonthMustBeThisOrNext() {
            service.prepare("2026-09", "system");
            assertThat(refusal(() -> service.prepare("2026-08", "system")))
                    .isEqualTo(AcbRefusalCode.ACTION_MONTH_INVALID);
            assertThat(refusal(() -> service.prepare("2026-11", "system")))
                    .isEqualTo(AcbRefusalCode.ACTION_MONTH_INVALID);
        }

        @Test
        @DisplayName("a malformed month is refused by code, before anything is read")
        void aMalformedMonth() {
            for (String bad : new String[] {"2026-9", "October", "", "2026-10-01", null}) {
                assertThat(refusal(() -> service.prepare(bad, "system")))
                        .as("'%s'", bad).isEqualTo(AcbRefusalCode.ACTION_MONTH_INVALID);
            }
            verifyNoInteractions(runs);
        }

        @Test
        @DisplayName("no rate in force refuses at once -- no run is created")
        void noRate() {
            when(rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(any()))
                    .thenReturn(Optional.empty());
            assertThat(refusal(() -> service.prepare("2026-10", "system")))
                    .isEqualTo(AcbRefusalCode.RATE_NOT_IN_FORCE);
            verify(runs, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("a bank with two ACTIVE subscription rows refuses the run and is named")
        void anAmbiguousBank() {
            when(population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.ACTIVE)).thenReturn(List.of(1, 1));
            AcbRefusal refused = catchRefusal(() -> service.prepare("2026-10", "system"));
            assertThat(refused.code()).isEqualTo(AcbRefusalCode.BANK_SUBSCRIPTION_AMBIGUOUS);
            assertThat(refused.details()).containsEntry("banks", "Alpha Bank");
            verify(runs, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("a month already held by a live run is refused with that run's state and version")
        void aLiveRunHoldsTheMonth() {
            AcbRun live = run(9L, AcbRunStatus.PREPARED, 3);
            when(runs.findLive(eq(LocalDate.of(2026, 10, 1)), any())).thenReturn(Optional.of(live));
            AcbRefusal refused = catchRefusal(() -> service.prepare("2026-10", "system"));
            assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_ALREADY_EXISTS);
            assertThat(refused.details()).containsEntry("runId", "9")
                    .containsEntry("status", "PREPARED").containsEntry("version", "3");
            verify(job, never()).prepare(anyLong());
        }
    }

    @Nested
    @DisplayName("approving -- built, and refused by the route to every caller until B1")
    class Approving {

        @Test
        @DisplayName("a stale version is a conflict that says what the run is now")
        void aStaleVersion() {
            when(runs.findById(5L)).thenReturn(Optional.of(run(5L, AcbRunStatus.APPROVED, 4)));
            AcbRefusal refused = catchRefusal(() -> service.approve(5L, 3, "approver", null));
            assertThat(refused.code()).isEqualTo(AcbRefusalCode.RUN_VERSION_CONFLICT);
            assertThat(refused.details()).containsEntry("status", "APPROVED").containsEntry("version", "4");
        }

        @Test
        @DisplayName("a run that is not PREPARED cannot be approved")
        void notInState() {
            when(runs.findById(5L)).thenReturn(Optional.of(run(5L, AcbRunStatus.PREPARING, 0)));
            assertThat(refusal(() -> service.approve(5L, 0, "approver", null)))
                    .isEqualTo(AcbRefusalCode.RUN_NOT_IN_STATE);
        }

        @Test
        @DisplayName("an empty run cannot be approved -- counted from the lines, not the stored total")
        void anEmptyRun() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 0);
            run.setLineCount(12);
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            when(lines.totalsFor(5L)).thenReturn(new LineTotals(0L, null));
            assertThat(refusal(() -> service.approve(5L, 0, "approver", null)))
                    .isEqualTo(AcbRefusalCode.RUN_EMPTY);
        }

        @Test
        @DisplayName("🔒 the person who prepared a run may approve it -- the bank checks the file, not a second person here (D14 as amended, Q29)")
        void thePreparerMayApprove() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 0);
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            when(lines.totalsFor(5L)).thenReturn(new LineTotals(3L, new BigDecimal("522.00")));
            service.approve(5L, 0, run.getCreatedBy(), null);
            assertThat(run.getStatus()).isEqualTo(AcbRunStatus.APPROVED);
            assertThat(run.getApprovedBy()).as("who approved is still recorded").isEqualTo("preparer");
        }

        @Test
        @DisplayName("🔒 a run whose month gained a holiday after it was prepared is refused RUN_CALENDAR_CHANGED (SD3-F)")
        void theCalendarMoved() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 0);
            run.setCalendarFingerprint(AcbCalendars.ZA_STATUTORY.fingerprint(YearMonth.of(2026, 10)));
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            when(lines.totalsFor(5L)).thenReturn(new LineTotals(3L, new BigDecimal("522.00")));
            List<LocalDate> declared = new java.util.ArrayList<>(AcbCalendars.ZA_STATUTORY_2026_2027);
            declared.add(LocalDate.of(2026, 10, 28));
            calendar = HolidayCalendar.of("za-statutory", LocalDate.of(2027, 12, 31), declared);
            assertThat(refusal(() -> service.approve(5L, 0, "approver", null)))
                    .isEqualTo(AcbRefusalCode.RUN_CALENDAR_CHANGED);
            assertThat(run.getStatus()).isEqualTo(AcbRunStatus.PREPARED);
        }

        @Test
        @DisplayName("the must-pass twin: a holiday declared in ANOTHER month leaves the run approvable")
        void aHolidayElsewhere() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 0);
            run.setCalendarFingerprint(AcbCalendars.ZA_STATUTORY.fingerprint(YearMonth.of(2026, 10)));
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            when(lines.totalsFor(5L)).thenReturn(new LineTotals(3L, new BigDecimal("522.00")));
            List<LocalDate> declared = new java.util.ArrayList<>(AcbCalendars.ZA_STATUTORY_2026_2027);
            declared.add(LocalDate.of(2026, 11, 4));
            calendar = HolidayCalendar.of("za-statutory", LocalDate.of(2027, 12, 31), declared);
            service.approve(5L, 0, "approver", null);
            assertThat(run.getStatus()).isEqualTo(AcbRunStatus.APPROVED);
        }

        @Test
        @DisplayName("and a different person approves a prepared run with lines, the act audited")
        void approves() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 0);
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            when(lines.totalsFor(5L)).thenReturn(new LineTotals(3L, new BigDecimal("522.00")));
            service.approve(5L, 0, "approver", "checked the totals");
            assertThat(run.getStatus()).isEqualTo(AcbRunStatus.APPROVED);
            assertThat(run.getApprovedBy()).isEqualTo("approver");
            ArgumentCaptor<AcbAuditEvent> audited = ArgumentCaptor.forClass(AcbAuditEvent.class);
            verify(auditEvents).save(audited.capture());
            assertThat(audited.getValue().getFromState()).isEqualTo("PREPARED");
            assertThat(audited.getValue().getToState()).isEqualTo("APPROVED");
            assertThat(audited.getValue().getReason()).isEqualTo("checked the totals");
        }
    }

    @Nested
    @DisplayName("cancelling")
    class Cancelling {

        @Test
        @DisplayName("cancels a prepared run and releases its lines' claims, in the same change")
        void cancelReleases() {
            AcbRun run = run(5L, AcbRunStatus.PREPARED, 2);
            when(runs.findById(5L)).thenReturn(Optional.of(run));
            service.cancel(5L, 2, "system", "wrong month");
            assertThat(run.getStatus()).isEqualTo(AcbRunStatus.CANCELLED);
            verify(lines).releaseRun(5L);
        }

        @Test
        @DisplayName("an unknown run is RUN_NOT_FOUND")
        void unknown() {
            when(runs.findById(99L)).thenReturn(Optional.empty());
            assertThat(refusal(() -> service.cancel(99L, 0, "system", null)))
                    .isEqualTo(AcbRefusalCode.RUN_NOT_FOUND);
        }
    }

    @Test
    @DisplayName("an account number shows its last four digits on the sheet, and nothing when that is all of it")
    void lastFour() {
        assertThat(AcbRunServiceImpl.lastFour("1234567890")).isEqualTo("7890");
        assertThat(AcbRunServiceImpl.lastFour("1234")).isNull();
        assertThat(AcbRunServiceImpl.lastFour(null)).isNull();
    }

    @Test
    @DisplayName("the month parse is strict ISO: YYYY-MM")
    void parseMonth() {
        assertThat(AcbRunServiceImpl.parseMonth(" 2026-10 ")).isEqualTo(YearMonth.of(2026, 10));
    }

    // ------------------------------------------------------------------------------------------

    private static AcbRun run(Long id, AcbRunStatus status, int version) {
        AcbRun run = new AcbRun();
        run.setId(id);
        run.setActionMonth(LocalDate.of(2026, 10, 1));
        run.setStatus(status);
        run.setVersion(version);
        run.setSetupDate(LocalDate.of(2026, 9, 18));
        run.setHolidaySet("weekends-only");
        run.setLineCount(0);
        run.setTotalAmount(new BigDecimal("0.00"));
        run.setCreatedBy("preparer");
        run.setStartedAt(Instant.parse("2026-09-18T08:00:00Z"));
        run.setHeartbeatAt(Instant.parse("2026-09-18T08:00:00Z"));
        return run;
    }

    private static AcbRefusalCode refusal(Runnable call) {
        return catchRefusal(call).code();
    }

    private static AcbRefusal catchRefusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected an AcbRefusal and the call returned normally");
    }

    @Test
    @DisplayName("a refusal that never comes fails the helper -- it is not a silent pass")
    void theHelperIsNotVacuous() {
        assertThatThrownBy(() -> catchRefusal(() -> { })).isInstanceOf(AssertionError.class);
    }
}

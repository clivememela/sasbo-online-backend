package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRatesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 🔒 R1-19, Q43, D25: the yearly rate's way in, on the real database. A rate dated ahead is not in
 * force before its month and is from it; a second row for a month, a month a live run has charged and
 * a month already here are refused by name; two administrators racing get one row. The frozen clock
 * is 18 September 2026: this month is September, next is October.
 */
@DisplayName("The yearly rate (01.9, Q43)")
class AcbRateServiceIT extends AbstractAcbIT {

    @Autowired
    private AcbRateService service;
    @Autowired
    private AcbRateRepository rates;
    @Autowired
    private AcbRunService runs;

    @Test
    @DisplayName("V41's row says what SASBO confirmed: from January")
    void theFirstRowsSource() {
        assertThat(service.rates().rates()).singleElement().satisfies(rate -> {
            assertThat(rate.effectiveFrom()).isEqualTo("2026-01-01");
            assertThat(rate.source()).startsWith("From January -- confirmed by SASBO 2026-09-21");
        });
        assertThat(service.rates().januaryNotice()).as("September is not December").isNull();
    }

    @Test
    @DisplayName("🔒 R1-19: a rate dated ahead is not in force before its month, and is from it -- audited run-less, who and what")
    void aRateDatedAhead() {
        AcbRatesDto after = service.add("2026-11-01", new BigDecimal("185.00"), "Agreed at the synthetic AGM", "admin-a");
        assertThat(after.rates()).extracting(AcbRatesDto.Rate::effectiveFrom).containsExactly("2026-01-01", "2026-11-01");
        assertThat(AcbRuns.rateInForce(rates, YearMonth.of(2026, 10)).getAmount()).isEqualByComparingTo("174.00");
        assertThat(AcbRuns.rateInForce(rates, YearMonth.of(2026, 11)).getAmount()).isEqualByComparingTo("185.00");
        assertThat(jdbc.queryForMap("SELECT actor, reason, run_id FROM acb_audit_events WHERE kind = 'RATE_ADDED'"))
                .containsEntry("actor", "admin-a").containsEntry("reason", "R 185.00 from 2026-11")
                .containsEntry("run_id", null);
    }

    @Test
    @DisplayName("a second row for a month is RATE_ALREADY_SET, a month already here is RATE_NOT_FUTURE -- rows are added, never edited")
    void refusalsByName() {
        service.add("2026-11-01", new BigDecimal("185.00"), "Agreed", "admin-a");
        assertThat(catchThrowableOfType(AcbRefusal.class,
                () -> service.add("2026-11-01", new BigDecimal("190.00"), "Again", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.RATE_ALREADY_SET);
        assertThat(catchThrowableOfType(AcbRefusal.class,
                () -> service.add("2026-09-01", new BigDecimal("190.00"), "This month", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.RATE_NOT_FUTURE);
        assertThat(catchThrowableOfType(AcbRefusal.class,
                () -> service.add("2026-01-01", new BigDecimal("190.00"), "The first row", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.RATE_NOT_FUTURE);
        assertThat(catchThrowableOfType(AcbRefusal.class,
                () -> service.add("2026-12-15", new BigDecimal("190.00"), "Mid-month", "admin-a")).code())
                .isEqualTo(AcbRefusalCode.DATE_INVALID);
        assertThat(jdbc.queryForObject("SELECT amount FROM acb_rates WHERE effective_from = DATE '2026-11-01'",
                BigDecimal.class)).isEqualByComparingTo("185.00");
    }

    @Test
    @DisplayName("🔒 R1-19: a month a live run has already charged is RATE_MONTH_CHARGED, naming the run")
    void aChargedMonthIsRefused() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        AcbRunDto october = runs.prepare("2026-10", "system");
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class,
                () -> service.add("2026-10-01", new BigDecimal("185.00"), "Too late", "admin-a"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RATE_MONTH_CHARGED);
        assertThat(refused.details()).containsEntry("runId", String.valueOf(october.id()));
    }

    @Test
    @DisplayName("🧪 T2-F: two administrators adding one month at once get one row and one RATE_ALREADY_SET -- never a 500")
    void twoAddsRace() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Object>> outcomes = new ArrayList<>();
        try {
            for (String who : List.of("admin-a", "admin-b")) {
                outcomes.add(pool.submit(() -> {
                    start.await();
                    try {
                        return service.add("2026-12-01", new BigDecimal("185.00"), "By " + who, who);
                    } catch (AcbRefusal refused) {
                        return refused.code();
                    }
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> outcome : outcomes) {
                results.add(outcome.get(30, TimeUnit.SECONDS));
            }
            assertThat(results).filteredOn(AcbRatesDto.class::isInstance).hasSize(1);
            assertThat(results).filteredOn(AcbRefusalCode.RATE_ALREADY_SET::equals).hasSize(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_rates WHERE effective_from = DATE '2026-12-01'", Long.class))
                .isEqualTo(1L);
    }
}

package za.co.titandynamix.sasbo_online_backend.services.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import javax.sql.DataSource;
import java.sql.Date;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 R3-17, SD3-K: the termination report does not grow with the ledger. Over a synthetic THREE-YEAR
 * ledger its statements are the same for ten members as for thirty -- the window's thirteen months and
 * the opening position, each one query -- and nothing is read per member or per month.
 *
 * <p>{@code @DataJpaTest} gives this class a fresh embedded database of its own; the ledger goes in as
 * JDBC batches.
 */
@DataJpaTest
@Import({AcbReturnsServiceImpl.class, AcbAuditTrail.class, AcbTerminationReportQueryCountTest.Wiring.class})
@DisplayName("The termination report: statements do not grow with the ledger (R3-17)")
class AcbTerminationReportQueryCountTest {

    @TestConfiguration
    static class Wiring {

        @Bean
        Clock acbClock() {
            return Clock.fixed(Instant.parse("2026-09-18T08:00:00Z"), AcbTime.ZONE);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    // The service's collaborators this report never calls: the member service (03.5's status changes), and the
    // letter sender and its launcher (03.3). Mocked, so the slice holds the report's own statements and nothing else.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.MemberService memberService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AcbLetterSender letterSender;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher launcher;

    /** Thirty-six months: October 2023 to September 2026. */
    private static final YearMonth FIRST = YearMonth.of(2023, 10);
    private static final int MONTHS = 36;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AcbReturnsServiceImpl service;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("the same statements for ten members over three years as for thirty")
    void theCountIsConstant() {
        QueryCount.assertConstantAcrossSizes(entityManagerFactory, this::seedTo,
                () -> service.terminationReport(0, 50), 10, 30);

        // Non-vacuity: the thirty-member ledger does list members, and only from the window.
        AcbTerminationReportDto report = service.terminationReport(0, 50);
        assertThat(report.members().totalElements()).isPositive();
        assertThat(report.members().content()).anySatisfy(c -> assertThat(c.terminate()).isTrue());
        assertThat(report.members().content()).allSatisfy(c -> assertThat(c.unpaidMonths())
                .allSatisfy(month -> assertThat(YearMonth.parse(month)).isAfterOrEqualTo(YearMonth.of(2025, 9))));
    }

    /**
     * Empties the ledger, then writes {@code members} members' debits for thirty-six months. Every fourth
     * member ends on three unpaids in a row (terminate); the rest are unpaid two months in three, so a third
     * of them end on two in a row (listed) -- the report is never asked to list nobody.
     */
    private void seedTo(int members) {
        for (String table : new String[] {"acb_run_lines", "acb_runs"}) {
            jdbc.update("DELETE FROM " + table);
        }
        List<Object[]> runs = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            YearMonth month = FIRST.plusMonths(i);
            runs.add(new Object[] {1000L + i, Date.valueOf(month.atDay(1))});
        }
        jdbc.batchUpdate("INSERT INTO acb_runs (id, action_month, status, setup_date, holiday_set, line_count, "
                + "total_amount, created_by, started_at, heartbeat_at, version) VALUES (?, ?, 'CLOSED', DATE '2026-09-18', "
                + "'za-statutory', 0, 0, 'system', TIMESTAMP '2026-09-18 08:00:00', TIMESTAMP '2026-09-18 08:00:00', 0)", runs);
        List<Object[]> lines = new ArrayList<>();
        long id = 1;
        for (int i = 0; i < MONTHS; i++) {
            YearMonth month = FIRST.plusMonths(i);
            for (int m = 1; m <= members; m++) {
                lines.add(new Object[] {id++, 1000L + i, m, "S-" + m, "Surname" + m, Date.valueOf(month.atDay(25)),
                        (m % 4 == 0 ? i >= MONTHS - 3 : (m + i) % 3 != 0) ? "UNPAID" : "PAID"});
            }
        }
        jdbc.batchUpdate("INSERT INTO acb_run_lines (id, run_id, member_id, re_presents_line_id, run_released, "
                + "membership_number, surname, bank_name, branch_code, account_number, debit_day, action_date, amount, "
                + "outcome) VALUES (?, ?, ?, NULL, FALSE, ?, ?, 'Synthetic Bank', '250000', '6200000000', 25, ?, 174.00, ?)",
                lines);
    }
}

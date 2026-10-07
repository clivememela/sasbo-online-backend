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
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preparing a run costs the same number of statements for a hundred members as for three hundred:
 * nothing in the job queries per member.
 *
 * <h2>The defect it exists to catch</h2>
 * The capture path resolves a member's bank with two queries per member. Reused by a run over the
 * real membership, that is over a hundred thousand statements -- correct, green in every other test,
 * and a preparation that outlives its instance. The count is asserted CONSTANT across two sizes: if
 * it grows with the data, the job queries per row.
 *
 * <h2>Fast lane, and why that is honest here</h2>
 * The claim is about how many statements the job ISSUES, which H2 counts as faithfully as
 * PostgreSQL. {@code @DataJpaTest} gives this class its own fresh embedded database -- nothing else
 * writes to it -- and the fixture goes in as JDBC batches, milliseconds rather than seconds.
 *
 * <p>What it counts is Hibernate's statements. The lines are written as JDBC batches, a handful of
 * round trips per run whatever its size; the reads -- where a per-member lookup would hide -- are
 * all Hibernate's, and all counted. 🔒 That includes the holiday calendar's: the job reads the REAL
 * table-backed source here, V47's rows put in by the fixture, and its one statement is counted with
 * the rest (SD3-L) -- a calendar read per member would make the count grow.
 */
@DataJpaTest
@Import({AcbPreparationJob.class, AcbAuditTrail.class, AcbHolidayCalendarSource.class, AcbUnpaidCounter.class,
        AcbPreparationQueryCountTest.Wiring.class})
@DisplayName("Preparing a run: statements do not grow with the membership")
class AcbPreparationQueryCountTest {

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

    /** Twenty banks, so a hundred members is five a bank and three hundred is fifteen. */
    private static final int BANKS = 20;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private AcbPreparationJob job;

    @Autowired
    private JdbcTemplate jdbc;

    private long runId;

    @Test
    @DisplayName("the same statements for 100 members as for 300")
    void theCountIsConstant() {
        QueryCount.assertConstantAcrossSizes(entityManagerFactory, this::seedTo,
                () -> job.prepare(this.runId), 100, 300);

        // Non-vacuity: the second preparation really did debit three hundred members.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_run_lines WHERE run_id = ?", Integer.class, runId))
                .isEqualTo(300);
        assertThat(jdbc.queryForObject("SELECT status FROM acb_runs WHERE id = ?", String.class, runId))
                .isEqualTo("PREPARED");
        // ...and it read the calendar from the table: the run names it and fingerprints October with it.
        assertThat(jdbc.queryForObject("SELECT holiday_set || ' ' || calendar_fingerprint FROM acb_runs WHERE id = ?",
                String.class, runId)).isEqualTo("za-statutory za-statutory|2026-10|");
    }

    /** Empties everything this test wrote, then builds {@code members} debitable members and a run. */
    private void seedTo(int members) {
        for (String table : new String[] {"acb_audit_events", "acb_run_exclusion_counts", "acb_run_exclusions",
                "acb_run_lines", "acb_runs", "members", "subscriptions", "banks", "acb_rates",
                "acb_holidays", "acb_calendar"}) {
            jdbc.update("DELETE FROM " + table);
        }
        AcbRunFixture.insertRate(jdbc, LocalDate.of(2026, 1, 1), new BigDecimal("174.00"));
        AcbRunFixture.insertCalendar(jdbc);
        AcbRunFixture.insert(jdbc, BANKS, members / BANKS);
        this.runId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) + 1 FROM acb_runs", Long.class) + members;
        jdbc.update("INSERT INTO acb_runs (id, action_month, status, setup_date, holiday_set, line_count, "
                        + "total_amount, created_by, started_at, heartbeat_at, version) "
                        + "VALUES (?, ?, 'PREPARING', ?, 'weekends-only', 0, 0, 'system', ?, ?, 0)",
                runId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 9, 18),
                OffsetDateTime.of(2026, 9, 18, 8, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 9, 18, 8, 0, 0, 0, ZoneOffset.UTC));
    }
}

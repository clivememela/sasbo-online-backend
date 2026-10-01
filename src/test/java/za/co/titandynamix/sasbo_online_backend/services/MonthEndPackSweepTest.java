package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ME-21 at start (month end 04.10, DL-58): a pack a dead instance left PREPARING is swept to FAILED, interrupted, once
 * the application is ready -- before anybody asks for a pack, which would sweep it too. The dead instance is a runner
 * ordered first, which leaves one pack preparing with an old heartbeat and one ready, as a restart mid-job would.
 * A context of its own, on a database of its own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
        "spring.datasource.url=jdbc:h2:mem:month-end-pack-sweep;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@Import(MonthEndPackSweepTest.ADeadInstancesPacks.class)
@DisplayName("Month end's pack sweep, at start")
class MonthEndPackSweepTest {

    @TestConfiguration
    static class ADeadInstancesPacks {
        /** What the previous instance left: August's pack mid-job, its heartbeat a month old, and July's ready. */
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ApplicationRunner aDeadInstancesPacks(JdbcTemplate jdbc) {
            return args -> {
                jdbc.update("INSERT INTO month_end_packs (month_start, status, asked_by, started_at, heartbeat_at) "
                        + "VALUES (DATE '2026-08-01', 'PREPARING', 'system', "
                        + "TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00', "
                        + "TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00')");
                jdbc.update("INSERT INTO month_end_packs (month_start, status, asked_by, started_at, heartbeat_at, "
                        + "finished_at, statistics_file, monthly_file) VALUES (DATE '2026-07-01', 'READY', 'system', "
                        + "TIMESTAMP WITH TIME ZONE '2026-08-01 08:00:00+00:00', "
                        + "TIMESTAMP WITH TIME ZONE '2026-08-01 08:00:00+00:00', "
                        + "TIMESTAMP WITH TIME ZONE '2026-08-01 08:01:00+00:00', X'00', X'00')");
            };
        }
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("🔒 ME-21, DL-58: once the application is ready, a pack a dead instance left preparing is FAILED, interrupted")
    void theSweepAtStart() {
        assertThat(jdbc.queryForMap("SELECT status, failure FROM month_end_packs WHERE month_start = DATE '2026-08-01'"))
                .containsEntry("STATUS", "FAILED")
                .containsEntry("FAILURE", MonthEndPacks.INTERRUPTED);
    }

    @Test
    @DisplayName("must-pass: a pack that finished is left as it was")
    void aReadyPackIsLeftAlone() {
        assertThat(jdbc.queryForObject("SELECT status FROM month_end_packs WHERE month_start = DATE '2026-07-01'",
                String.class)).isEqualTo("READY");
    }
}

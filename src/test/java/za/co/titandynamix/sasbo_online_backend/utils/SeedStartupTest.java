package za.co.titandynamix.sasbo_online_backend.utils;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed, through a whole start -- the real {@link DataSeeder}, as an application runs it (R0-8, R0-9,
 * and R3-15's start-up half).
 *
 * <p>Each context has an H2 database of its OWN name: the fast lane's classes run concurrently and share
 * {@code sasbo-test}, which the other full-context classes have already seeded, and "nothing was seeded"
 * can only be asserted of a database nobody else writes to.
 */
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("The seed, through a whole start")
class SeedStartupTest {

    /**
     * What the migrations would have given the database, and a clock inside the range the seeded history
     * can cover (SD4-D -- no seeder test reads the wall clock).
     *
     * <p>H2 builds the fast lane's schema from the entities with Flyway off, so V41's rate, V45's codes and
     * V47's calendar are not there; a runner ordered FIRST puts them in before {@link DataSeeder} runs, as Flyway does
     * before any runner in every real database. Without them the history seeder would skip, and "it
     * succeeds on H2" (SD3-I) would be a test of the skip.
     */
    @TestConfiguration
    static class MigrationRowsAndAFrozenClock {

        static final Instant FROZEN = Instant.parse("2026-09-18T08:00:00Z");

        @Bean
        @Primary
        Clock frozenClockForTheSeed() {
            return Clock.fixed(FROZEN, AcbTime.ZONE);
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        ApplicationRunner whatFlywayWouldHaveSeeded(JdbcTemplate jdbc) {
            return args -> {
                AcbRunFixture.insertRate(jdbc, LocalDate.of(2026, 1, 1), new BigDecimal("174.00"));
                // With V70's re-debit rules: 2 may be presented again, 4 never (Stephne, DOD-03).
                jdbc.update("INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source, re_debit_allowed) "
                        + "VALUES ('2', 'NOT_PROVIDED_FOR', 'Not Provided For', FALSE, 'V45 and V70, on H2', TRUE), "
                        + "('4', 'PAYMENT_STOPPED', 'Payment Stopped', FALSE, 'V45 and V70, on H2', FALSE)");
                AcbRunFixture.insertCalendar(jdbc);
            };
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "sasbo.environment=production",
            "spring.datasource.url=jdbc:h2:mem:seed-production;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @Import(MigrationRowsAndAFrozenClock.class)
    @DisplayName("🔒 where the production marker is set (SD3-A)")
    class WithTheProductionMarker {

        @Autowired
        private MemberRepository members;

        @Autowired
        private BankRepository banks;

        @Autowired
        private SubscriptionsRepository subscriptions;

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private DataSeeder seeder;

        @Test
        @DisplayName("an empty database -- the real production's first boot -- gets no synthetic member and no ACB history, even on a second start")
        void nobodySyntheticIsSeeded() throws Exception {
            assertThat(members.count()).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_runs", Long.class)).isZero();
            seeder.run(new DefaultApplicationArguments());
            assertThat(members.count()).as("after a second start").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_runs", Long.class)).as("after a second start").isZero();
        }

        @Test
        @DisplayName("the must-pass twin: the reference data -- banks, subscription rows -- is real, and is seeded")
        void referenceDataIsStillSeeded() {
            assertThat(banks.count()).isPositive();
            assertThat(subscriptions.count()).isPositive();
        }

        @Test
        @DisplayName("🔒 EH2-D: a seeded member found on a production database is said at start-up, at WARN")
        void aSeededMemberInProductionIsSaid() throws Exception {
            // The system that started as a demo and became production: one seeded member is on record.
            members.save(DebitOrderMemberSeeder.readSeedFile().members().get(0).toMember());
            try (LogCapture log = LogCapture.on(DataSeeder.class)) {
                seeder.run(new DefaultApplicationArguments());
                assertThat(log.renderedAt(Level.WARN)).contains("1 seeded (synthetic) member(s) are on this PRODUCTION database");
            } finally {
                members.deleteAll();
            }
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties =
            "spring.datasource.url=jdbc:h2:mem:seed-demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
    @Import(MigrationRowsAndAFrozenClock.class)
    @DisplayName("in a demo -- no marker, which is every environment but the real production")
    class InADemo {

        @Autowired
        private MemberRepository members;

        @Autowired
        private JdbcTemplate jdbc;

        @Autowired
        private DataSeeder seeder;

        @Test
        @DisplayName("the whole seed is just there -- the 160 and the debit-order population -- and a second start adds nobody")
        void everythingIsThereOnce() throws Exception {
            long original;
            try (java.io.InputStream stream = new org.springframework.core.io.ClassPathResource("data/members.json")
                    .getInputStream()) {
                original = DataSeeder.readSeedMembers(stream).size();
            }
            // The payroll demonstration's own members are seeded whatever the switch says, as the debit-order ones are.
            long expected = original + DebitOrderMemberSeeder.readSeedFile().members().size()
                    + PayrollSeeder.readSeedFile().members().size();
            assertThat(members.count()).isEqualTo(expected);
            seeder.run(new DefaultApplicationArguments());
            assertThat(members.count()).as("after a second start").isEqualTo(expected);
        }

        @Test
        @DisplayName("🔒 MD53, MV-45: the switch unset is off -- no workplace, nobody placed at one, none of the invented members' past, no taken month")
        void theSwitchUnsetLeavesNothingOfSasbosShape() {
            // The invented members are the count above: the 160 and the debit-order population, and nobody else.
            assertThat(jdbc.queryForObject("SELECT count(*) FROM workplaces", Long.class)).as("no workplace").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM members WHERE workplace_id IS NOT NULL", Long.class))
                    .as("nobody placed at one").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes "
                    + "WHERE reason IN ('DELETION_CODE', 'SUSPENSION_CODE')", Long.class))
                    .as("none of the invented members' past, which is written by SASBO's codes").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class))
                    .as("no taken month").isZero();
        }

        @Test
        @DisplayName("every bank has at most one subscription row, and SASBO's own collecting bank has its row (F54)")
        void oneRowPerBankAndStandardBanksIsThere() {
            Long doubled = jdbc.queryForObject("SELECT count(*) FROM (SELECT bank_id FROM subscriptions "
                    + "WHERE bank_id IS NOT NULL GROUP BY bank_id HAVING count(*) > 1) d", Long.class);
            assertThat(doubled).isZero();
            Long standardBank = jdbc.queryForObject("SELECT count(*) FROM subscriptions s JOIN banks b ON b.id = s.bank_id "
                    + "WHERE lower(b.name) = 'the standard bank of south africa limited' AND s.status = 'ACTIVE'", Long.class);
            assertThat(standardBank).isEqualTo(1L);
            Long orphans = jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE bank_id IS NULL", Long.class);
            assertThat(orphans).isZero();
        }

        @Test
        @DisplayName("🔒 SD3-I: the history seeder SUCCEEDS on H2 -- July closed, August returned, SEED_COMPLETE last -- and a second start seeds no run")
        void theHistoryIsThereOnce() throws Exception {
            assertThat(jdbc.queryForList("SELECT status FROM acb_runs ORDER BY action_month"))
                    .extracting(row -> row.get("status"))
                    .containsExactly("CLOSED", "RETURNED");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL "
                    + "AND kind = 'SEED_COMPLETE'", Long.class)).isEqualTo(1L);
            seeder.run(new DefaultApplicationArguments());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_runs", Long.class)).as("after a second start").isEqualTo(2L);
        }
    }
}

package za.co.titandynamix.sasbo_online_backend.utils;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The seed, through a whole start -- the real {@link DataSeeder}, as an application runs it (R0-8, R0-9).
 *
 * <p>Each context has an H2 database of its OWN name: the fast lane's classes run concurrently and share
 * {@code sasbo-test}, which the other full-context classes have already seeded, and "nothing was seeded"
 * can only be asserted of a database nobody else writes to.
 */
@DisplayName("The seed, through a whole start")
class SeedStartupTest {

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
            "sasbo.environment=production",
            "spring.datasource.url=jdbc:h2:mem:seed-production;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
    @DisplayName("🔒 where the production marker is set (SD3-A)")
    class WithTheProductionMarker {

        @Autowired
        private MemberRepository members;

        @Autowired
        private BankRepository banks;

        @Autowired
        private SubscriptionsRepository subscriptions;

        @Autowired
        private DataSeeder seeder;

        @Test
        @DisplayName("an empty database -- the real production's first boot -- gets no synthetic member, even on a second start")
        void nobodySyntheticIsSeeded() throws Exception {
            assertThat(members.count()).isZero();
            seeder.run(new DefaultApplicationArguments());
            assertThat(members.count()).as("after a second start").isZero();
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
            long expected = original + DebitOrderMemberSeeder.readSeedFile().members().size();
            assertThat(members.count()).isEqualTo(expected);
            seeder.run(new DefaultApplicationArguments());
            assertThat(members.count()).as("after a second start").isEqualTo(expected);
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
    }
}

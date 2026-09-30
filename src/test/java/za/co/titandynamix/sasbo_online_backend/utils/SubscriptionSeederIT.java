package za.co.titandynamix.sasbo_online_backend.utils;

import ch.qos.logback.classic.Level;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The subscription rows are seeded PER ROW, keyed by bank -- and Standard Bank's orphan is given its
 * bank, not doubled (F54, SD4-G, R0-8).
 *
 * <p>The database that matters is one already holding TODAY's fifteen rows, as the by-count seeder
 * wrote them: Standard Bank's with no bank, because the file named it "Standard Bank of South Africa
 * Limited" and the bank on record is "The Standard Bank of South Africa Limited" (register A5's
 * instance). {@link #todaysRows} writes exactly that state. Every expectation is read from the file.
 */
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("SubscriptionSeeder -- per row, keyed by bank, and the orphan repaired narrowly")
class SubscriptionSeederIT extends AbstractWritingIT {

    private static final String STANDARD_BANK = "The Standard Bank of South Africa Limited";

    @Autowired
    private SubscriptionSeeder seeder;

    @Autowired
    private BankSeeder bankSeeder;

    @Autowired
    private SubscriptionsRepository subscriptions;

    @Autowired
    private BankRepository banks;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private List<SubscriptionsDto> file;

    @BeforeEach
    void theBanksAreOnRecord() throws Exception {
        bankSeeder.seed();
        file = SubscriptionSeeder.readSeedFile();
        assertThat(file).as("the subscription seed file was read").isNotEmpty();
    }

    @Test
    @DisplayName("an empty table: one row per bank in the file, Standard Bank's with its bank -- and a second start adds none")
    void anEmptyTableGetsOneRowPerBank() throws Exception {
        SubscriptionSeeder.Outcome first = seeder.seed();
        assertThat(first.inserted()).isEqualTo(file.size());
        assertThat(seeder.seed().inserted()).as("the second start").isZero();
        assertThat(rowsByBank()).allSatisfy((bank, rows) -> assertThat(rows).as(bank).isEqualTo(1));
        assertThat(rowsByBank()).containsEntry(STANDARD_BANK.toLowerCase(Locale.ROOT), 1L);
        assertThat(orphans()).isZero();
    }

    @Test
    @DisplayName("🔴 today's fifteen rows: Standard Bank's orphan is given its bank, nothing is doubled, started twice")
    void todaysDatabaseIsRepairedNotDoubled() throws Exception {
        todaysRows(null);
        assertThat(orphans()).as("the orphan is there first").isEqualTo(1);
        try (LogCapture log = LogCapture.on(SubscriptionSeeder.class)) {
            SubscriptionSeeder.Outcome outcome = seeder.seed();
            assertThat(outcome.repaired()).isEqualTo(1);
            assertThat(outcome.inserted()).isZero();
            assertThat(log.renderedAt(Level.INFO)).contains("had no bank; it is given " + STANDARD_BANK);
        }
        SubscriptionSeeder.Outcome again = seeder.seed();
        assertThat(again.repaired() + again.inserted()).as("the second start").isZero();
        assertThat(subscriptions.count()).isEqualTo(file.size());
        assertThat(orphans()).isZero();
        assertThat(rowsByBank()).containsEntry(STANDARD_BANK.toLowerCase(Locale.ROOT), 1L)
                .allSatisfy((bank, rows) -> assertThat(rows).as(bank).isEqualTo(1));
    }

    @Test
    @DisplayName("🔒 two orphans: neither is changed, and Standard Bank gets no row -- a person decides (SD4-G)")
    void twoOrphansAreLeftAlone() throws Exception {
        todaysRows(null);
        insertRow(null, 15, "174.00", false, "ACTIVE");
        try (LogCapture log = LogCapture.on(SubscriptionSeeder.class)) {
            SubscriptionSeeder.Outcome outcome = seeder.seed();
            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.inserted()).isZero();
            assertThat(log.renderedAt(Level.WARN)).contains("a person decides");
        }
        assertThat(orphans()).isEqualTo(2);
        assertThat(rowsByBank()).doesNotContainKey(STANDARD_BANK.toLowerCase(Locale.ROOT));
    }

    @Test
    @DisplayName("an orphan that matches no row of the file is left alone, with a WARN -- and Standard Bank gets its own row")
    void anUnmatchedOrphanIsLeftAlone() throws Exception {
        todaysRows(20);
        try (LogCapture log = LogCapture.on(SubscriptionSeeder.class)) {
            SubscriptionSeeder.Outcome outcome = seeder.seed();
            assertThat(outcome.repaired()).isZero();
            assertThat(outcome.inserted()).as("Standard Bank's row, since the orphan is not it").isEqualTo(1);
            assertThat(log.renderedAt(Level.WARN)).contains("has no bank").contains("left alone");
        }
        assertThat(orphans()).isEqualTo(1);
        assertThat(rowsByBank()).containsEntry(STANDARD_BANK.toLowerCase(Locale.ROOT), 1L);
    }

    @Test
    @DisplayName("🔒 where the production marker is set the orphan is not repaired -- a person sets its bank")
    void productionLeavesTheOrphan() throws Exception {
        todaysRows(null);
        SubscriptionSeeder inProduction = new SubscriptionSeeder(subscriptions, banks, new SasboEnvironment("production"));
        assertThat(inProduction.seed().repaired()).isZero();
        assertThat(orphans()).isEqualTo(1);
        // The must-pass twin: the same database, no marker.
        assertThat(seeder.seed().repaired()).isEqualTo(1);
    }

    @Test
    @DisplayName("once seeded, a start costs ONE query (SD4-F)")
    void aSeededStartCostsOneQuery() throws Exception {
        seeder.seed();
        long statements = QueryCount.during(entityManagerFactory, () -> {
            try {
                assertThat(seeder.seed().inserted()).isZero();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        assertThat(statements).isEqualTo(1);
    }

    // ------------------------------------------------------------------------------------------

    /**
     * The fifteen rows as the by-count seeder left them: each attached to its bank by name -- except
     * Standard Bank's, which it could not match and wrote with none.
     *
     * @param orphanDay the orphan's debit day, or {@code null} for the file's own (15)
     */
    private void todaysRows(Integer orphanDay) {
        for (SubscriptionsDto dto : file) {
            boolean standardBank = dto.bankName().equalsIgnoreCase(STANDARD_BANK);
            Integer bankId = standardBank ? null : bankId(dto.bankName());
            int day = standardBank && orphanDay != null ? orphanDay : dto.acbDebitDay();
            insertRow(bankId, day, dto.subscriptionAmount().toPlainString(), dto.isCoreBanking(), dto.status().name());
        }
    }

    private void insertRow(Integer bankId, int day, String amount, Boolean core, String status) {
        jdbc.update("INSERT INTO subscriptions (bank_id, acb_debit_day, subscription_amount, core_banking, status, "
                        + "registration_date) VALUES (?, ?, CAST(? AS NUMERIC), ?, ?, ?)",
                bankId, day, amount, core, status, Timestamp.valueOf(LocalDateTime.of(2026, 9, 1, 9, 0)));
    }

    private Integer bankId(String name) {
        return jdbc.queryForObject("SELECT id FROM banks WHERE lower(name) = lower(?)", Integer.class, name);
    }

    private long orphans() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM subscriptions WHERE bank_id IS NULL", Long.class);
        return count == null ? 0 : count;
    }

    private Map<String, Long> rowsByBank() {
        Map<String, Long> byBank = new java.util.TreeMap<>();
        jdbc.query("SELECT lower(b.name) AS bank, count(*) AS n FROM subscriptions s JOIN banks b ON b.id = s.bank_id "
                + "GROUP BY lower(b.name)", rs -> {
                    byBank.put(rs.getString("bank"), rs.getLong("n"));
                });
        return byBank;
    }
}

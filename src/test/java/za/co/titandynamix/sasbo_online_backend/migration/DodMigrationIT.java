package za.co.titandynamix.sasbo_online_backend.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import za.co.titandynamix.sasbo_online_backend.support.AbstractPostgresIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V70 to V72 against the real chain, on a database of their own: V70 is idempotent where a demonstration database
 * already holds some of SASBO's suspension codes -- the reference-list seeder writes 11, 13 and 19, and the
 * demonstration seeders add any code they use -- and leaves every row it finds as it found it (the parent's
 * instruction: test the migration against a database that already has some of the codes).
 */
@DisplayName("V70 to V72 against the real migration chain")
class DodMigrationIT extends AbstractPostgresIT {

    private static final MigrationVersion V70 = MigrationVersion.fromVersion("70");
    private static final MigrationVersion V72 = MigrationVersion.fromVersion("72");
    private static final String PROBE_DATABASE = "v70_dod_probe";

    private JdbcTemplate probe;

    @BeforeEach
    void anEmptyDatabaseOfItsOwn() {
        JdbcTemplate shared = jdbcFor(POSTGRES.getJdbcUrl());
        shared.execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE);
        shared.execute("CREATE DATABASE " + PROBE_DATABASE);
        this.probe = jdbcFor(probeUrl());
    }

    @AfterEach
    void dropIt() {
        jdbcFor(POSTGRES.getJdbcUrl()).execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("on a database already holding 11, 12 and 30 -- names of its own -- V70 adds the rest once and leaves "
            + "those three as they were")
    void v70LeavesTheCodesItFinds() {
        migrateTo(lastVersionBefore(V70));
        probe.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'NOT ON PAYROLL'), "
                + "(12, 'MATERNITY LEAVE (DEMO)'), (30, 'NO AUTHORITY TO DEBIT')");

        migrateTo(V72);

        assertThat(probe.queryForList("SELECT code FROM suspension_codes ORDER BY code", Integer.class))
                .as("her sheet's codes, each once").containsExactlyElementsOf(MigrationSeed.SUSPENSION_CODES);
        assertThat(probe.queryForObject("SELECT name FROM suspension_codes WHERE code = 12", String.class))
                .as("a row V70 found is never rewritten").isEqualTo("MATERNITY LEAVE (DEMO)");
        assertThat(probe.queryForObject("SELECT name FROM suspension_codes WHERE code = 30", String.class))
                .isEqualTo("NO AUTHORITY TO DEBIT");
        assertThat(probe.queryForObject("SELECT name FROM suspension_codes WHERE code = 16", String.class))
                .isEqualTo("PAYMENT STOPPED");
        assertThat(probe.queryForObject("SELECT count(*) FROM suspension_codes WHERE code IN (14, 17, 20)", Long.class))
                .as("14, 17 and 20 are not on her sheet, and are not invented (Friday item 2)").isZero();
    }

    @Test
    @DisplayName("the must-pass twin: on an empty database V70 puts every one of her sheet's codes on record -- what "
            + "production is born with")
    void v70SeedsAnEmptyDatabase() {
        migrateTo(V72);
        assertThat(probe.queryForList("SELECT code FROM suspension_codes ORDER BY code", Integer.class))
                .containsExactlyElementsOf(MigrationSeed.SUSPENSION_CODES);
    }

    @Test
    @DisplayName("V70's bank codes, rate, calendar and audit kinds as the code reads them -- and their constraints by name")
    void v70sDataAndConstraints() {
        migrateTo(V72);
        assertThat(probe.queryForList("SELECT code FROM acb_unpaid_codes ORDER BY length(code), code", String.class))
                .containsExactlyElementsOf(MigrationSeed.ACB_UNPAID_CODES.stream().map(MigrationSeed.UnpaidCode::code)
                        .toList());
        Map<String, Object> two = probe.queryForMap("SELECT re_debit_allowed, suspension_code, provisional, version "
                + "FROM acb_unpaid_codes WHERE code = '2'");
        assertThat(two).containsEntry("re_debit_allowed", true).containsEntry("suspension_code", 18)
                .containsEntry("provisional", false).containsEntry("version", 0);
        assertThat(probe.queryForObject("SELECT wording FROM acb_unpaid_codes WHERE code = '6'", String.class))
                .as("her sheet's words").isEqualTo("Debit returned");
        assertThat(probe.queryForList("SELECT effective_from::text || ' ' || amount || ' ' || provisional "
                + "FROM acb_rates ORDER BY effective_from", String.class))
                .containsExactly("2025-01-01 166.65 true", "2026-01-01 174.00 false");
        assertThat(probe.queryForObject("SELECT known_until::text || ' ' || bank_closures_count FROM acb_calendar",
                String.class)).isEqualTo("2028-12-31 true");
        assertThat(probe.queryForObject("SELECT count(*) FROM acb_holidays WHERE kind = 'PUBLIC_HOLIDAY'", Long.class))
                .isEqualTo((long) MigrationSeed.ACB_HOLIDAYS.size());
        assertThat(constraintsOf("acb_holidays")).contains("acb_holidays_kind_known");
        assertThat(constraintsOf("acb_run_lines")).contains("acb_run_lines_withdrawn_known",
                "acb_run_lines_withdrawn_says_when", "acb_run_lines_returns_batch_fk");
        assertThat(constraintsOf("acb_returns_batches")).contains("acb_returns_batches_source_known",
                "acb_returns_batches_counts_add_up", "acb_returns_batches_run_fk");
        assertThat(constraintsOf("acb_unpaid_counts")).contains("acb_unpaid_counts_one_per_member",
                "acb_unpaid_counts_count_in_range", "acb_unpaid_counts_member_fk");
        assertThat(constraintsOf("acb_letters")).contains("acb_letters_one_per_line_and_template",
                "acb_letters_template_known").doesNotContain("acb_letters_one_per_unpaid");
        assertThat(probe.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'members'", String.class))
                .contains("members_bank_name_normalised");
    }

    @Test
    @DisplayName("V72's counts refuse a count outside 0 to 3, and the audit trail takes V70's run-less kinds")
    void theConstraintsHold() {
        migrateTo(V72);
        probe.update("INSERT INTO members (id, surname, created_at, updated_at) VALUES (1, 'Invented', now(), now())");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> probe.update("INSERT INTO acb_unpaid_counts "
                        + "(member_id, membership_number, unpaid_count, counted_on, source, entered_by, entered_at) "
                        + "VALUES (1, 'M-095601', 4, DATE '2026-10-14', 'test', 'test', now())"))
                .hasMessageContaining("acb_unpaid_counts_count_in_range");
        probe.update("INSERT INTO acb_unpaid_counts (member_id, membership_number, unpaid_count, counted_on, source, "
                + "entered_by, entered_at) VALUES (1, 'M-095601', 3, DATE '2026-10-14', 'test', 'test', now())");
        probe.update("INSERT INTO acb_audit_events (occurred_at, actor, kind, reason) "
                + "VALUES (now(), 'test', 'DEBIT_DAY_MOVED', 'a bank moved')");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> probe.update("INSERT INTO acb_audit_events "
                        + "(occurred_at, actor, kind, reason) VALUES (now(), 'test', 'NOT_A_KIND', 'x')"))
                .hasMessageContaining("acb_audit_events_runless_kinds");
    }

    // ── the machinery, as MemberMoneyMigrationIT's ───────────────────────────────────────────

    private List<String> constraintsOf(String table) {
        return probe.queryForList("SELECT c.conname FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid "
                + "WHERE t.relname = ?", String.class, table);
    }

    private void migrateTo(MigrationVersion target) {
        flyway(target).migrate();
    }

    private Flyway flyway(MigrationVersion target) {
        return Flyway.configure()
                .dataSource(probeUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }

    /** The last migration before {@code version}, read from the chain, never written down here. */
    private MigrationVersion lastVersionBefore(MigrationVersion version) {
        return Arrays.stream(flyway(MigrationVersion.LATEST).info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .filter(v -> v.compareTo(version) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    private String probeUrl() {
        return "jdbc:postgresql://" + POSTGRES.getHost() + ":"
                + POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + PROBE_DATABASE;
    }

    private static JdbcTemplate jdbcFor(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}

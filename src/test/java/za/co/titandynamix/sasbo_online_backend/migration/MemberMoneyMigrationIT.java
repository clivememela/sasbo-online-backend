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
import za.co.titandynamix.sasbo_online_backend.support.AbstractPostgresIT;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔴 <b>V40 — the member's four money columns become {@code NUMERIC(12,2)}, step one of two.</b>
 *
 * <h2>Why this runs the chain itself, in a database of its own</h2>
 * A data migration can only be tested by having data in front of it. Every other
 * lane here starts <b>after</b> Flyway has finished, so there is no moment at
 * which a test could put {@code 'abc'} into a text column and watch V40 decide
 * what to do with it. This class therefore creates an empty database beside the
 * shared one, migrates it to the version <b>before</b> V40, writes one row per
 * shape, and then applies V40 and reads the result.
 *
 * <p>A separate <b>database</b> rather than a separate schema, because
 * {@code V2__Update_Union_Schema.sql} names {@code public.members} explicitly:
 * the chain is not schema-agnostic, and a test that pretended otherwise would
 * either fail confusingly or quietly migrate the shared schema.
 *
 * <h2>The fixture is the rule's own table</h2>
 * {@link #EDGE_SHAPES} is the list the gate names (R0-6) — the shapes the seed
 * does not contain, because all 640 seeded values are clean numeric text and
 * therefore exercise none of the decisions this migration makes. Each row
 * carries what it must become and what the migration must say about it, so the
 * expectation is one table rather than a dozen scattered literals.
 *
 * <h2>The non-vacuity half</h2>
 * {@code lexicalOrderingIsWhatTheTextColumnStillDoes} asserts the OLD behaviour
 * on the {@code *_text} column that V40 leaves behind — in the same database,
 * from the same bytes. Without it, "ordered numerically" is a claim about a
 * column that might simply have been empty.
 */
@DisplayName("V40 against the real migration chain")
class MemberMoneyMigrationIT extends AbstractPostgresIT {

    /** The migration this test is about. */
    private static final MigrationVersion V40 = MigrationVersion.fromVersion("40");

    private static final String PROBE_DATABASE = "v40_money_probe";

    /**
     * surname · the raw text · what the numeric column must hold · what the report must say.
     * A {@code null} expectation means NULL; a {@code null} outcome means "not reported".
     */
    private static final List<Object[]> EDGE_SHAPES = List.of(
            new Object[] {"ordinary", "174.00", new BigDecimal("174.00"), null},
            new Object[] {"padded", " 174 ", new BigDecimal("174.00"), null},
            new Object[] {"negative", "-5", new BigDecimal("-5.00"), null},
            new Object[] {"rounding", "174.005", new BigDecimal("174.01"), "rounded"},
            new Object[] {"blank", "", null, "blank"},
            new Object[] {"whitespace", "   ", null, "blank"},
            new Object[] {"letters", "abc", null, "unparseable"},
            new Object[] {"comma", "174,00", null, "unparseable"},
            new Object[] {"exponent", "1e3", null, "unparseable"},
            new Object[] {"currency", "R174", null, "unparseable"},
            new Object[] {"absent", null, null, null});

    private JdbcTemplate probe;

    @BeforeEach
    void createAnEmptyDatabaseOfItsOwn() {
        JdbcTemplate shared = jdbcFor(POSTGRES.getJdbcUrl());
        shared.execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE);
        shared.execute("CREATE DATABASE " + PROBE_DATABASE);
        this.probe = jdbcFor(probeUrl());
    }

    @AfterEach
    void dropIt() {
        this.probe = null;
        JdbcTemplate shared = jdbcFor(POSTGRES.getJdbcUrl());
        shared.execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("every shape is converted or reported, and nothing is silently nulled")
    void everyShapeIsConvertedOrReported() {
        migrateTo(lastVersionBeforeV40());
        assertThat(columnType("monthly_subscription"))
                .as("the precondition: before V40 the money column is text")
                .isEqualTo("character varying");
        insertTheEdgeShapes();

        migrateTo(MigrationVersion.LATEST);

        assertThat(columnType("monthly_subscription"))
                .as("after V40 it is numeric")
                .isEqualTo("numeric");

        for (Object[] shape : EDGE_SHAPES) {
            String surname = (String) shape[0];
            BigDecimal expected = (BigDecimal) shape[2];
            String outcome = (String) shape[3];

            BigDecimal actual = this.probe.queryForObject(
                    "SELECT monthly_subscription FROM members WHERE surname = ?",
                    BigDecimal.class, surname);
            if (expected == null) {
                assertThat(actual).as("%s (%s) must not be converted", surname, shape[1]).isNull();
            } else {
                assertThat(actual).as("%s (%s)", surname, shape[1]).isEqualByComparingTo(expected);
            }

            List<Map<String, Object>> reported = this.probe.queryForList(
                    "SELECT outcome, raw_value FROM members_money_migration_report r"
                            + " JOIN members m ON m.id = r.member_id"
                            + " WHERE m.surname = ? AND r.column_name = 'monthly_subscription_text'",
                    surname);
            if (outcome == null) {
                assertThat(reported).as("%s must not be reported", surname).isEmpty();
            } else {
                assertThat(reported).as("%s must be reported once", surname).hasSize(1);
                assertThat(reported.get(0).get("outcome")).isEqualTo(outcome);
                assertThat(reported.get(0).get("raw_value"))
                        .as("the report keeps the raw text, so a human can decide what it meant")
                        .isEqualTo(((String) shape[1]).trim());
            }
        }

        assertThat(this.probe.queryForObject(
                "SELECT count(*) FROM members WHERE monthly_subscription_text IS NOT NULL", Integer.class))
                .as("nothing was destroyed: every text value is still readable in its *_text column")
                .isEqualTo(10);
    }

    @Test
    @DisplayName("money orders numerically afterwards -- and the text it came from still does not")
    void moneyOrdersNumerically() {
        // R0-1, with its own non-vacuity beside it: the same two values, in the
        // same database, ordered by the numeric column and by the text column V40
        // left behind. If the numeric assertion below could pass on an empty
        // table, the text one would too -- and it does not.
        migrateTo(lastVersionBeforeV40());
        insertMember("nine", "9.00");
        insertMember("hundred", "100.00");

        migrateTo(MigrationVersion.LATEST);

        assertThat(this.probe.queryForList(
                "SELECT surname FROM members WHERE monthly_subscription IS NOT NULL"
                        + " ORDER BY monthly_subscription", String.class))
                .as("numeric: nine before a hundred")
                .containsExactly("nine", "hundred");

        assertThat(this.probe.queryForList(
                "SELECT surname FROM members WHERE monthly_subscription_text IS NOT NULL"
                        + " ORDER BY monthly_subscription_text", String.class))
                .as("lexical, which is what this column did for every one of its rows until now")
                .containsExactly("hundred", "nine");
    }

    // ── the machinery ────────────────────────────────────────────────────────────────────────

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

    /**
     * The last migration before V40, <b>read from the chain</b> rather than written down here.
     *
     * <p>Flyway refuses a target version that does not exist, and there is no V39: the ACB arc
     * starts at V40 and the gap below it is deliberate. Naming the current neighbour (V29) instead
     * would be a second copy of the chain's shape in a test that is not about the chain's shape —
     * and it would quietly stop meaning "just before V40" the day the capture arc adds a V30.
     */
    private MigrationVersion lastVersionBeforeV40() {
        return Arrays.stream(flyway(MigrationVersion.LATEST).info().all())
                .map(MigrationInfo::getVersion)
                .filter(Objects::nonNull)
                .filter(version -> version.compareTo(V40) < 0)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException(
                        "no migration below V40 -- the chain cannot be wound to before the one under test"));
    }

    private void insertTheEdgeShapes() {
        for (Object[] shape : EDGE_SHAPES) {
            insertMember((String) shape[0], (String) shape[1]);
        }
    }

    private void insertMember(String surname, String rawMoney) {
        this.probe.update(
                "INSERT INTO members (surname, monthly_subscription, current_balance,"
                        + " created_at, updated_at) VALUES (?, ?, ?, now(), now())",
                surname, rawMoney, rawMoney);
    }

    private String columnType(String column) {
        return this.probe.queryForObject(
                "SELECT data_type FROM information_schema.columns"
                        + " WHERE table_name = 'members' AND column_name = ?",
                String.class, column);
    }

    private String probeUrl() {
        return POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName(), "/" + PROBE_DATABASE);
    }

    private static JdbcTemplate jdbcFor(String url) {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(url);
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return new JdbcTemplate(dataSource);
    }
}

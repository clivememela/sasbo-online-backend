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

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

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
 * <p>Every shape is written to <b>all four</b> money columns and every column is
 * read back, because V40 converts them in a single {@code UPDATE} and a test that
 * looked at one of them would pass just as happily if the other three had been
 * left out of that statement.
 *
 * <h2>The non-vacuity half</h2>
 * {@code moneyOrdersNumerically} asserts the OLD behaviour on the {@code *_text}
 * column that V40 leaves behind — in the same database, from the same bytes.
 * Without it, "ordered numerically" is a claim about a column that might simply
 * have been empty.
 */
@DisplayName("V40 against the real migration chain")
class MemberMoneyMigrationIT extends AbstractPostgresIT {

    /** The migration this test is about. */
    private static final MigrationVersion V40 = MigrationVersion.fromVersion("40");

    private static final String PROBE_DATABASE = "v40_money_probe";

    /** The one table V40 creates. */
    private static final String REPORT_TABLE = "members_money_migration_report";

    /** The four columns V40 converts. Named here so the assertions cover all of them, not one. */
    private static final List<String> MONEY_COLUMNS =
            List.of("monthly_subscription", "current_balance", "arrears_balance", "last_month_balance");

    /**
     * One row of the migration's own rule table.
     *
     * @param surname how the row is found again; the shape's name, not a person's
     * @param raw     the text the column holds before V40, or {@code null} for no value at all
     * @param numeric what the NUMERIC column must hold afterwards, or {@code null} for NULL
     * @param outcome what the report must say about it, or {@code null} for "not reported"
     */
    private record Shape(String surname, String raw, BigDecimal numeric, String outcome) {

        /** The raw text as the report stores it: trimmed, because the report trims. */
        String reportedValue() {
            return this.raw == null ? null : this.raw.trim();
        }
    }

    private static final List<Shape> EDGE_SHAPES = List.of(
            new Shape("ordinary", "174.00", new BigDecimal("174.00"), null),
            new Shape("padded", " 174 ", new BigDecimal("174.00"), null),
            new Shape("negative", "-5", new BigDecimal("-5.00"), null),
            new Shape("rounding", "174.005", new BigDecimal("174.01"), "rounded"),
            new Shape("blank", "", null, "blank"),
            new Shape("whitespace", "   ", null, "blank"),
            new Shape("letters", "abc", null, "unparseable"),
            new Shape("comma", "174,00", null, "unparseable"),
            new Shape("exponent", "1e3", null, "unparseable"),
            new Shape("currency", "R174", null, "unparseable"),
            new Shape("absent", null, null, null));

    /** Derived, not written down: the number of shapes that put text in a column at all. */
    private static final long SHAPES_WITH_TEXT =
            EDGE_SHAPES.stream().filter(shape -> shape.raw() != null).count();

    private JdbcTemplate probe;

    /**
     * The last version before V40, read from the chain once per test rather than on each use —
     * {@code info()} opens a connection and scans the classpath, and the answer cannot change
     * while a test is running.
     */
    private MigrationVersion beforeV40;

    @BeforeEach
    void createAnEmptyDatabaseOfItsOwn() {
        JdbcTemplate shared = jdbcFor(POSTGRES.getJdbcUrl());
        shared.execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE);
        shared.execute("CREATE DATABASE " + PROBE_DATABASE);
        this.probe = jdbcFor(probeUrl());
        this.beforeV40 = lastVersionBeforeV40();
    }

    @AfterEach
    void dropIt() {
        jdbcFor(POSTGRES.getJdbcUrl()).execute("DROP DATABASE IF EXISTS " + PROBE_DATABASE + " WITH (FORCE)");
    }

    @Test
    @DisplayName("every shape is converted or reported, and nothing is silently nulled")
    void everyShapeIsConvertedOrReported() {
        migrateTo(this.beforeV40);
        assertThat(columnType("members", "monthly_subscription"))
                .as("the precondition: before V40 the money column is text")
                .isEqualTo("character varying");
        insertTheEdgeShapes();

        migrateTo(MigrationVersion.LATEST);

        // Softly, because the first disagreeing shape is rarely the only one, and
        // the whole point of a rule table is to see the rule's shape when it breaks.
        assertSoftly(softly -> {
            for (String column : MONEY_COLUMNS) {
                softly.assertThat(columnType("members", column))
                        .as("after V40 %s is numeric", column)
                        .isEqualTo("numeric");

                for (Shape shape : EDGE_SHAPES) {
                    BigDecimal actual = this.probe.queryForObject(
                            "SELECT " + column + " FROM members WHERE surname = ?",
                            BigDecimal.class, shape.surname());
                    if (shape.numeric() == null) {
                        softly.assertThat(actual)
                                .as("%s.%s (%s) must not be converted", column, shape.surname(), shape.raw())
                                .isNull();
                    } else {
                        softly.assertThat(actual)
                                .as("%s.%s (%s)", column, shape.surname(), shape.raw())
                                .isEqualByComparingTo(shape.numeric());
                    }

                    List<Map<String, Object>> reported = reportRowsFor(column, shape.surname());
                    if (shape.outcome() == null) {
                        softly.assertThat(reported)
                                .as("%s.%s must not be reported", column, shape.surname())
                                .isEmpty();
                    } else {
                        softly.assertThat(reported)
                                .as("%s.%s must be reported once", column, shape.surname())
                                .hasSize(1);
                        if (reported.size() == 1) {
                            softly.assertThat(reported.get(0).get("outcome"))
                                    .as("%s.%s", column, shape.surname())
                                    .isEqualTo(shape.outcome());
                            softly.assertThat(reported.get(0).get("raw_value"))
                                    .as("the report keeps the raw text, so a human can decide what it meant")
                                    .isEqualTo(shape.reportedValue());
                        }
                    }
                }

                softly.assertThat(this.probe.queryForObject(
                                "SELECT count(*) FROM members WHERE " + column + "_text IS NOT NULL", Long.class))
                        .as("nothing was destroyed: every %s text value is still readable in %s_text",
                                column, column)
                        .isEqualTo(SHAPES_WITH_TEXT);
            }
        });
    }

    @Test
    @DisplayName("money orders numerically afterwards -- and the text it came from still does not")
    void moneyOrdersNumerically() {
        // R0-1, with its own non-vacuity beside it: the same two values, in the
        // same database, ordered by the numeric column and by the text column V40
        // left behind. If the numeric assertion below could pass on an empty
        // table, the text one would too -- and it does not.
        migrateTo(this.beforeV40);
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

    @Test
    @DisplayName("V40 adds one table and drops none -- the report, and nothing else")
    void v40AddsOnlyTheReportTable() {
        // 🔑 This is what earns MigrationChainContentTest's table count going from 24
        // to 25. That test is gated behind P0_DB_URL and does not run in either
        // ordinary lane, so on its own the new number would be an edit nobody checked.
        // Here the same claim is a measured delta, in a lane that runs.
        //
        // It is also the check a data migration most needs and least often gets: the
        // difference between renaming a column and dropping one is invisible in a
        // report of converted values, and catastrophic.
        migrateTo(this.beforeV40);
        List<String> before = publicTables();

        migrateTo(MigrationVersion.LATEST);

        assertThat(publicTables())
                .as("every table that existed before V40, plus its report, and nothing else")
                .containsExactlyInAnyOrderElementsOf(
                        Stream.concat(before.stream(), Stream.of(REPORT_TABLE)).toList());
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

    private List<Map<String, Object>> reportRowsFor(String column, String surname) {
        return this.probe.queryForList(
                "SELECT outcome, raw_value FROM members_money_migration_report r"
                        + " JOIN members m ON m.id = r.member_id"
                        + " WHERE m.surname = ? AND r.column_name = ?",
                surname, column + "_text");
    }

    private void insertTheEdgeShapes() {
        for (Shape shape : EDGE_SHAPES) {
            insertMember(shape.surname(), shape.raw());
        }
    }

    /** The same raw text in all four money columns, so all four are under test. */
    private void insertMember(String surname, String rawMoney) {
        this.probe.update(
                "INSERT INTO members (surname, monthly_subscription, current_balance, arrears_balance,"
                        + " last_month_balance, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, now(), now())",
                surname, rawMoney, rawMoney, rawMoney, rawMoney);
    }

    /**
     * {@code information_schema.columns} lists every schema the session can see, so the schema is
     * named: without it a second {@code members} anywhere on the search path could answer instead,
     * and {@code queryForObject} would fail on two rows rather than on the wrong one.
     */
    private String columnType(String table, String column) {
        return this.probe.queryForObject(
                "SELECT data_type FROM information_schema.columns"
                        + " WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                String.class, table, column);
    }

    /**
     * Built from the container's host and port rather than by editing the database name out of
     * {@code getJdbcUrl()}: that string substitution also rewrites any other occurrence of the
     * name, and the name is the container's to choose.
     */
    /** Every base table in {@code public}, Flyway's own history included -- it is one too. */
    private List<String> publicTables() {
        return this.probe.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                        + " ORDER BY table_name",
                String.class);
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

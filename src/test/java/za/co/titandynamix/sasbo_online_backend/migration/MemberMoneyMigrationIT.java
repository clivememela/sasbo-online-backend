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
 * 🔴 <b>V40 — the member's four money columns gain {@code NUMERIC(12,2)} twins, step one of two.</b>
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
 * <h2>🔴 The assertion that matters most is {@code theOldColumnsAreUntouched}</h2>
 * V40's first version RENAMED the text columns and gave their names to the new
 * NUMERIC ones. That retypes the exact column a running old build writes to, and
 * Flyway runs at boot — before the new instance passes its health check — so the
 * old build is still serving when the migration commits. Measured on PostgreSQL
 * 18.3: inserting a parameter declared {@code varchar} into a {@code numeric}
 * column fails outright (<b>42804</b>), so every member save would have failed
 * for the length of that window. Register-side it is blast-radius <b>F38</b>.
 *
 * <p>So the guard is not "the new columns are right" — it is <b>"the old columns
 * are exactly as they were"</b>, which is the property the deploy depends on and
 * the one a future simplification would quietly remove.
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
 */
@DisplayName("V40 against the real migration chain")
class MemberMoneyMigrationIT extends AbstractPostgresIT {

    /** The migration this test is about. */
    private static final MigrationVersion V40 = MigrationVersion.fromVersion("40");

    /**
     * The last version of step one's world (00.2): V52 contracts the text half away. The tests of V40's behaviour --
     * the overlap an old build and a new one share -- run to here, not to LATEST, which is V52's own subject below.
     */
    private static final MigrationVersion STEP_ONE = MigrationVersion.fromVersion("51");

    private static final String PROBE_DATABASE = "v40_money_probe";

    /** The one table V40 creates. */
    private static final String REPORT_TABLE = "members_money_migration_report";

    /**
     * The four money columns as they exist BEFORE V40 and, deliberately, after it.
     * V40's numeric twin of each is {@code <name>_numeric} until V49 renames it.
     */
    private static final List<String> MONEY_COLUMNS =
            List.of("monthly_subscription", "current_balance", "arrears_balance", "last_month_balance");

    private static String numericTwinOf(String column) {
        return column + "_numeric";
    }

    /**
     * One row of the migration's own rule table.
     *
     * @param surname how the row is found again; the shape's name, not a person's
     * @param raw     the text the column holds before V40, or {@code null} for no value at all
     * @param numeric what the NUMERIC twin must hold afterwards, or {@code null} for NULL
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

    /**
     * Derived, not written down: the shapes V49's gate must count -- a non-blank text that did not
     * convert. Blanks are excluded (nothing in them to lose) and so is the absent value. Each shape is
     * one member, whichever of its four columns qualifies, so this is a count of members.
     */
    private static final long SHAPES_ONLY_IN_TEXT = EDGE_SHAPES.stream()
            .filter(shape -> shape.numeric() == null)
            .filter(shape -> shape.raw() != null && !shape.raw().isBlank())
            .count();

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
    @DisplayName("🔴 the four original columns are untouched -- same name, same type, same bytes")
    void theOldColumnsAreUntouched() {
        // THE backward-compatibility guard, and the reason this migration adds rather
        // than renames. A build that predates V40 is still serving while V40 commits,
        // and it reads and writes these four names as text. If any of them is renamed
        // or retyped, that build's member saves fail with 42804 for the length of the
        // deploy window. Delete this test and the next "simplification" back to a
        // rename passes every other assertion in this file.
        migrateTo(this.beforeV40);
        insertTheEdgeShapes();

        migrateTo(STEP_ONE);

        assertSoftly(softly -> {
            for (String column : MONEY_COLUMNS) {
                softly.assertThat(columnType("members", column))
                        .as("%s must still be text after V40 -- an old build writes strings to it", column)
                        .isEqualTo("character varying");

                for (Shape shape : EDGE_SHAPES) {
                    softly.assertThat(this.probe.queryForObject(
                                    "SELECT " + column + " FROM members WHERE surname = ?",
                                    String.class, shape.surname()))
                            .as("%s.%s keeps its exact bytes", column, shape.surname())
                            .isEqualTo(shape.raw());
                }
            }
        });
    }

    @Test
    @DisplayName("🔴 an OLD build's write reaches the numeric twin, so the new build never serves a stale value")
    void anOldBuildWriteReachesTheNumericTwin() {
        // Adding columns alongside is not enough on its own: during the deploy overlap
        // the old build writes ONLY the text column, and without the trigger the numeric
        // twin keeps its backfilled value -- so the new build serves a stale amount and
        // nothing fails anywhere. That swaps a loud failure for a silent one.
        migrateTo(this.beforeV40);
        insertMember("existing", "100.00");
        migrateTo(STEP_ONE);

        // What the old build does: it knows nothing of *_numeric and writes text.
        this.probe.update("UPDATE members SET monthly_subscription = '174.50' WHERE surname = 'existing'");
        this.probe.update(
                "INSERT INTO members (surname, monthly_subscription, created_at, updated_at)"
                        + " VALUES ('joined-during-overlap', '99.90', now(), now())");

        assertThat(numericOf("monthly_subscription", "existing"))
                .as("an old-build UPDATE to the text must reach the numeric twin")
                .isEqualByComparingTo("174.50");
        assertThat(numericOf("monthly_subscription", "joined-during-overlap"))
                .as("an old-build INSERT must fill the numeric twin it never mentioned")
                .isEqualByComparingTo("99.90");
    }

    @Test
    @DisplayName("🔴 a NEW build's write reaches the text column, so a rollback shows the current value")
    void aNewBuildWriteReachesTheTextColumn() {
        // The rollback half. After the overlap the new build writes ONLY the numeric
        // column; without the trigger, rolling back to the old build would show every
        // member the amount they had before the deploy.
        migrateTo(this.beforeV40);
        insertMember("existing", "100.00");
        migrateTo(STEP_ONE);

        // What the new build does: it no longer maps the text column at all.
        this.probe.update("UPDATE members SET monthly_subscription_numeric = 250.00 WHERE surname = 'existing'");
        this.probe.update(
                "INSERT INTO members (surname, monthly_subscription_numeric, created_at, updated_at)"
                        + " VALUES ('joined-after-deploy', 120.50, now(), now())");

        assertThat(textOf("monthly_subscription", "existing"))
                .as("a new-build UPDATE to the numeric must reach the text an old build would read")
                .isEqualTo("250.00");
        assertThat(textOf("monthly_subscription", "joined-after-deploy"))
                .as("a new-build INSERT must fill the text it never mentioned")
                .isEqualTo("120.50");
    }

    @Test
    @DisplayName("🔴 V49's gate COUNTS the values that exist only as text -- it does not merely return zero")
    void theV49GateCountsWhatExistsOnlyInText() {
        // The gate's first draft was blind: for 'abc' both sides are NULL, and NULL IS
        // DISTINCT FROM NULL is false, so it returned 0 over fixtures that included the
        // very values it exists to protect -- and that 0 was once reported as a pass.
        // So the assertion that matters is the non-zero one: the gate must SEE them.
        migrateTo(this.beforeV40);
        insertTheEdgeShapes();
        migrateTo(STEP_ONE);

        assertThat(gate())
                .as("every member with a non-blank, unparseable money value must be counted")
                .isEqualTo(SHAPES_ONLY_IN_TEXT)
                .isPositive();

        // The must-pass twin: once a human has decided what each of those meant, the
        // gate opens. Written as the new build would write it, so the trigger runs too.
        this.probe.update(
                "UPDATE members SET monthly_subscription_numeric = 0, current_balance_numeric = 0,"
                        + " arrears_balance_numeric = 0, last_month_balance_numeric = 0"
                        + " WHERE monthly_subscription_numeric IS NULL"
                        + " AND nullif(btrim(monthly_subscription), '') IS NOT NULL");

        assertThat(gate())
                .as("with every text-only value resolved, nothing would be lost by dropping the text")
                .isZero();
    }

    @Test
    @DisplayName("every shape is converted or reported, and nothing is silently nulled")
    void everyShapeIsConvertedOrReported() {
        migrateTo(this.beforeV40);
        assertThat(columnType("members", "monthly_subscription"))
                .as("the precondition: before V40 the money column is text")
                .isEqualTo("character varying");
        insertTheEdgeShapes();

        migrateTo(STEP_ONE);

        // Softly, because the first disagreeing shape is rarely the only one, and
        // the whole point of a rule table is to see the rule's shape when it breaks.
        assertSoftly(softly -> {
            for (String column : MONEY_COLUMNS) {
                String twin = numericTwinOf(column);
                softly.assertThat(columnType("members", twin))
                        .as("after V40 %s is numeric", twin)
                        .isEqualTo("numeric");

                for (Shape shape : EDGE_SHAPES) {
                    BigDecimal actual = this.probe.queryForObject(
                            "SELECT " + twin + " FROM members WHERE surname = ?",
                            BigDecimal.class, shape.surname());
                    if (shape.numeric() == null) {
                        softly.assertThat(actual)
                                .as("%s.%s (%s) must not be converted", twin, shape.surname(), shape.raw())
                                .isNull();
                    } else {
                        softly.assertThat(actual)
                                .as("%s.%s (%s)", twin, shape.surname(), shape.raw())
                                .isEqualByComparingTo(shape.numeric());
                    }

                    // The report names the ORIGINAL column: that is where the raw value
                    // lives, and it is what V49 drops.
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
                                "SELECT count(*) FROM members WHERE " + column + " IS NOT NULL", Long.class))
                        .as("nothing was destroyed: every %s text value is still readable", column)
                        .isEqualTo(SHAPES_WITH_TEXT);
            }
        });
    }

    @Test
    @DisplayName("money orders numerically afterwards -- and the text it came from still does not")
    void moneyOrdersNumerically() {
        // R0-1, with its own non-vacuity beside it: the same two values, in the
        // same database, ordered by the numeric twin and by the text column V40
        // left in place. If the numeric assertion below could pass on an empty
        // table, the text one would too -- and it does not.
        migrateTo(this.beforeV40);
        insertMember("nine", "9.00");
        insertMember("hundred", "100.00");

        migrateTo(STEP_ONE);

        assertThat(this.probe.queryForList(
                "SELECT surname FROM members WHERE monthly_subscription_numeric IS NOT NULL"
                        + " ORDER BY monthly_subscription_numeric", String.class))
                .as("numeric: nine before a hundred")
                .containsExactly("nine", "hundred");

        assertThat(this.probe.queryForList(
                "SELECT surname FROM members WHERE monthly_subscription IS NOT NULL"
                        + " ORDER BY monthly_subscription", String.class))
                .as("lexical, which is what this column did for every one of its rows until now "
                        + "-- and still does, because V40 leaves it alone")
                .containsExactly("hundred", "nine");
    }

    @Test
    @DisplayName("V40 adds one table and drops none -- the report, and nothing else")
    void v40AddsOnlyTheReportTable() {
        // 🔑 This is what earns MigrationChainContentTest's table count going from 24
        // to 25. That test is gated behind P0_DB_URL and does not run in either
        // ordinary lane, so on its own the new number would be an edit nobody checked.
        // Here the same claim is a measured delta, in a lane that runs.
        migrateTo(this.beforeV40);
        List<String> before = publicTables();

        // To V40 EXACTLY, not LATEST: this test's subject is V40, and from ACB P1 the chain goes
        // on to V41-V43, which add six tables of their own. Measuring to LATEST would redden this
        // for a migration it is not about -- ACB's tables are counted where they are added.
        migrateTo(V40);

        assertThat(publicTables())
                .as("every table that existed before V40, plus its report, and nothing else")
                .containsExactlyInAnyOrderElementsOf(
                        Stream.concat(before.stream(), Stream.of(REPORT_TABLE)).toList());
    }

    @Test
    @DisplayName("🔴 R0-7: V52 refuses while any member holds a money value only as text -- and nothing of it lands")
    void v52RefusesWhileAValueLivesOnlyInText() {
        migrateTo(this.beforeV40);
        insertTheEdgeShapes();
        migrateTo(STEP_ONE);
        assertThat(gate()).as("the fixture holds text-only values for V52 to find").isPositive();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> migrateTo(MigrationVersion.LATEST))
                .hasStackTraceContaining("V52 refuses");

        // Flyway runs the file in one transaction on PostgreSQL: the text, the trigger and the gate are all there.
        assertThat(textColumnsLeft()).containsExactlyInAnyOrderElementsOf(MONEY_COLUMNS);
        assertThat(gate()).as("the gate is still there to be asked").isPositive();
    }

    @Test
    @DisplayName("V52 drops the text half, the trigger and the gate once nothing lives only in text -- and renames nothing")
    void v52ContractsOnceTheTextIsClean() {
        // The must-pass twin of the refusal: values that converted, so nothing would be lost.
        migrateTo(this.beforeV40);
        insertMember("nine", "9.00");
        insertMember("hundred", "100.00");

        migrateTo(MigrationVersion.LATEST);

        assertThat(textColumnsLeft()).as("the four VARCHAR columns are gone").isEmpty();
        assertThat(numericOf("monthly_subscription", "hundred")).as("the numeric twin, under its own name")
                .isEqualByComparingTo("100.00");
        assertThat(this.probe.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'members_money_keep_in_step'", Integer.class)).isZero();
        assertThat(this.probe.queryForObject("SELECT count(*) FROM pg_proc WHERE proname IN "
                + "('members_money_only_in_text', 'members_money_keep_in_step')", Integer.class)).isZero();
        assertThat(this.probe.queryForObject(
                "SELECT count(*) FROM pg_proc WHERE proname = 'money_text_as_numeric'", Integer.class))
                .as("the parse rule stays: it reads no column").isOne();
    }

    /** Which of the four VARCHAR money columns the probe's members table still has. */
    private List<String> textColumnsLeft() {
        return this.probe.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'members'"
                + " AND column_name IN ('monthly_subscription', 'current_balance', 'arrears_balance', 'last_month_balance')",
                String.class);
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

    /** V49's gate, called exactly as V49 will call it -- one definition, in V40. */
    private long gate() {
        return this.probe.queryForObject("SELECT members_money_only_in_text()", Long.class);
    }

    private BigDecimal numericOf(String column, String surname) {
        return this.probe.queryForObject(
                "SELECT " + numericTwinOf(column) + " FROM members WHERE surname = ?", BigDecimal.class, surname);
    }

    private String textOf(String column, String surname) {
        return this.probe.queryForObject(
                "SELECT " + column + " FROM members WHERE surname = ?", String.class, surname);
    }

    private List<Map<String, Object>> reportRowsFor(String column, String surname) {
        return this.probe.queryForList(
                "SELECT outcome, raw_value FROM " + REPORT_TABLE + " r"
                        + " JOIN members m ON m.id = r.member_id"
                        + " WHERE m.surname = ? AND r.column_name = ?",
                surname, column);
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

    /** Every base table in {@code public}, Flyway's own history included -- it is one too. */
    private List<String> publicTables() {
        return this.probe.queryForList(
                "SELECT table_name FROM information_schema.tables"
                        + " WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
                        + " ORDER BY table_name",
                String.class);
    }

    /**
     * Built from the container's host and port rather than by editing the database name out of
     * {@code getJdbcUrl()}: that string substitution also rewrites any other occurrence of the
     * name, and the name is the container's to choose.
     */
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

package za.co.titandynamix.sasbo_online_backend.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Returns the shared integration database to the state the migration chain left
 * it in.
 *
 * <h2>It restores; it does not merely spare</h2>
 * The obvious implementation excludes the migration-seeded reference tables
 * from the clear and moves on. That is a trap, and a real one: a test that
 * MUTATES seeded configuration -- renames a union structure, flips
 * {@code requires_election_date} -- leaks into every test that runs after it in
 * the same JVM, and the symptom appears in an unrelated file. Excluding a table
 * from a reset protects its rows from deletion and not from editing.
 *
 * <p>So the seeded tables are snapshotted once, before any test has had a
 * chance to touch them, and every reset rewrites them from that snapshot.
 * Nothing is spared and everything is restored.
 *
 * <h2>Snapshot timing</h2>
 * The snapshot is taken on first use. The container is started before any test
 * runs and {@code DataSeeder} is neutralised, so first use is still virgin
 * state -- but that is a property of the harness, so
 * {@link #assertSnapshotIsPristine} exists to say so out loud rather than
 * assume it.
 *
 * <h2>A reset is exclusive, and that has to be declared</h2>
 * Test classes run concurrently and every integration test shares ONE real
 * database. A reset is a {@code TRUNCATE} across every table in it, so while
 * one class is resetting, a sibling reading a count sees zero and a sibling
 * that has just written sees its row disappear. Nothing about that is a flake
 * to be retried: it is two tests using one resource without saying so.
 *
 * <p>{@link #SHARED_DATABASE} is the name they say it with. Every integration
 * test that touches table data declares it:
 *
 * <ul>
 *   <li>{@code @ResourceLock(value = SHARED_DATABASE, mode = READ_WRITE)} --
 *       anything that calls {@link #reset(JdbcTemplate)} or writes rows.
 *   <li>{@code @ResourceLock(value = SHARED_DATABASE, mode = READ)} -- anything
 *       that only reads. Readers still run concurrently with each other; they
 *       are simply never running while a truncate is.
 * </ul>
 *
 * <p><b>A lock is a confession.</b> The cheaper answer is a test that never
 * needs one -- transactional and rolled back, or keyed uniquely enough that a
 * sibling cannot collide with it. These locks exist because a truncate-based
 * reset cannot be either of those things, and that is the honest cost of
 * restoring rather than sparing. Every class carrying one says in a comment
 * which of the two it is and why.
 *
 * <p>Found on 2026-09-04 by the first repository test to write rows: the
 * harness self-test truncated the database between that test's {@code save} and
 * its query. The harness had shipped without the declaration because, until
 * then, only the self-test wrote anything.
 */
public final class DatabaseReset {

    /**
     * The one real database every integration test shares. See the class
     * comment -- this is the resource name, and it lives here rather than in
     * each test so that the classes contending for it cannot spell it
     * differently and quietly stop contending.
     */
    public static final String SHARED_DATABASE = "the shared integration database";

    /**
     * Seeded by the migrations, and therefore restored rather than deleted.
     * Ordered parent-first so the re-insert satisfies foreign keys as it goes.
     */
    private static final List<String> SEEDED_TABLES =
            List.of("union_structures", "representative_roles", "recruiters", "acb_rates", "acb_unpaid_codes",
                    "acb_holidays", "acb_calendar");

    /** What the migrations leave behind, from the one place that says so. */
    private static final Map<String, Integer> SEEDED_COUNTS = Map.of(
            "union_structures", MigrationSeed.UNION_STRUCTURES,
            "representative_roles", MigrationSeed.REPRESENTATIVE_ROLES,
            "recruiters", MigrationSeed.RECRUITERS,
            "acb_rates", MigrationSeed.ACB_RATES,
            "acb_unpaid_codes", MigrationSeed.ACB_UNPAID_CODES.size(),
            "acb_holidays", MigrationSeed.ACB_HOLIDAYS.size(),
            "acb_calendar", 1);

    /**
     * The seeded tables keyed by a natural value rather than a generated {@code id} -- ordered by
     * that key, and with no identity sequence to move after the restore. {@code acb_unpaid_codes}
     * is keyed by the bank's own code, {@code acb_holidays} by its date, and {@code acb_calendar}'s one
     * row by an id it assigns itself (F47: without these two, every test that prepares a run after
     * the first reset is refused {@code HOLIDAYS_NOT_KNOWN}).
     */
    private static final Map<String, String> NATURAL_KEYS = Map.of(
            "acb_unpaid_codes", "code", "acb_holidays", "holiday_date", "acb_calendar", "id");

    private static String keyOf(String table) {
        return NATURAL_KEYS.getOrDefault(table, "id");
    }

    private static final String FLYWAY_HISTORY = "flyway_schema_history";

    private static volatile Map<String, List<Map<String, Object>>> snapshot;

    private DatabaseReset() {
    }

    /**
     * Empties every table, then rewrites the migration-seeded ones from the
     * snapshot. Safe to call from any test; the first call takes the snapshot.
     */
    public static synchronized void reset(JdbcTemplate jdbc) {
        if (snapshot == null) {
            snapshot = takeSnapshot(jdbc);
        }

        List<String> tables = allTablesExceptHistory(jdbc);
        // One TRUNCATE ... CASCADE for everything: it ignores foreign key order,
        // which a table-at-a-time DELETE cannot, and RESTART IDENTITY keeps
        // generated ids from drifting upward run after run and turning an id
        // into a test's hidden dependency.
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");

        for (String table : SEEDED_TABLES) {
            restore(jdbc, table, snapshot.get(table));
        }
    }

    /**
     * Asserts the snapshot this class will restore forever is the migrations'
     * output and not some test's leftovers. Called by the harness self-test.
     */
    public static void assertSnapshotIsPristine(JdbcTemplate jdbc) {
        if (snapshot == null) {
            snapshot = takeSnapshot(jdbc);
        }
        SEEDED_COUNTS.forEach((table, expected) -> {
            int actual = snapshot.get(table).size();
            if (actual != expected) {
                throw new AssertionError(
                        "The reset snapshot for " + table + " holds " + actual + " rows, not "
                                + expected + ". Every reset from here on would restore that, so "
                                + "the snapshot was taken after something had already written to "
                                + "the database.");
            }
        });
    }

    private static Map<String, List<Map<String, Object>>> takeSnapshot(JdbcTemplate jdbc) {
        Map<String, List<Map<String, Object>>> taken = new java.util.LinkedHashMap<>();
        for (String table : SEEDED_TABLES) {
            taken.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY " + keyOf(table)));
        }
        return Map.copyOf(taken);
    }

    /**
     * Every table the reset truncates, read once.
     *
     * <p>The list cannot change after the migrations have run, and this used to
     * be an {@code information_schema} query on every single reset.
     */
    private static List<String> allTablesExceptHistory(JdbcTemplate jdbc) {
        List<String> cached = tables;
        if (cached == null) {
            cached = readTableNames(jdbc);
            tables = cached;
        }
        return cached;
    }

    private static volatile List<String> tables;

    private static List<String> readTableNames(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> ? ORDER BY table_name",
                String.class, FLYWAY_HISTORY);
    }

    /**
     * Rewrites one seeded table from the snapshot, in a single batch.
     *
     * <p>One statement per table rather than one per row. The seeded tables
     * hold about sixty rows between them, and this runs on both sides of every
     * integration test -- around a hundred times a run -- so a row-at-a-time
     * insert was ~5,800 round trips to the container for work whose SQL is
     * identical every time. Every row of a table has the same columns, because
     * they all came from one {@code SELECT *}, so the statement is built once.
     */
    private static void restore(JdbcTemplate jdbc, String table, List<Map<String, Object>> rows) {
        if (!rows.isEmpty()) {
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
            List<Object[]> values = rows.stream()
                    .map(row -> columns.stream().map(row::get).toArray())
                    .toList();
            jdbc.batchUpdate("INSERT INTO " + table + " (" + String.join(", ", columns) + ") "
                    + "VALUES (" + placeholders + ")", values);
        }
        // Explicit ids were just written, so the identity sequence still points at
        // 1 and the next JPA insert would collide. Left unset, this is a failure
        // that appears in whatever test inserts next, with no connection to here.
        // A table keyed by a natural value has no sequence to move.
        if (NATURAL_KEYS.containsKey(table)) {
            return;
        }
        jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                + "GREATEST((SELECT COALESCE(max(id), 1) FROM " + table + "), 1))");
    }
}

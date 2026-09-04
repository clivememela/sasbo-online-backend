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
 */
public final class DatabaseReset {

    /**
     * Seeded by the migrations, and therefore restored rather than deleted.
     * Ordered parent-first so the re-insert satisfies foreign keys as it goes.
     */
    private static final List<String> SEEDED_TABLES =
            List.of("union_structures", "representative_roles", "recruiters");

    /** What the migrations leave behind, from the one place that says so. */
    private static final Map<String, Integer> SEEDED_COUNTS = Map.of(
            "union_structures", MigrationSeed.UNION_STRUCTURES,
            "representative_roles", MigrationSeed.REPRESENTATIVE_ROLES,
            "recruiters", MigrationSeed.RECRUITERS);

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
            taken.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY id"));
        }
        return Map.copyOf(taken);
    }

    private static List<String> allTablesExceptHistory(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' "
                        + "AND table_name <> ? ORDER BY table_name",
                String.class, FLYWAY_HISTORY);
    }

    private static void restore(JdbcTemplate jdbc, String table, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            List<String> columns = new ArrayList<>(row.keySet());
            String placeholders = String.join(", ", columns.stream().map(c -> "?").toList());
            jdbc.update("INSERT INTO " + table + " (" + String.join(", ", columns) + ") "
                            + "VALUES (" + placeholders + ")",
                    columns.stream().map(row::get).toArray());
        }
        // Explicit ids were just written, so the identity sequence still points at
        // 1 and the next JPA insert would collide. Left unset, this is a failure
        // that appears in whatever test inserts next, with no connection to here.
        jdbc.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), "
                + "GREATEST((SELECT COALESCE(max(id), 1) FROM " + table + "), 1))");
    }
}

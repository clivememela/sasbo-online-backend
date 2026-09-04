package za.co.titandynamix.sasbo_online_backend.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The substrate guarding itself.
 *
 * <p>{@link AbstractPostgresIT} and {@link DatabaseReset} are the two things
 * every later integration test will trust without checking. "A self-test
 * exists" is not a specification, so this carries the one assertion that cannot
 * be made without a live container -- the self-checking one. The other two
 * assertions the harness needs are static classification checks and live in
 * {@link LaneSeparationTest}, in the fast lane, because a lane guard that only
 * runs when Docker is up cannot protect a machine that has none.
 */
@DisplayName("The harness, checking itself")
class AbstractPostgresITSelfTest extends AbstractWritingIT {

    @Test
    @DisplayName("comes back from a reset with the seed intact -- one assertion, two things proven")
    void resetLeavesAWorkingDatabaseWithItsSeed() {
        DatabaseReset.assertSnapshotIsPristine(jdbc);

        // Dirty it first, in both directions a real test would: insert a row into
        // a mutable table, and MUTATE a seeded one. A reset that only spares the
        // seeded tables survives the first and leaks the second.
        jdbc.update("INSERT INTO banks (name, universal_branch_code) VALUES (?, ?)",
                "Self-test Bank", "000000");
        jdbc.update("UPDATE union_structures SET name = ? WHERE id = 1", "TAMPERED");

        DatabaseReset.reset(jdbc);

        // ONE assertion that can only pass if TWO independent things happened:
        // the container is still reachable (the query runs at all), AND V16's
        // seed is present and unmodified (the row is right). Drop either half
        // and it goes red. Two separate assertions could each be satisfied for
        // the wrong reason -- a reachable but empty database passes the first,
        // and a stale connection to something else could pass the second.
        assertThat(jdbc.queryForObject(
                "SELECT count(*) || ':' || (SELECT name FROM union_structures WHERE id = 1) "
                        + "FROM union_structures", String.class))
                .as("after a reset: the seeded structure count and the root's name, read in one "
                        + "round trip from the container. '19:SASBO' means the database answered "
                        + "AND the migration seed survived; anything else means one of the two "
                        + "did not")
                .isEqualTo(MigrationSeed.UNION_STRUCTURES + ":" + MigrationSeed.ROOT_STRUCTURE);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM banks", Integer.class))
                .as("the row this test inserted, after the reset")
                .isZero();
    }

    @Test
    @DisplayName("hands back identity sequences that do not collide with the restored seed")
    void resetRewindsSequencesWithoutCollidingWithSeededIds() {
        DatabaseReset.reset(jdbc);

        // The seed writes explicit ids, which leaves the identity sequence behind
        // them. Without the setval in DatabaseReset the next generated id would
        // collide -- and it would fail in whichever test inserted next, with
        // nothing pointing back to the reset.
        Integer generated = jdbc.queryForObject(
                "INSERT INTO union_structures (name) VALUES ('sequence probe') RETURNING id",
                Integer.class);

        assertThat(generated)
                .as("a generated id after a reset must not land on top of a seeded one")
                .isGreaterThan(MigrationSeed.UNION_STRUCTURES);

        DatabaseReset.reset(jdbc);
    }
}

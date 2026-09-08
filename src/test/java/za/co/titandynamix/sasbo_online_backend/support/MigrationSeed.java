package za.co.titandynamix.sasbo_online_backend.support;

import java.util.List;

/**
 * What the migration chain seeds, written down once.
 *
 * <p>These numbers were spread across four files before this existed -- the
 * chain-content assertions, the harness check, the self-test and the reset
 * snapshot -- which is four places for one fact to go stale independently, and
 * the exact shape of drift these plans took from MobiZone, where a floor
 * copy-pasted into four guards went stale in all four and two copies ended up
 * disagreeing with each other.
 *
 * <p>They are a SPECIFICATION, not an observation: a test asserting the database
 * matches them is a real assertion, because the database is not where they come
 * from. Re-derive them from the migrations if they ever change; never edit one
 * to make a red test green.
 *
 * <ul>
 *   <li>{@code union_structures} and {@code representative_roles} are V16's
 *       reseed. Under the incumbent's {@code baseline-version: 0} configuration
 *       V16 was skipped outright and both tables were left empty, which is
 *       exactly why they are worth asserting.
 *   <li>{@code recruiters} is V17's seed.
 * </ul>
 */
public final class MigrationSeed {

    public static final int UNION_STRUCTURES = 19;
    public static final int REPRESENTATIVE_ROLES = 6;
    public static final int RECRUITERS = 33;

    /** The root of the structure hierarchy, {@code union_structures.id = 1}. */
    public static final String ROOT_STRUCTURE = "SASBO";

    /**
     * Every version the chain applies, in Flyway's order -- the one declaration
     * of the chain's shape, and the only place it is written down.
     *
     * <p>🔑 THIS LIST IS CLOSED ON PURPOSE. It pins two things a
     * {@code containsAtLeast} would let through: the deliberate absence of a
     * V6, and the presence of {@code 1.1}. Do not relax it to make a red test
     * green -- a new migration is a deliberate act and adding its version here
     * is part of writing it.
     *
     * <p>{@code 1.1} is a fractional version and not a typo. There were briefly
     * two different files both claiming version 1; the surviving baseline is
     * {@code V1__initial_schema.sql} and {@code V1_1__baseline_completion.sql}
     * supplies the three objects it lacks. That file's header carries the whole
     * story.
     */
    public static final List<String> APPLIED_VERSIONS = List.of(
            "1", "1.1", "2", "3", "4", "5", "7", "8", "9", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19");

    /**
     * The last migration in the chain, DERIVED rather than restated.
     *
     * <p>It was previously an {@code int} written out a second time, which is
     * one fact in two places and the shape this class exists to prevent. It is
     * a {@code String} because a Flyway version is not a number -- {@code 1.1}
     * is in the chain, and the query that used to read this cast the column to
     * {@code int}, which that value would have made fail.
     */
    public static final String LAST_MIGRATION_VERSION =
            APPLIED_VERSIONS.get(APPLIED_VERSIONS.size() - 1);

    private MigrationSeed() {
    }
}

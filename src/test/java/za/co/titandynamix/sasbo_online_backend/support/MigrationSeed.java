package za.co.titandynamix.sasbo_online_backend.support;

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

    /** The last migration in the chain. There is deliberately no V6. */
    public static final int LAST_MIGRATION_VERSION = 18;

    private MigrationSeed() {
    }
}

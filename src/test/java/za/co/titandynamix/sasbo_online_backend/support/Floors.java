package za.co.titandynamix.sasbo_online_backend.support;

/**
 * The non-vacuity floors, in one place, each measured through its own guard's
 * discovery.
 *
 * <h2>Why this exists</h2>
 * The floors were written inline at roughly twenty call sites across eight
 * guard classes, and for the <b>same</b> population they had already come to
 * disagree. {@code TestSourceInventory.all()} was floored at <b>5</b> in
 * {@code LaneSeparationTest}, <b>12</b> in {@code DisabledPopulationTest} and
 * <b>20</b> in both {@code CoverageFloorTest} and {@code SecuritySliceTest} --
 * a four-fold spread over one list, with nothing to say which of them was the
 * calibrated one. A reader hitting the 12 could not tell whether it or the 20
 * was right, and neither could the next person to change the walker.
 *
 * <p>This is the argument {@link MigrationSeed} already makes about the seed
 * counts, in its own words: they "were spread across four files before this
 * existed -- four places for one fact to go stale independently." Same fact,
 * same fix.
 *
 * <h2>The rules these numbers obey</h2>
 * <ul>
 *   <li>A floor is a <b>non-vacuity canary, not a coverage target.</b> It
 *       asserts the scan scanned something. It is never raised to match a new
 *       actual: a floor equal to the count is fragile without being stricter.
 *   <li>Each is measured <b>through the guard's own discovery</b>, never by a
 *       different tool. A {@code find} over a directory and
 *       {@code MainSourceInventory.inLayer} disagree by design.
 *   <li>Every number here <b>expires.</b> The date it was measured is part of
 *       the record, and re-measuring is the only way to quote it.
 *   <li>A floor calibrated for one population is <b>never lent to another.</b>
 *       That is what silently disarmed the guards above.
 * </ul>
 *
 * <p>Actuals measured 2026-09-07 by counting sources: 12 controllers, 10
 * mappers, 17 repositories, 19 service files, 9 service implementations, 44
 * entities and DTOs, 62 test classes, 10 integration tests, 17 classes
 * declaring {@code @WebMvcTest}, 1 exception handler, 258 files tracked by git.
 * Floors sit below each with real headroom. Two entries are marked ⚑ because
 * they are discovered at run time and cannot be counted from the filesystem.
 */
public final class Floors {

    private Floors() {
    }

    /** Controllers, via {@code MainSourceInventory.inLayer(CONTROLLER)}. Actual 12. */
    public static final int CONTROLLERS = 10;

    /** Mappers, via {@code MainSourceInventory.inLayer(MAPPER)}. Actual 10. */
    public static final int MAPPERS = 8;

    /** Repositories, via {@code MainSourceInventory.inLayer(REPOSITORY)}. Actual 17. */
    public static final int REPOSITORIES = 12;

    /**
     * Repositories declaring a query method beyond inherited CRUD. Actual 15.
     *
     * <p>Its own entry rather than {@link #REPOSITORIES}. This is a strict
     * SUBSET of that population and was previously floored with the superset's
     * number, justified in the Javadoc by the subset's actual -- so each of the
     * two was documented by the other's count.
     *
     * <p>⚑ The actual is the one figure here NOT re-measured on 2026-09-07: it
     * is produced by a regex over repository sources at run time, not by
     * counting files, so it cannot be measured without running the guard. Last
     * recorded as 15 on 2026-09-04. The floor is left below that.
     */
    public static final int REPOSITORIES_WITH_A_QUERY = 10;

    /** Service files, via {@code MainSourceInventory.inLayer(SERVICE)}. Actual 19. */
    public static final int SERVICES = 15;

    /** Service implementations, via {@code services/impl}. Actual 9. */
    public static final int SERVICE_IMPLEMENTATIONS = 5;

    /** Entities and DTOs together, the PII-bearing types. Actual 44. */
    public static final int DATA_TYPES = 30;

    /** Exception handlers. Actual 1 -- the floor is the population. */
    public static final int EXCEPTION_HANDLERS = 1;

    /**
     * Test classes, via {@code TestSourceInventory.all()}. Actual 62.
     *
     * 🔑 THE ONE THAT HAD FOUR VALUES. 5, 12, 20 and 20 across four guards over
     * one list. 20 was the best calibrated of them and is kept; the others were
     * not deliberate choices about this population, they were numbers written
     * at four different times.
     */
    public static final int TEST_CLASSES = 20;

    /** Integration test classes, by the {@code *IT} filename rule. Actual 10. */
    public static final int INTEGRATION_TEST_CLASSES = 5;

    /** Test classes declaring {@code @WebMvcTest}. Actual 17. */
    public static final int CONTROLLER_SLICES = 8;

    /** Files tracked by git in this repository. Actual 258. */
    public static final int TRACKED_FILES = 100;

    /**
     * Collection-returning endpoints.
     *
     * <p>⚑ Discovered at run time by walking controller sources, so like
     * {@link #REPOSITORIES_WITH_A_QUERY} it has no actual that can be counted
     * from the filesystem. Floor unchanged from where the guard set it.
     */
    public static final int COLLECTION_ENDPOINTS = 8;
}

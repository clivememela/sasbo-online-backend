package za.co.titandynamix.sasbo_online_backend.support;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory.TestClassSource;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The lane split, guarded.
 *
 * <p>Two lanes only work if every test class is in exactly one of them.
 * {@code test} excludes the {@code integration} tag and {@code integrationTest}
 * includes it, so a class that is in NEITHER simply never runs -- and never
 * running looks identical to passing in every report anyone reads.
 *
 * <p>This lives in the FAST lane, deliberately, even though it guards the
 * harness. A classification guard that only runs when Docker is up cannot
 * protect a machine without Docker, and the fast lane is precisely the
 * population that matters there. The third harness assertion -- the one that
 * needs a live container -- is in {@link AbstractPostgresITSelfTest}.
 */
@DisplayName("The two lanes")
class LaneSeparationTest {

    /**
     * Deliberately far below the real count, and never raised to match it. It
     * asserts the walker walked, not how many tests exist.
     */

    @Test
    @DisplayName("classify two anchors whose lane is not in doubt")
    void anchorCanaries() {
        // Two named classes with known membership. This pins the LOGIC rather
        // than the suite size, so it never drifts as test files land -- which a
        // count-based version of this assertion would.
        List<TestClassSource> all = TestSourceInventory.all();

        TestClassSource integrationAnchor = byName(all, "PostgresHarnessIT");
        TestClassSource fastAnchor = byName(all, "MemberValidatorTest");

        assertThat(integrationAnchor.isIntegration())
                .as("PostgresHarnessIT extends AbstractPostgresIT and owns a container. "
                        + "If this is false the integration lane is running nothing")
                .isTrue();

        assertThat(fastAnchor.isFastLane())
                .as("MemberValidatorTest is a plain unit test. If this is false the fast lane "
                        + "has started requiring Docker")
                .isTrue();
    }

    @Test
    @DisplayName("put every test class in exactly one lane, with none lost between them")
    void everyTestClassLandsInExactlyOneLane() {
        List<TestClassSource> all = TestSourceInventory.all();

        // Non-vacuity first: without it, a broken walker returns nothing and
        // every assertion below passes over an empty list.
        assertNonVacuousScan(all, Floors.TEST_CLASSES, "test classes under src/test/java");

        List<TestClassSource> integration = all.stream().filter(TestClassSource::isIntegration).toList();
        List<TestClassSource> fast = all.stream().filter(TestClassSource::isFastLane).toList();

        // Completeness, by discovery, carrying no number: the two lanes account
        // for every class found. A classifier that silently drops one passes
        // every per-class check and is caught only here.
        assertThat(integration.size() + fast.size())
                .as("classes in a lane, versus classes that exist. A class in neither lane "
                        + "never runs, and never running is indistinguishable from passing")
                .isEqualTo(all.size());

        assertThat(integration)
                .as("the lanes must be disjoint -- a class in both runs twice, against two "
                        + "different databases, and the second result silently wins")
                .doesNotContainAnyElementsOf(fast);

        assertThat(integration)
                .as("the integration lane is not empty")
                .isNotEmpty();
    }

    // The disabled population is asserted by DisabledPopulationTest, not here.
    //
    // This class carried that assertion while the population was empty and there
    // was nowhere better for it to live. It asserted the LIST OF CLASSES holding
    // a @Disabled, which was the most that could be checked before any defect
    // had been written down. DisabledPopulationTest asserts the stronger thing:
    // every @Disabled cites a named register row, every row is cited by a test,
    // and the counts match in both directions.
    //
    // Two guards asserting overlapping populations is how a floor ends up
    // copy-pasted into four places and stale in all four, so this one was moved
    // rather than kept alongside. Lane separation is what this class is for.

    private static TestClassSource byName(List<TestClassSource> all, String name) {
        return all.stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "Anchor class " + name + " not found. An anchor that has been renamed or "
                                + "deleted must be replaced deliberately -- silently dropping it "
                                + "would leave the lane logic unguarded."));
    }
}

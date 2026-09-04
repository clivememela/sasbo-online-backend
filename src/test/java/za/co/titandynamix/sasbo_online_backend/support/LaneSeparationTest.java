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
    private static final int SCAN_FLOOR = 5;

    /**
     * Every test class expected to contain a deliberately disabled test. Empty,
     * and it stays empty until a phase lands a defect assertion that is meant to
     * be red. Each entry added here must name the register row it asserts, in
     * the {@code @Disabled} reason, so the two cannot drift apart.
     */
    private static final List<String> DECLARED_DISABLED = List.of();

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
        assertNonVacuousScan(all, SCAN_FLOOR, "test classes under src/test/java");

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

    @Test
    @DisplayName("carry no disabled tests, because none is declared yet")
    void disabledPopulationIsExactlyWhatIsDeclared() {
        // The arc will later land roughly a dozen deliberately @Disabled tests,
        // each asserting a defect it does not fix. Individually each is
        // justified; collectively, a suite carrying a dozen disabled tests with
        // nothing counting them reads as neglected rather than deliberate.
        //
        // So the population is asserted EXACTLY, from the moment it is zero.
        // Adding a disabled test without adding it to DECLARED_DISABLED reddens
        // here -- and so does fixing the defect and leaving the test disabled,
        // which is the half that actually rots.
        List<TestClassSource> all = TestSourceInventory.all();
        assertNonVacuousScan(all, SCAN_FLOOR, "test classes scanned for @Disabled");

        List<String> disabled = all.stream()
                .filter(TestClassSource::hasDisabledAnnotation)
                .map(TestClassSource::name)
                .sorted()
                .toList();

        assertThat(disabled)
                .as("classes containing @Disabled, against the declared population. "
                        + "Nothing in this repository is disabled yet: no defect has been "
                        + "asserted-and-left-red, and nothing is skipped for unreliability -- "
                        + "quarantine is not a thing here")
                .isEqualTo(DECLARED_DISABLED);
    }

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

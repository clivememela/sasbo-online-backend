package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory.TestClassSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * A controller slice may not both switch the security filters off and make a
 * claim about security.
 *
 * <h2>Why this is the most dangerous convention in the suite</h2>
 * {@code @WebMvcTest} does <b>not</b> load the application's
 * {@code SecurityConfig}. It applies Spring Security's defaults, and those deny
 * an anonymous request — so a test asserting "an anonymous caller is denied",
 * written in a bare slice, <b>passes</b> while {@code permitAll()} sits
 * untouched in production.
 *
 * <p>{@code addFilters = false} is the mirror image: it removes the filter
 * chain entirely, so every authorization assertion in that class becomes
 * meaningless in the other direction.
 *
 * <p>Either way the failure is silent and reported green. The one test in this
 * suite whose entire purpose is to fail correctly is the one these mistakes
 * would disarm.
 *
 * <h2>Why it is a guard and not a comment</h2>
 * The rule was written down twice — in the reference controller slice and in
 * the security slice — and checked nowhere, across thirteen classes carrying
 * the annotation. Every comparable rule in this suite (layering, PII,
 * transaction boundaries, lane membership) was turned into a discovered guard;
 * this one had been left as prose, which is the weakest form a rule can take.
 *
 * <p>It costs one fast test, needs no Docker, and is written against the
 * <b>shape</b> of a test class rather than against today's thirteen — so the
 * fourteenth is covered because it exists.
 */
@DisplayName("Security slices")
class SecuritySliceTest {

    /**
     * Phrases that only appear when a test is making a claim about security.
     *
     * <p>Matched against {@code code()}, not {@code body()} -- comments are blanked
     * first, so a class that merely EXPLAINS the rule is not convicted for saying
     * the words. Its first run did exactly that, twice.
     */
    private static final List<String> AUTHORIZATION_CLAIMS = List.of(
            "isUnauthorized", "isForbidden", "@WithMockUser", "SecurityConfig");

    @Test
    @DisplayName("no slice both disables the filters and asserts something about authorization")
    void filtersOffMeansNoSecurityClaim() {
        List<TestClassSource> classes = TestSourceInventory.allExcept(SecuritySliceTest.class);
        assertNonVacuousScan(classes, 20, "test classes");

        List<TestClassSource> slices = classes.stream()
                .filter(c -> c.code().contains("@WebMvcTest"))
                .toList();
        assertNonVacuousScan(slices, 8, "controller slices");

        List<String> offenders = new ArrayList<>();
        for (TestClassSource slice : slices) {
            if (!slice.code().contains("addFilters = false")) {
                continue;
            }
            for (String claim : AUTHORIZATION_CLAIMS) {
                if (slice.code().contains(claim)) {
                    offenders.add(slice.name() + " disables the filters and mentions " + claim);
                }
            }
        }

        assertThat(offenders)
                .as("a slice with addFilters = false has no filter chain, so an authorization "
                        + "assertion in it proves nothing -- and a slice WITHOUT the real "
                        + "SecurityConfig asserts Spring Security's defaults rather than this "
                        + "application's. Either way the test passes for the wrong reason and "
                        + "nobody finds out. Split the security claim into its own class that "
                        + "imports SecurityConfig and does not disable the filters, as "
                        + "MemberControllerSecurityTest does")
                .isEmpty();
    }

    @Test
    @DisplayName("any slice claiming something about authorization imports the real SecurityConfig")
    void securityClaimsUseTheRealConfiguration() {
        // The other direction, and the one that silently disarms rather than
        // silently permits: without the real configuration a POPIA test asserts
        // the slice's defaults, goes green, and reports that the application is
        // safe when it is not.
        List<TestClassSource> slices = TestSourceInventory.allExcept(SecuritySliceTest.class)
                .stream()
                .filter(c -> c.code().contains("@WebMvcTest"))
                .toList();

        List<String> offenders = slices.stream()
                .filter(c -> c.code().contains("isUnauthorized") || c.code().contains("isForbidden"))
                .filter(c -> !c.code().contains("SecurityConfig.class"))
                .map(TestClassSource::name)
                .toList();

        assertThat(offenders)
                .as("these slices assert an authorization outcome without importing the "
                        + "application's SecurityConfig, so they are asserting Spring Security's "
                        + "defaults instead")
                .isEmpty();
    }

    @Test
    @DisplayName("the security slice this suite relies on is still shaped the way it has to be")
    void theSecuritySliceIsStillCorrectlyShaped() {
        // The named instance, asserted positively. The two scans above are
        // absence assertions -- they pass on an empty population -- so this is
        // what proves the shape they are checking for actually exists here.
        TestClassSource security = TestSourceInventory.all().stream()
                .filter(c -> c.name().equals("MemberControllerSecurityTest"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "MemberControllerSecurityTest is gone. It is the only test asserting "
                                + "that the member register is not readable anonymously; if it "
                                + "has been renamed, point this guard at the new name "
                                + "deliberately rather than deleting the check."));

        assertThat(security.code())
                .as("it must import the real SecurityConfig, or its assertion is about the "
                        + "slice's defaults rather than about production")
                .contains("SecurityConfig.class");
        assertThat(security.code())
                .as("it must NOT disable the filters, or there is no filter chain left to "
                        + "make the claim against")
                .doesNotContain("addFilters = false");
    }
}

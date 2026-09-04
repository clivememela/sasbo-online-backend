package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory.TestClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The coverage register. <b>This test is the contract; no number in any document
 * is.</b>
 *
 * <h2>Written last, deliberately</h2>
 * The instinct is to write this first, as the target. The opposite is right: its
 * canary numbers are derived <i>through its own discovery walker</i>, against
 * the real suite, with real headroom -- none of which is knowable until the
 * domains exist. Written first, it is a number somebody invented.
 *
 * <h2>It is TWO assertions, and they have opposite requirements</h2>
 * <ol>
 *   <li><b>Completeness -- by discovery, carrying NO number.</b> Every
 *       controller, every mapper, every custom-query repository, and the
 *       exception handler has a test. This moves with the codebase and cannot
 *       go stale, because it names no count.
 *   <li><b>A non-vacuity canary -- a number set deliberately BELOW the real
 *       one.</b> Its only job is to prove the walker walked. It is not a
 *       coverage target and must never be raised to match reality.
 * </ol>
 *
 * <p>Conflating them produces a floor equal to the actual, which is fragile
 * without being stricter: delete one controller legitimately and it reddens for
 * a reason nobody caused -- and whoever hits that lowers the threshold, at which
 * point the canary is gone. <b>&#9312; is what enforces coverage. &#9313; only
 * has to prove the walker is walking.</b>
 *
 * <h2>What it cannot do, and why JaCoCo is not a substitute</h2>
 * A line-coverage percentage cannot tell you a controller has no test
 * <i>file</i>. It will happily report 60% while two controllers are untouched.
 * The floor is the contract; a coverage report is the map.
 *
 * <h2>Its reddening change</h2>
 * Add a {@code @RestController} with no test file. This must go red naming it.
 */
@DisplayName("Coverage floor")
class CoverageFloorTest {

    /**
     * Canaries, measured 2026-09-04 through this class's own walker, each set
     * deliberately below the real count.
     *
     * <p>Actuals on that date: 12 controllers, 10 mappers, 15 repositories with
     * a declared query method, 19 service files. Never raise these to match; a
     * count expires, and a canary equal to the actual is fragile without being
     * stricter.
     */
    private static final int CONTROLLER_CANARY = 10;
    private static final int MAPPER_CANARY = 8;
    private static final int REPOSITORY_CANARY = 12;
    private static final int SERVICE_CANARY = 15;

    /** A repository method beyond the inherited CRUD -- what makes one testworthy. */
    private static final Pattern DECLARED_QUERY_METHOD =
            Pattern.compile("^\\s{4}(?!//)[A-Za-z@].*\\w+\\s*\\(", Pattern.MULTILINE);

    @Test
    @DisplayName("every controller has a test, named by this walker rather than by eye")
    void everyControllerHasATest() {
        List<MainClassSource> controllers = MainSourceInventory.inLayer(Layer.CONTROLLER);
        assertNonVacuousScan(controllers, CONTROLLER_CANARY, "controllers");

        assertThat(withoutATest(controllers))
                .as("controllers with no test file. Discovery, not a list -- a controller added "
                        + "next month is covered because it exists, and a hard-coded list would "
                        + "be a second thing to update that nobody will")
                .isEmpty();
    }

    @Test
    @DisplayName("every mapper has a test")
    void everyMapperHasATest() {
        List<MainClassSource> mappers = MainSourceInventory.inLayer(Layer.MAPPER);
        assertNonVacuousScan(mappers, MAPPER_CANARY, "mappers");

        assertThat(withoutATest(mappers))
                .as("mappers with no test file. A floor of 'at least eight mapper tests' is "
                        + "satisfied by testing the eight easiest; discovery is what makes the "
                        + "tenth covered the moment it exists -- and mappers are where the worst "
                        + "defect class hides")
                .isEmpty();
    }

    @Test
    @DisplayName("every repository declaring a query method is covered by an IT")
    void everyCustomQueryRepositoryHasAnIT() {
        // The layer the floor originally omitted, and the one where the DATABASE
        // decides the answer -- so it is the one layer H2 cannot stand in for.
        List<MainClassSource> repositories = MainSourceInventory.inLayer(Layer.REPOSITORY);
        assertNonVacuousScan(repositories, REPOSITORY_CANARY, "repositories");

        List<MainClassSource> withQueries = repositories.stream()
                .filter(CoverageFloorTest::declaresAQueryMethod)
                .toList();
        assertNonVacuousScan(withQueries, REPOSITORY_CANARY, "repositories with a query method");

        // "Covered by an IT" rather than "has a file called <Name>IT": several
        // repositories share one IT because they share a hierarchy, and building
        // that chain five times would be five places for the fixtures to drift.
        // What matters is that some IT exercises it.
        List<String> uncovered = new ArrayList<>();
        List<TestClassSource> integrationTests = TestSourceInventory.all().stream()
                .filter(TestClassSource::isIntegration)
                .toList();
        assertNonVacuousScan(integrationTests, 5, "integration test classes");

        for (MainClassSource repository : withQueries) {
            boolean covered = integrationTests.stream()
                    .anyMatch(it -> it.body().contains(repository.name()));
            if (!covered) {
                uncovered.add(repository.name() + " declares a query method and no IT uses it");
            }
        }

        assertThat(uncovered)
                .as("Spring Data wrote findById and save, and those are its tests to fail. These "
                        + "are the hand-written queries, the @Query bodies, the constraints and "
                        + "the ordering -- everything the database decides, which is exactly what "
                        + "the fast lane cannot be evidence about")
                .isEmpty();
    }

    @Test
    @DisplayName("the exception handler is on the register, like everything else")
    void theExceptionHandlerHasATest() {
        // It was untested, and it is the machinery that produces the envelope
        // every other controller test asserts on -- so all of those rested on it
        // by accident. Registered here so it cannot fall off again.
        List<MainClassSource> handlers = MainSourceInventory.inLayer(Layer.EXCEPTION).stream()
                .filter(c -> c.name().endsWith("Handler"))
                .toList();

        assertNonVacuousScan(handlers, 1, "exception handlers");
        assertThat(withoutATest(handlers)).isEmpty();
    }

    @Test
    @DisplayName("every service module is scanned, and the ones with logic have a test")
    void servicesAreScanned() {
        // Services are scanned rather than required one-for-one: several are
        // interfaces with no behaviour of their own, and demanding a test file
        // for an interface would be a box nobody can tick honestly.
        List<MainClassSource> services = MainSourceInventory.inLayer(Layer.SERVICE);
        assertNonVacuousScan(services, SERVICE_CANARY, "services");

        List<MainClassSource> implementations = services.stream()
                .filter(s -> s.code().contains("@Service"))
                .toList();
        assertNonVacuousScan(implementations, 5, "service implementations");

        // "A test constructs it" rather than "a file is named after it". Several
        // small services share one test class -- five near-empty files would
        // obscure rather than clarify -- and what matters is that something
        // exercises the service, not what the file is called.
        //
        // Constructing it is a deliberately strong signal: a controller slice
        // that merely MOCKS the service would satisfy a looser "mentions it"
        // rule while testing none of its behaviour.
        List<TestClassSource> tests = TestSourceInventory.all();
        List<String> untested = new ArrayList<>();
        for (MainClassSource implementation : implementations) {
            String constructed = "new " + implementation.name() + "(";
            boolean covered = tests.stream()
                    .anyMatch(test -> test.name().startsWith(implementation.name())
                            || test.body().contains(constructed));
            if (!covered) {
                untested.add(implementation.name());
            }
        }

        assertThat(untested)
                .as("service implementations no test constructs. An interface needs none -- it "
                        + "has no behaviour to get wrong -- but anything annotated @Service does")
                .isEmpty();
    }

    /** The classes in {@code candidates} that no test file is named for. */
    private static List<String> withoutATest(List<MainClassSource> candidates) {
        List<TestClassSource> tests = TestSourceInventory.all();
        assertNonVacuousScan(tests, 20, "test classes");

        List<String> missing = new ArrayList<>();
        for (MainClassSource candidate : candidates) {
            boolean covered = tests.stream()
                    .anyMatch(test -> test.name().startsWith(candidate.name()));
            if (!covered) {
                missing.add(candidate.name());
            }
        }
        return missing;
    }

    private static boolean declaresAQueryMethod(MainClassSource repository) {
        // Anything the interface declares itself, beyond what JpaRepository
        // already provides. The body has had its comments blanked, so a method
        // signature inside a comment cannot count.
        return DECLARED_QUERY_METHOD.matcher(repository.code()).find();
    }
}

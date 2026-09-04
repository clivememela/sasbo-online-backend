package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Each layer talks to the one below it, and to nothing else.
 *
 * <h2>Written against the layer, never against the class that prompted it</h2>
 * This guard names no controller and no service. It discovers them, so a
 * controller added next month is covered the moment it exists. A guard naming
 * only what prompted it would have closed this exact hole and left the hole
 * open.
 *
 * <h2>What it convicts, and why that matters</h2>
 * A controller reaching straight into a repository puts query logic in the
 * transport layer, where no service test can see it and no repository test
 * covers it. It is also how a rule ends up implemented twice and differently:
 * once in the service everyone knows about, once in the controller nobody does.
 *
 * <p>Comments are blanked before scanning, so a class explaining <i>why</i> it
 * does not use a repository is not convicted for saying the word.
 *
 * <h2>The exceptions are listed, with reasons, and asserted</h2>
 * {@link #KNOWN_VIOLATIONS} is not a suppression list that grows quietly. Every
 * entry is asserted to still be a violation: fix one and this test reddens
 * until the entry is removed, which is what stops the list outliving the
 * problem.
 */
@DisplayName("Layering")
class LayeringTest {

    /**
     * Controllers that reach past the service layer today, with what each is
     * doing. Both are simple read-through lookups that were never given a
     * service -- worth fixing, and a production change, so they are recorded
     * rather than repaired here.
     */
    private static final Map<String, String> KNOWN_VIOLATIONS = Map.of(
            "AreaController",
            "injects AreaRepository and maps rows to AreaDto inline; there is no AreaService",
            "RepresentativeRoleController",
            "injects RepresentativeRoleRepository directly; there is no service for roles");

    @Test
    @DisplayName("no controller reaches past the service layer into a repository")
    void controllersDoNotUseRepositories() {
        List<MainClassSource> controllers = MainSourceInventory.inLayer(Layer.CONTROLLER);

        // The canary. Without it a broken walker finds no controllers, reports
        // no offenders, and this test passes while asserting nothing.
        assertNonVacuousScan(controllers, 10, "controllers");

        List<String> offenders = new ArrayList<>();
        for (MainClassSource controller : controllers) {
            List<Integer> hits = controller.linesContaining(".repository.");
            if (hits.isEmpty() || KNOWN_VIOLATIONS.containsKey(controller.name())) {
                continue;
            }
            offenders.add(controller.at(hits.get(0)) + " imports a repository");
        }

        assertThat(offenders)
                .as("a controller that queries a repository directly puts query logic where no "
                        + "service test can see it, and is how one rule ends up implemented "
                        + "twice. Give it a service, or add it to KNOWN_VIOLATIONS with the "
                        + "reason -- and then be ready to justify the entry")
                .isEmpty();
    }

    @Test
    @DisplayName("every listed layering exception is still a real one")
    void knownViolationsAreStillViolations() {
        // The half that keeps the list honest. A suppression list nobody has to
        // maintain stops describing anything, and its third entry is invisible.
        List<MainClassSource> controllers = MainSourceInventory.inLayer(Layer.CONTROLLER);

        for (Map.Entry<String, String> entry : KNOWN_VIOLATIONS.entrySet()) {
            MainClassSource controller = controllers.stream()
                    .filter(c -> c.name().equals(entry.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            entry.getKey() + " is on KNOWN_VIOLATIONS but no such controller "
                                    + "exists. Remove the entry."));

            assertThat(controller.contains(".repository."))
                    .as("%s is listed as a layering exception -- \"%s\" -- but it no longer "
                            + "touches a repository. Delete the entry; the list may not outlive "
                            + "the problem", entry.getKey(), entry.getValue())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("no service depends on a controller")
    void servicesDoNotDependOnControllers() {
        List<MainClassSource> services = MainSourceInventory.inLayer(Layer.SERVICE);

        assertNonVacuousScan(services, 15, "services");

        List<String> offenders = services.stream()
                .filter(s -> s.contains(".controller."))
                .map(s -> s.at(s.linesContaining(".controller.").get(0)))
                .toList();

        assertThat(offenders)
                .as("a service importing a controller inverts the dependency and makes the "
                        + "business rule unreachable without the transport layer")
                .isEmpty();
    }

    @Test
    @DisplayName("no entity or DTO depends on a service or a repository")
    void dataTypesDependOnNothingAboveThem() {
        List<MainClassSource> dataTypes = new ArrayList<>();
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.ENTITY));
        dataTypes.addAll(MainSourceInventory.inLayer(Layer.DTO));

        assertNonVacuousScan(dataTypes, 30, "entities and DTOs");

        List<String> offenders = new ArrayList<>();
        for (MainClassSource type : dataTypes) {
            for (String forbidden : List.of(".services.", ".repository.", ".controller.")) {
                for (Integer line : type.linesContaining(forbidden)) {
                    offenders.add(type.at(line) + " -> " + forbidden);
                }
            }
        }

        assertThat(offenders)
                .as("a data type that knows about a service cannot be constructed in a test "
                        + "without dragging the whole application in behind it")
                .isEmpty();
    }
}

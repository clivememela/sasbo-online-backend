package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.Floors;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * An endpoint returning a collection has a bound on how much it can return.
 *
 * <h2>The failure it names</h2>
 * Nothing in this application paginates. Every list endpoint returns the whole
 * table, so the response grows with the database rather than with the request.
 * That is not a problem at 160 members and it is a very expensive one at
 * 160,000: the first symptom is a browser that stops responding, and the second
 * is a request that times out somewhere with no error anyone can act on.
 *
 * <p>{@code GET /api/members} is the clearest case -- it returns every member,
 * with every field, to any caller.
 *
 * <h2>Bounded means one of three things</h2>
 * <ul>
 *   <li>a {@code Pageable} parameter, or a {@code Page}/{@code Slice} return;
 *   <li>a {@code Top}/{@code First} derived query behind it;
 *   <li>a listing on {@link #BOUNDED_BY_NATURE} -- a set whose size the schema
 *       fixes, not one that merely happens to be small today.
 * </ul>
 *
 * <h2>The allowlist carries a ceiling, not just a name</h2>
 * "Small enough" is a claim with a number in it, and a claim with no number is
 * one nobody can check. Each entry says what bounds it and how large that bound
 * is, so the day a reference table stops being reference data somebody has
 * something concrete to disagree with.
 */
@DisplayName("Unbounded collections")
class UnboundedCollectionTest {

    /**
     * Endpoints whose result the schema itself bounds, with the ceiling. These
     * are configuration and reference data: their size is a property of the
     * migration chain, not of how long the application has been in use.
     */
    private static final Map<String, String> BOUNDED_BY_NATURE = Map.of(
            "BankController",
            "banks and their branches -- reference data seeded by migration; ceiling ~70 banks",
            "ProvinceController",
            "the nine South African provinces; ceiling 9, fixed by geography",
            "AreaController",
            "SASBO areas -- reference data; ceiling ~50",
            "RepresentativeRoleController",
            "representative roles seeded by V16; ceiling 6, asserted by the migration seed",
            "RecruitmentChannelController",
            "recruitment channels -- reference data; ceiling ~20",
            "DeletionCodeController",
            "deletion reason codes -- reference data; ceiling ~40",
            "JobGradeController",
            "job grades per bank -- reference data; ceiling 27, asserted by the migration seed",
            "UserAdminController",
            "sign-in's positions and roles, at most 100 each (UserAdministration refuses past that, TOO_MANY), and the "
                    + "permission catalogue, the enum's 18; its users are a page");
    // ACBController was here -- "ACB debit-day configuration, reference data", excusing a
    // controller that had no endpoints at all (register E7). Removed with its hand-written
    // `continue` below when ACB's first routes landed: the runs grow one a month and the lines by
    // thousands, so the guard now judges ACB like any other controller, and every ACB list is paged.

    private static final Pattern COLLECTION_ENDPOINT = Pattern.compile(
            "@GetMapping[^\\n]*\\n(?:\\s*@[^\\n]*\\n)*\\s*public\\s+(?:ResponseEntity<)?"
                    + "(List|Set|Collection)<");

    @Test
    @DisplayName("the scan finds the controllers and the list endpoints it is meant to grade")
    void theScanIsNotVacuous() {
        assertNonVacuousScan(MainSourceInventory.inLayer(Layer.CONTROLLER), Floors.CONTROLLERS, "controllers");
        assertNonVacuousScan(ENDPOINTS, Floors.COLLECTION_ENDPOINTS, "collection-returning endpoints");
    }

    @Test
    @Disabled(KnownDefects.COLLECTION_ENDPOINTS_ARE_UNBOUNDED)
    @DisplayName("every collection endpoint is bounded")
    void everyCollectionEndpointIsBounded() {
        List<String> unbounded = ENDPOINTS.stream()
                .filter(e -> !e.bounded())
                .map(Endpoint::describe)
                .toList();

        assertThat(unbounded)
                .as("these endpoints return the whole table. The response grows with the "
                        + "database rather than with the request, and the first symptom is a "
                        + "browser that stops responding")
                .isEmpty();
    }

    @Test
    @DisplayName("GET /api/members is named as unbounded, and still is")
    void membersIsStillUnbounded() {
        // The named instance. The disabled test above cannot report a
        // regression, because it is red by design -- so the specific case the
        // finding rests on is asserted separately and positively.
        List<Endpoint> memberEndpoints = ENDPOINTS.stream()
                .filter(e -> e.controller().equals("MemberController"))
                .toList();

        assertThat(memberEndpoints)
                .as("MemberController should still expose collection endpoints")
                .isNotEmpty();
        assertThat(memberEndpoints).allSatisfy(e -> assertThat(e.bounded())
                .as("%s is bounded now. If pagination has been added, remove this test and "
                        + "re-enable the guard above", e.describe())
                .isFalse());
    }

    @Test
    @DisplayName("every allowlisted controller still exists and still returns a collection")
    void theAllowlistIsStillAccurate() {
        List<String> controllers = MainSourceInventory.inLayer(Layer.CONTROLLER).stream()
                .map(MainClassSource::name)
                .toList();

        for (Map.Entry<String, String> entry : BOUNDED_BY_NATURE.entrySet()) {
            assertThat(controllers)
                    .as("%s is allowlisted as bounded-by-nature -- \"%s\" -- but no such "
                            + "controller exists. Remove the entry", entry.getKey(),
                            entry.getValue())
                    .contains(entry.getKey());

            // The half this test's own name promised and did not deliver. Only
            // asserting that the controller EXISTS would leave an entry sitting
            // on a controller that no longer returns a collection -- an
            // exemption for a rule it is no longer breaking, which is exactly
            // the stale-list rot every other guard here asserts against. It has
            // no exceptions now: the one it had, ACBController, left the list.
            assertThat(ENDPOINTS)
                    .as("%s is allowlisted as bounded-by-nature but no longer returns a "
                            + "collection. Delete the entry", entry.getKey())
                    .anyMatch(e -> e.controller().equals(entry.getKey()));
        }
    }

    private record Endpoint(String controller, int line, boolean bounded) {
        String describe() {
            return controller + ".java:" + line + " returns an unbounded collection";
        }
    }

    /** Computed once: the source it derives from cannot change mid-run. */
    private static final List<Endpoint> ENDPOINTS = collectionEndpoints();

    private static List<Endpoint> collectionEndpoints() {
        List<Endpoint> found = new ArrayList<>();
        for (MainClassSource controller : MainSourceInventory.inLayer(Layer.CONTROLLER)) {
            boolean allowlisted = BOUNDED_BY_NATURE.containsKey(controller.name());
            // Both depend on the controller, not on the individual match, so
            // they are computed once per file rather than once per endpoint.
            boolean paginated = controller.contains("Pageable")
                    || controller.contains("Page<")
                    || controller.contains("Slice<");
            Matcher matcher = COLLECTION_ENDPOINT.matcher(controller.code());
            while (matcher.find()) {
                int line = 1 + (int) controller.code()
                        .substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                found.add(new Endpoint(controller.name(), line, allowlisted || paginated));
            }
        }
        return found;
    }
}

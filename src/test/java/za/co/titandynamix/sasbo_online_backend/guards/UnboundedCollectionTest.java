package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.controller.MemberRegisterController;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterQuery;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;
import za.co.titandynamix.sasbo_online_backend.support.Floors;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
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
 * A list endpoint that returns the whole table grows with the database rather
 * than with the request. That is not a problem at 160 members and it is a very
 * expensive one at 160,000: the first symptom is a browser that stops
 * responding, and the second is a request that times out somewhere with no
 * error anyone can act on.
 *
 * <p>{@code GET /api/members} was the clearest case -- every member, with every
 * field, to any caller (register row A10). The register reads a page now:
 * {@code POST /api/members/register/search}, at most a hundred slim rows, from a
 * controller of its own -- a page, so no collection endpoint to this scan.
 * {@code GET /api/members} stays for the callers it still has, and is named in
 * {@link #STILL_UNBOUNDED} as what it still is. Its controller is kept free of
 * paged routes on purpose: the scan judges a controller by whether its source
 * pages anywhere, so a paged route beside it would grade it bounded.
 *
 * <h2>Bounded means one of these</h2>
 * <ul>
 *   <li>a {@code Pageable} parameter, or a {@code Page}/{@code Slice} return;
 *   <li>a controller on {@link #BOUNDED_BY_NATURE}, or an endpoint on
 *       {@link #BOUNDED_ENDPOINTS} -- a set whose size the schema fixes, not one
 *       that merely happens to be small today.
 * </ul>
 *
 * <h2>Every list carries its reason, and is checked both ways</h2>
 * "Small enough" is a claim with a number in it, and a claim with no number is
 * one nobody can check: each bounded entry says what bounds it and how large.
 * An endpoint that is still unbounded is named in {@link #STILL_UNBOUNDED} with
 * what it returns and why it is not paged yet -- asserted, not suppressed: an
 * entry whose endpoint is paged or gone reddens until it is removed, and a new
 * unbounded endpoint reddens until it is paged or named with a reason somebody
 * can disagree with. That is the guard the A10 row waited for, switched on.
 */
@DisplayName("Unbounded collections")
class UnboundedCollectionTest {

    /**
     * Controllers whose every collection the schema itself bounds, with the
     * ceiling. These are configuration and reference data: their size is a
     * property of the migration chain, not of how long the application has
     * been in use.
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
            "job grades per bank -- reference data; ceiling 27, asserted by the migration seed");
    // ACBController was here -- "ACB debit-day configuration, reference data", excusing a
    // controller that had no endpoints at all (register E7). Removed with its hand-written
    // `continue` below when ACB's first routes landed: the runs grow one a month and the lines by
    // thousands, so the guard now judges ACB like any other controller, and every ACB list is paged.

    /**
     * Single endpoints whose result the schema bounds, in a controller that also
     * serves something that is not -- named by controller and method, with the
     * ceiling, for the reason the controller entries carry one.
     */
    private static final Map<String, String> BOUNDED_ENDPOINTS = Map.of(
            "MemberController.getAllRepresentativeRoles",
            "the representative roles RepresentativeRoleController serves, seeded by V16; ceiling 6",
            "MemberController.getAllStructures",
            "SASBO's union structures, seeded by V16 and added to by no route; ceiling 19",
            "MemberController.getStructuresTree",
            "the same nineteen union structures, as their tree; ceiling 19",
            "MemberController.getPaymentTypeMetadata",
            "the PaymentType enum's values; ceiling 2, fixed by the enum");

    /**
     * Endpoints that are STILL UNBOUNDED, each with what it returns and why it is
     * not paged yet. Not an allowlist: an exception with its reason, checked to
     * be still real by {@link #theStillUnboundedAreStillUnbounded}.
     */
    private static final Map<String, String> STILL_UNBOUNDED = Map.of(
            "MemberController.getAll",
            "GET /api/members, every member with every field (register row A10) -- superseded for the register by "
                    + "POST /api/members/register/search, a page of at most a hundred. Kept for its remaining "
                    + "callers: the Recruiters screen on main (which the October build moves to a count on the "
                    + "server) and the automation suite's MembersApi; the route and this entry go when they have",
            "BankBranchController.search",
            "the bank branch offices, 68 seeded and added to by maintenance: a list grown by hand, not by the "
                    + "register; not A10's",
            "RecruiterController.getSelectableRecruiters",
            "SASBO's recruiters -- organisers and recruitment types, 33 seeded and added to by hand: dozens, "
                    + "never members; not A10's",
            "RepresentativeController.getAll",
            "representatives: members elected to a structure -- grows with elections, not with the register; "
                    + "not A10's",
            "SubscriptionsController.getAll",
            "subscription amounts, a row for each bank and each change of amount -- a few dozen a year; not A10's");

    /**
     * A {@code @GetMapping} whose method answers a {@code List}, {@code Set} or
     * {@code Collection} -- bare or in a {@code ResponseEntity} -- and its name.
     */
    private static final Pattern COLLECTION_ENDPOINT = Pattern.compile(
            "@GetMapping[^\\n]*\\n(?:\\s*@[^\\n]*\\n)*\\s*public\\s+(?:ResponseEntity<)?"
                    + "(List|Set|Collection)<[\\w.<>, ?]*>\\s+(\\w+)\\s*\\(");

    @Test
    @DisplayName("the scan finds the controllers and the list endpoints it is meant to grade")
    void theScanIsNotVacuous() {
        assertNonVacuousScan(MainSourceInventory.inLayer(Layer.CONTROLLER), Floors.CONTROLLERS, "controllers");
        assertNonVacuousScan(ENDPOINTS, Floors.COLLECTION_ENDPOINTS, "collection-returning endpoints");
    }

    @Test
    @DisplayName("every collection endpoint is bounded -- or named, with its reason, as still unbounded")
    void everyCollectionEndpointIsBounded() {
        List<String> unbounded = ENDPOINTS.stream()
                .filter(e -> !e.bounded() && !STILL_UNBOUNDED.containsKey(e.key()))
                .map(Endpoint::describe)
                .toList();

        assertThat(unbounded)
                .as("these endpoints return the whole table. The response grows with the "
                        + "database rather than with the request, and the first symptom is a "
                        + "browser that stops responding. Page it, or name it in STILL_UNBOUNDED "
                        + "with what it returns and why")
                .isEmpty();
    }

    @Test
    @DisplayName("every endpoint named as still unbounded still is -- one paged or gone reddens until its entry goes")
    void theStillUnboundedAreStillUnbounded() {
        for (Map.Entry<String, String> entry : STILL_UNBOUNDED.entrySet()) {
            assertThat(ENDPOINTS)
                    .as("%s is named as still unbounded -- \"%s\" -- but no such collection endpoint exists. "
                            + "Remove the entry", entry.getKey(), entry.getValue())
                    .anyMatch(e -> e.key().equals(entry.getKey()));
            assertThat(ENDPOINTS)
                    .as("%s is bounded now. Remove its STILL_UNBOUNDED entry", entry.getKey())
                    .noneMatch(e -> e.key().equals(entry.getKey()) && e.bounded());
        }
    }

    @Test
    @DisplayName("A10: the register reads a page of at most a hundred, and GET /api/members is named as what it still is")
    void theRegisterReadsAPage() throws NoSuchMethodException {
        Method search = MemberRegisterController.class.getMethod("search", MemberRegisterQuery.class);

        assertThat(search.getReturnType()).as("the register's route answers a page")
                .isEqualTo(MemberRegisterPageDto.class);
        assertThat(Collection.class.isAssignableFrom(search.getReturnType())).isFalse();
        assertThat(MemberRegisterService.MAX_PAGE_SIZE).as("a page's ceiling").isBetween(1, 100);
        assertThat(ENDPOINTS).as("the register's controller answers no whole collection")
                .noneMatch(e -> e.controller().equals("MemberRegisterController"));
        assertThat(ENDPOINTS).as("GET /api/members is still found by the scan, and still unbounded")
                .anyMatch(e -> e.key().equals("MemberController.getAll") && !e.bounded());
        assertThat(STILL_UNBOUNDED).containsKey("MemberController.getAll");
    }

    @Test
    @DisplayName("canary: a collection endpoint named nowhere is caught -- the scan reads a method's name")
    void theScanCatchesAnUnnamedEndpoint() {
        Matcher found = COLLECTION_ENDPOINT.matcher("    @GetMapping(\"/x\")\n    @Transactional(readOnly = true)\n"
                + "    public ResponseEntity<List<Map<String, Object>>> everythingInvented(@RequestParam int a) {");

        assertThat(found.find()).isTrue();
        assertThat(found.group(2)).isEqualTo("everythingInvented");
        assertThat(STILL_UNBOUNDED).doesNotContainKey("AnyController." + found.group(2));
    }

    @Test
    @DisplayName("every allowlisted controller and endpoint still exists and still returns a collection")
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
        for (Map.Entry<String, String> entry : BOUNDED_ENDPOINTS.entrySet()) {
            assertThat(ENDPOINTS)
                    .as("%s is allowlisted as bounded-by-nature -- \"%s\" -- but is no collection endpoint "
                            + "any more. Delete the entry", entry.getKey(), entry.getValue())
                    .anyMatch(e -> e.key().equals(entry.getKey()));
        }
    }

    private record Endpoint(String controller, String method, int line, boolean bounded) {
        String key() {
            return controller + "." + method;
        }

        String describe() {
            return controller + ".java:" + line + " " + method + "() returns an unbounded collection";
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
                String method = matcher.group(2);
                boolean bounded = allowlisted || paginated
                        || BOUNDED_ENDPOINTS.containsKey(controller.name() + "." + method);
                found.add(new Endpoint(controller.name(), method, line, bounded));
            }
        }
        return found;
    }
}

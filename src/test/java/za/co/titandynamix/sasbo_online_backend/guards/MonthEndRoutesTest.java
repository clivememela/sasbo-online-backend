package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.security.Permission;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Every route of month end lives in a controller of the arc (MD49), and every one is OPEN BY A WRITTEN REASON,
 * REFUSING (MV-34), or -- since the October build -- HELD BY A PERMISSION its handler names: a route nobody decided
 * about is a route that answers anyone by accident.
 *
 * <p>A bare {@code @SpringBootTest} on purpose, as {@code ApiSurfaceSnapshotTest} is, so the two share one context
 * and this boots nothing of its own.
 */
@SpringBootTest
@DisplayName("Month end's routes")
class MonthEndRoutesTest {

    /** Where the arc's routes live. A route here in a controller that is not the arc's is the finding. */
    static final Pattern ARC_ROUTE = Pattern.compile(
            "^(/api/reference/.*|/api/workplaces(/.*)?|/api/month-end(/.*)?|/api/members/summary"
                    + "|/api/members/\\{[^}]+}/status|/api/members/\\{[^}]+}/history|/api/members/register/.*)$");

    /**
     * The arc's routes that refuse every caller until sign-in exists (MD4), each named by the unit that adds it. None
     * since the October build: the take (04.2, 04.11) and the listings (04.3, 04.7, 04.8) are held by a permission.
     */
    static final Set<String> REFUSING = Set.of();

    /**
     * The arc's routes HELD BY A PERMISSION (October build, contracts/permissions.md), as "METHOD path" to the permission
     * the route's handler method names in {@code @PreAuthorize("hasAuthority('<PERMISSION>')")}. Read off the handler
     * by {@link #everyHeldRouteNamesItsPermission}: a route listed here whose method names another permission, or none,
     * is the finding -- and so is a handler of the arc that names one and is not listed.
     */
    static final Map<String, String> HELD_BY_PERMISSION = Map.ofEntries(
            // Each stream adds its own routes here, one entry a route, with the unit or answer that adds it.
            // 04.2, 04.11: taking a month, and taking it again -- by the same position (Stephne, MEN-10); sign-in.
            Map.entry("POST /api/month-end/{month}/take", "MONTH_END_TAKE"),
            // 04.3, 04.7, 04.8: a listing names members, within the caller's scope; sign-in.
            Map.entry("GET /api/month-end/{month}/listings/{listing}", "MEMBERS_VIEW"),
            // Register row A10: the register a page at a time, and its figures -- MEMBERS_VIEW for a signed-in caller,
            // only the caller's own region or area; isAnonymous() admits anyone while sign-in is open (OB8).
            Map.entry("POST /api/members/register/search", "MEMBERS_VIEW"),
            Map.entry("GET /api/members/register/overview", "MEMBERS_VIEW"),
            // reports-visits (5 Oct Show & Tell): the Location Listing's members at a branch -- a listing that names members.
            Map.entry("GET /api/workplaces/{id}/members", "MEMBERS_VIEW"),
            // reports-visits (5 Oct Show & Tell): the visit forms, saved as a draft and submitted -- a person's own drafts.
            Map.entry("GET /api/workplaces/visit-drafts", "VISITS_CAPTURE"),
            Map.entry("POST /api/workplaces/visit-drafts", "VISITS_CAPTURE"),
            Map.entry("GET /api/workplaces/visit-drafts/{id}", "VISITS_CAPTURE"),
            Map.entry("PUT /api/workplaces/visit-drafts/{id}", "VISITS_CAPTURE"),
            Map.entry("POST /api/workplaces/visit-drafts/{id}/submit", "VISITS_CAPTURE"),
            Map.entry("DELETE /api/workplaces/visit-drafts/{id}", "VISITS_CAPTURE"),
            // reports-visits review: the direct visit route, held as the drafts are -- it writes the same complement.
            Map.entry("POST /api/workplaces/{id}/visits", "VISITS_CAPTURE"),
            // reports-visits review: the batch print's listings, held by the catalogue's REPORTS_PRINT, and its members
            // in one read for the batch.
            Map.entry("GET /api/workplaces/listings", "REPORTS_PRINT"),
            Map.entry("GET /api/workplaces/members", "MEMBERS_VIEW"),
            // monthend-packs: who gets what -- the regions' recipients, the banks' files, the providers (MEN-02, MEN-33,
            // MEN-35) -- read and changed by a holder of MONTH_END_SEND.
            Map.entry("GET /api/month-end/settings", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/settings/recipients", "MONTH_END_SEND"),
            Map.entry("PUT /api/month-end/settings/recipients/{code}", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/settings/banks", "MONTH_END_SEND"),
            Map.entry("PUT /api/month-end/settings/banks/{code}", "MONTH_END_SEND"),
            Map.entry("PUT /api/month-end/settings/providers/{code}", "MONTH_END_SEND"),
            // monthend-packs: the regions' packs, made, downloaded or e-mailed, and the record of what was sent
            // (MEN-04, MEN-05, MEN-08).
            Map.entry("GET /api/month-end/{month}/region-packs", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/{month}/region-packs/{set}", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/{month}/region-packs/send", "MONTH_END_SEND"),
            Map.entry("GET /api/month-end/region-packs/{id}/{format}", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/region-packs/{id}/email", "MONTH_END_SEND"),
            Map.entry("GET /api/month-end/{month}/deliveries", "MONTH_END_SEND"),
            // monthend-packs: the banks' and providers' files, password-protected (MEN-05, MEN-32 to MEN-35).
            Map.entry("GET /api/month-end/{month}/files", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/{month}/files/banks/{code}", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/{month}/files/providers/{code}", "MONTH_END_SEND"),
            Map.entry("GET /api/month-end/files/{id}", "MONTH_END_SEND"),
            Map.entry("POST /api/month-end/files/{id}/sftp", "MONTH_END_SEND"),
            // Month end's rules, MEN-17: the reasons for no payroll deduction, kept by whoever maintains SASBO's lists.
            Map.entry("POST /api/reference/no-deduction-reasons", "REFERENCE_MAINTAIN"),
            Map.entry("PUT /api/reference/no-deduction-reasons/{id}", "REFERENCE_MAINTAIN"),
            // Month end's rules, MEN-31: a table code's core tier -- the Senior Secretariat decides, an administrator sets.
            Map.entry("PUT /api/reference/table-codes/{code}/core-tier", "REFERENCE_MAINTAIN"),
            // Month end's rules, MEN-18: listings that name members. MONTH_END_VIEW is held beside MEMBERS_VIEW --
            // Stephne, DOD-14: organisers do not see suspensions (OB30) -- which the controller tests prove.
            Map.entry("GET /api/month-end/suspensions/members", "MEMBERS_VIEW"),
            Map.entry("GET /api/month-end/suspensions/lapsed", "MEMBERS_VIEW")
    );

    /** The permission a {@code @PreAuthorize} expression names: {@code hasAuthority('MONTH_END_TAKE')}. */
    static final Pattern HAS_AUTHORITY = Pattern.compile("hasAuthority\\('([A-Z_]+)'\\)");

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    /** Every route, as METHOD path, with whether its controller carries {@link MonthEndApi}. */
    private Map<String, Boolean> routes() {
        Map<String, Boolean> found = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            boolean arcs = AnnotatedElementUtils.hasAnnotation(entry.getValue().getBeanType(), MonthEndApi.class);
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String path : entry.getKey().getPatternValues()) {
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    found.put(method.name() + " " + path, arcs);
                }
            }
        }
        return found;
    }

    /**
     * Every route of the arc, as METHOD path, with the permission its handler method's {@code @PreAuthorize} names --
     * {@code null} where the method carries none, or one that names no permission by {@link #HAS_AUTHORITY}.
     */
    private Map<String, String> declaredPermissions() {
        Map<String, String> found = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            if (!AnnotatedElementUtils.hasAnnotation(entry.getValue().getBeanType(), MonthEndApi.class)) {
                continue;
            }
            PreAuthorize held = AnnotatedElementUtils.findMergedAnnotation(entry.getValue().getMethod(),
                    PreAuthorize.class);
            String permission = held == null ? null : permissionOf(held.value());
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String path : entry.getKey().getPatternValues()) {
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    found.put(method.name() + " " + path, permission);
                }
            }
        }
        return found;
    }

    /** The permission an expression names, or {@code null} where it names none. */
    static String permissionOf(String expression) {
        Matcher named = HAS_AUTHORITY.matcher(expression == null ? "" : expression);
        return named.find() ? named.group(1) : null;
    }

    @Test
    @DisplayName("🔒 every route held by a permission names it on its handler, and every handler of the arc that names one is listed")
    void everyHeldRouteNamesItsPermission() {
        Map<String, String> declared = declaredPermissions();
        assertNonVacuousScan(List.copyOf(declared.keySet()), 10, "routes of the arc");
        Set<String> wrong = new TreeSet<>();
        HELD_BY_PERMISSION.forEach((route, permission) -> {
            if (!declared.containsKey(route)) {
                wrong.add(route + " is listed as held by " + permission + ", and is no route of the arc");
            } else if (!permission.equals(declared.get(route))) {
                wrong.add(route + " is listed as held by " + permission + ", and its handler names "
                        + declared.get(route));
            }
        });
        declared.forEach((route, permission) -> {
            if (permission != null && !HELD_BY_PERMISSION.containsKey(route)) {
                wrong.add(route + " names " + permission + " on its handler, and is not listed as held by it");
            }
        });
        assertThat(wrong).as("a permission a route is said to hold, and the one its handler enforces, are one").isEmpty();
        assertThat(Arrays.stream(Permission.values()).map(Enum::name).toList())
                .as("every permission listed is a name from security.Permission")
                .containsAll(HELD_BY_PERMISSION.values());
        Set<String> both = new TreeSet<>(HELD_BY_PERMISSION.keySet());
        both.retainAll(MonthEndGuardsTest.OPEN_BY_REASON.keySet());
        both.addAll(HELD_BY_PERMISSION.keySet().stream().filter(REFUSING::contains).toList());
        assertThat(both).as("a route is open, refusing or held by a permission -- one of the three").isEmpty();
    }

    @Test
    @DisplayName("canary: the permission predicate reads hasAuthority's name, and nothing from an expression that names none")
    void thePermissionPredicateCanFail() {
        assertThat(permissionOf("hasAuthority('MONTH_END_TAKE')")).isEqualTo("MONTH_END_TAKE");
        assertThat(permissionOf("isAuthenticated() and hasAuthority('MEMBERS_VIEW')")).isEqualTo("MEMBERS_VIEW");
        assertThat(permissionOf("permitAll()")).isNull();
        assertThat(permissionOf("hasRole('ADMIN')")).isNull();
        assertThat(permissionOf(null)).isNull();
    }

    @Test
    @DisplayName("MD49: every route under the arc's addresses is in a controller of the arc, and the arc has routes nowhere else")
    void everyRouteIsInAControllerOfTheArc() {
        Map<String, Boolean> routes = routes();
        assertNonVacuousScan(List.copyOf(routes.keySet()), 30, "routes");
        Set<String> strays = new TreeSet<>();
        for (Map.Entry<String, Boolean> route : routes.entrySet()) {
            String path = route.getKey().substring(route.getKey().indexOf(' ') + 1);
            if (ARC_ROUTE.matcher(path).matches() != route.getValue()) {
                strays.add(route.getKey() + (route.getValue() ? " is the arc's, at an address that is not"
                        : " is at the arc's address, in a controller that is not the arc's"));
            }
        }
        assertThat(strays)
                .as("a month-end route outside a controller of the arc answers a malformed request 500 (A63), and "
                        + "is watched by none of the arc's guards")
                .isEmpty();
    }

    @Test
    @DisplayName("canary: the address predicate knows the arc's addresses from everyone else's")
    void theAddressPredicateCanFail() {
        assertThat(ARC_ROUTE.matcher("/api/reference/regions").matches()).isTrue();
        assertThat(ARC_ROUTE.matcher("/api/members/{id}/status").matches()).isTrue();
        assertThat(ARC_ROUTE.matcher("/api/members/register/search").matches()).isTrue();
        assertThat(ARC_ROUTE.matcher("/api/members/{id}").matches()).isFalse();
        assertThat(ARC_ROUTE.matcher("/api/members").matches()).isFalse();
        assertThat(ARC_ROUTE.matcher("/api/workplacesX").matches()).isFalse();
    }

    @Test
    @DisplayName("🔒 MV-34: every route of the arc is open by a written reason, refuses, or is held by a permission -- and the lists name only real routes")
    void everyRouteIsDecided() {
        Set<String> arcs = new TreeSet<>();
        routes().forEach((route, isArcs) -> {
            if (isArcs) {
                arcs.add(route);
            }
        });
        assertNonVacuousScan(List.copyOf(arcs), 10, "routes of the arc");
        Set<String> undecided = new TreeSet<>(arcs);
        undecided.removeAll(MonthEndGuardsTest.OPEN_BY_REASON.keySet());
        undecided.removeAll(REFUSING);
        undecided.removeAll(HELD_BY_PERMISSION.keySet());
        assertThat(undecided).as("routes of the arc that are neither open by a written reason, refusing, nor held by a "
                + "permission").isEmpty();
        Set<String> gone = new TreeSet<>(MonthEndGuardsTest.OPEN_BY_REASON.keySet());
        gone.addAll(REFUSING);
        gone.addAll(HELD_BY_PERMISSION.keySet());
        gone.removeAll(arcs);
        assertThat(gone).as("entries that are no route of the arc -- take them off the list").isEmpty();
        assertThat(MonthEndGuardsTest.OPEN_BY_REASON.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
    }
}

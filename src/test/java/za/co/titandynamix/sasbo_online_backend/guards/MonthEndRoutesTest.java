package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Every route of month end lives in a controller of the arc (MD49), and every one is OPEN BY A WRITTEN REASON OR
 * REFUSING (MV-34): a route nobody decided about is a route that answers anyone by accident.
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
                    + "|/api/members/\\{[^}]+}/status|/api/members/\\{[^}]+}/history)$");

    /** The arc's routes that refuse every caller until sign-in exists (MD4), each named by the unit that adds it. */
    static final Set<String> REFUSING = Set.of(
            // 04.2: taking a month freezes its figures for good (MD4); proved refusing by MonthEndControllerTest.
            "POST /api/month-end/{month}/take",
            // 04.3, 04.7, 04.8: a listing names members; proved refusing by MonthEndStatisticsControllerSecurityTest.
            "GET /api/month-end/{month}/listings/{listing}");

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
        assertThat(ARC_ROUTE.matcher("/api/members/{id}").matches()).isFalse();
        assertThat(ARC_ROUTE.matcher("/api/members").matches()).isFalse();
        assertThat(ARC_ROUTE.matcher("/api/workplacesX").matches()).isFalse();
    }

    @Test
    @DisplayName("🔒 MV-34: every route of the arc is open by a written reason, or refuses -- and the list names only real routes")
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
        assertThat(undecided).as("routes of the arc that are neither open by a written reason nor refusing").isEmpty();
        Set<String> gone = new TreeSet<>(MonthEndGuardsTest.OPEN_BY_REASON.keySet());
        gone.addAll(REFUSING);
        gone.removeAll(arcs);
        assertThat(gone).as("entries that are no route of the arc -- take them off the list").isEmpty();
        assertThat(MonthEndGuardsTest.OPEN_BY_REASON.values()).allSatisfy(reason -> assertThat(reason).isNotBlank());
    }
}

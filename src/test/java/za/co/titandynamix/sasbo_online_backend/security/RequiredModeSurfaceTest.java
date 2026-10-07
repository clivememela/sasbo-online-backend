package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * Required mode over the WHOLE routed surface (signin.md): every {@code /api} route the application serves -- every
 * stream's, read off the handler mapping, so a route added next week is asked because it exists -- refuses nobody signed
 * in with {@code 401 SIGN_IN_REQUIRED}, except signing in and the mode. The health check stays open.
 *
 * <p>The application's real chain, filters on, the seed stood down. A request is refused before any handler is chosen,
 * so a path variable needs no real value and a body none at all.
 */
@SpringBootTest(properties = {
        "sasbo.signin.mode=required",
        "spring.datasource.url=jdbc:h2:mem:required-mode-surface;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@DisplayName("Required mode, over every route")
class RequiredModeSurfaceTest {

    /** The two routes that answer nobody signed in, in required mode (signin.md). */
    static final Set<String> OPEN = Set.of("POST /api/auth/login", "GET /api/auth/mode");

    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Test
    @DisplayName("🔒 every /api route refuses nobody signed in, 401 SIGN_IN_REQUIRED -- but signing in and the mode")
    void everyRouteNeedsASession() throws Exception {
        Set<String> asked = new TreeSet<>();
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mapping.getHandlerMethods().entrySet()) {
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : entry.getKey().getPatternValues()) {
                if (!pattern.startsWith("/api/")) {
                    continue;
                }
                for (RequestMethod method : methods.isEmpty() ? Set.of(RequestMethod.GET) : methods) {
                    String route = method.name() + " " + pattern;
                    asked.add(route);
                    MvcResult answer = mockMvc.perform(request(HttpMethod.valueOf(method.name()),
                            pattern.replaceAll("\\{[^}]+}", "1"))).andReturn();
                    boolean refused = answer.getResponse().getStatus() == 401
                            && answer.getResponse().getContentAsString().contains("\"code\":\"SIGN_IN_REQUIRED\"");
                    if (OPEN.contains(route) == refused) {
                        wrong.add(route + " answered " + answer.getResponse().getStatus());
                    }
                }
            }
        }
        assertNonVacuousScan(List.copyOf(asked), 100, "/api routes");
        assertThat(asked).containsAll(OPEN);
        assertThat(wrong).as("routes that answer nobody signed in in required mode, or open routes that refuse them")
                .isEmpty();
    }

    @Test
    @DisplayName("the health check answers nobody signed in, as Render's check and the suite's need")
    void theHealthCheckStaysOpen() throws Exception {
        assertThat(mockMvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus()).isNotEqualTo(401);
    }
}

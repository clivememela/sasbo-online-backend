package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A route that needs a permission answers 403 to a caller without it -- not the 500 a catch-all handler would make of
 * the refusal. Both catch-alls are covered: the application's own ({@code GlobalExceptionHandler}) and month end's
 * scoped one ({@code MonthEndExceptionHandler}, which every {@code @MonthEndApi} controller answers through first).
 *
 * <p>The two controllers below exist only here. The must-pass twins hold the permission and get a 200 through the same
 * chain, so a 403 can only be the permission refusing.
 */
@WebMvcTest(controllers = {SecurityRefusalAnswersTest.GuardedRoutes.class, SecurityRefusalAnswersTest.GuardedMonthEndRoutes.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class,
        SecurityRefusalAnswersTest.GuardedRoutes.class, SecurityRefusalAnswersTest.GuardedMonthEndRoutes.class})
@DisplayName("A permission refused by method security answers 403, not 500")
class SecurityRefusalAnswersTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("an ordinary route: a user without the permission is refused 403")
    void anOrdinaryRouteRefusesWith403() throws Exception {
        mockMvc.perform(get("/test/guarded")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "USERS_ADMIN")
    @DisplayName("an ordinary route: the permission's holder is answered")
    void anOrdinaryRouteAnswersItsHolder() throws Exception {
        mockMvc.perform(get("/test/guarded")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("an ordinary route: nobody signed in is refused, and not with a 500")
    void anOrdinaryRouteRefusesAnAnonymousCaller() throws Exception {
        mockMvc.perform(get("/test/guarded")).andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a month-end route: refused 403 through month end's own handler")
    void aMonthEndRouteRefusesWith403() throws Exception {
        mockMvc.perform(get("/test/guarded-month-end")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_TAKE")
    @DisplayName("a month-end route: the permission's holder is answered")
    void aMonthEndRouteAnswersItsHolder() throws Exception {
        mockMvc.perform(get("/test/guarded-month-end")).andExpect(status().isOk());
    }

    @RestController
    static class GuardedRoutes {
        @GetMapping("/test/guarded")
        @PreAuthorize("hasAuthority('USERS_ADMIN')")
        String guarded() {
            return "answered";
        }
    }

    @RestController
    @MonthEndApi
    static class GuardedMonthEndRoutes {
        @GetMapping("/test/guarded-month-end")
        @PreAuthorize("hasAuthority('MONTH_END_TAKE')")
        String guarded() {
            return "answered";
        }
    }
}

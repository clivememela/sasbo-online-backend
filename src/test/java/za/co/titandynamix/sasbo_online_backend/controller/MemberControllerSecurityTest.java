package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapperImpl;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;
import za.co.titandynamix.sasbo_online_backend.services.impl.WelcomeLetterDispatcher;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.NoTransactions;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whether an anonymous caller may read the member register.
 *
 * <h2>Separate from {@code MemberControllerTest}, and that is the point</h2>
 * That class runs with {@code addFilters = false}, which is correct there: it
 * asserts transport and binding and makes no claim about who may call anything.
 * <b>Any slice making a claim about authorization imports the real
 * configuration or makes no such claim.</b> Mixing the two in one file means
 * one annotation decides both, and the wrong one is invisible.
 *
 * <h2>The trap this file exists to avoid</h2>
 * {@code @WebMvcTest} does <b>not</b> load the application's
 * {@code SecurityConfig}. It applies Spring Security's defaults instead -- and
 * those deny an anonymous request. So a POPIA test written in a bare slice
 * <b>passes</b>, cheerfully, while {@code permitAll()} sits untouched in
 * production.
 *
 * <p>That would be the worst possible outcome: the one test in this suite whose
 * entire purpose is to fail correctly, silently disarmed and reported green.
 * Hence {@code @Import(SecurityConfig.class)} on the line below, and hence no
 * {@code addFilters = false} anywhere in this file.
 *
 * <h2>Why it is here and not in a browser journey</h2>
 * A browser journey is a slow and indirect way to assert an authorization rule.
 * The fast controller test is the right home; a journey-level check is
 * corroboration, not the assertion.
 */
@WebMvcTest(MemberController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class,
        MemberMapperImpl.class, WelcomeLetterDispatcher.class, NoTransactions.class})
@DisplayName("MemberController, with the application's real security configuration")
class MemberControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private RepresentativeService representativeService;

    // MemberMapperImpl is imported for real, so its three @Autowired
    // repositories have to exist. They are only used on the toEntity path,
    // which this slice does not exercise -- but the context will not start
    // without them.
    @MockitoBean
    private RepresentativeRepository representativeRepository;

    @MockitoBean
    private UnionStructureRepository unionStructureRepository;

    @MockitoBean
    private RepresentativeRoleRepository representativeRoleRepository;

    @Test
    @Disabled(KnownDefects.POPIA_ANONYMOUS_READ_IS_ALLOWED)
    @DisplayName("an anonymous caller cannot read the member register")
    void anonymousReadIsDenied() throws Exception {
        // Written to the standard, and expected to fail. /api/** is permitAll(),
        // so this register -- names, identity numbers, dates of birth and bank
        // account numbers for real people -- is readable by anyone who can
        // reach the port.
        //
        // Enforcing authentication is a production change and deliberately out
        // of scope here. This test turns green the day it happens.
        when(this.memberService.findAll()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("the real SecurityConfig is loaded, so the assertion above is about production")
    void theRealSecurityConfigIsLoaded() throws Exception {
        // The guard on the guard. If SecurityConfig were not imported, Spring
        // Security's defaults would apply and this request would be rejected --
        // which would make the disabled test above pass for a reason that has
        // nothing to do with the application.
        //
        // So: with the REAL configuration, an anonymous GET is permitted. That
        // is the defect, stated positively, and it is what proves the test
        // above is pointed at production rather than at a slice default.
        when(this.memberService.findAll()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the CORS policy is actually WIRED to the filter chain, not merely declared")
    void theCorsPolicyIsWired() throws Exception {
        // 🔴 THE ASSERTION `CorsPolicyTest` CANNOT MAKE, and the reason both files
        // exist. That one constructs `SecurityConfig` directly and asserts what
        // the `CorsConfigurationSource` bean CONTAINS -- which is the right way to
        // check the origin list cheaply, and is completely blind to whether
        // anything consults it. Delete `.cors(...)` from `filterChain` and CORS
        // stops working entirely while every assertion over there stays green:
        // the bean is still perfect, and nothing asks it anything.
        //
        // A preflight is the only thing that proves the wiring, because it is the
        // request the browser actually sends and the filter chain is what answers
        // it. This slice already boots the real `SecurityConfig`, so the context
        // is paid for.
        this.mockMvc.perform(options("/api/members")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test
    @DisplayName("a preflight from an origin NOT on the list is refused")
    void anUnknownOriginIsRefused() throws Exception {
        // The other direction, and the one that distinguishes "CORS is wired" from
        // "CORS allows everybody". Without it, an `allowedOriginPatterns("*")`
        // would satisfy the test above perfectly -- the header comes back, the
        // preflight succeeds, and the API is open to the internet with cookies.
        //
        // 🔑 Spring answers a disallowed preflight with 403 and NO
        // `Access-Control-Allow-Origin`. Both are asserted: the status alone would
        // also match a route that simply does not exist.
        this.mockMvc.perform(options("/api/members")
                        .header("Origin", "https://not-a-sasbo-origin.invalid")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}

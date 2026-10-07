package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterOverviewDto;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.ScopedPrincipal;
import za.co.titandynamix.sasbo_online_backend.services.MemberRegisterService;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The register's two routes under the application's REAL security configuration, filter chain on (register row A10,
 * contracts/permissions.md and signin.md):
 * <ul>
 *   <li>an anonymous caller is let through -- an anonymous caller reaches a route only while sign-in is open, and the
 *       register is open then, as it is today;</li>
 *   <li>a signed-in caller needs {@code MEMBERS_VIEW}; one without it is refused with a 403 -- not the 500 the arc's
 *       catch-all made of every refusal of a permission before {@code MonthEndExceptionHandler} threw them on;</li>
 *   <li>a signed-in caller's scope is the scope the service is asked with.</li>
 * </ul>
 */
@WebMvcTest(MemberRegisterController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MemberRegisterController, with the application's real security configuration")
class MemberRegisterControllerSecurityTest {

    /** A signed-in caller as the sign-in filter will put one in the context: a name and how far it may see. */
    record SignedIn(String username, String displayName, CallerScope scope) implements ScopedPrincipal {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberRegisterService register;

    private static MemberRegisterPageDto empty() {
        return new MemberRegisterPageDto(List.of(), 0, 50, 0, 0, "surname,asc");
    }

    private static MemberRegisterOverviewDto none() {
        return new MemberRegisterOverviewDto(0, 0, 0, 0, 0, 0, 0, List.of(), "2026-10-06");
    }

    @Test
    @WithAnonymousUser
    @DisplayName("open while sign-in is open: an anonymous caller reads a page, for the whole register")
    void anAnonymousCallerReadsAPage() throws Exception {
        when(register.page(any(), any())).thenReturn(empty());

        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        verify(register).page(any(), eq(CallerScope.all()));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("open while sign-in is open: an anonymous caller reads the figures")
    void anAnonymousCallerReadsTheFigures() throws Exception {
        when(register.overview(CallerScope.all())).thenReturn(none());

        mockMvc.perform(get("/api/members/register/overview")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a signed-in caller with MEMBERS_VIEW reads a page")
    void membersViewReadsAPage() throws Exception {
        when(register.page(any(), any())).thenReturn(empty());

        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("🔒 a signed-in caller without MEMBERS_VIEW is refused a page with a 403 -- not a 500 -- and nothing is read")
    void withoutMembersViewAPageIsRefused() throws Exception {
        mockMvc.perform(post("/api/members/register/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(register);
    }

    @Test
    @WithMockUser(authorities = "PAYROLL_PROCESS")
    @DisplayName("🔒 a signed-in caller without MEMBERS_VIEW is refused the figures with a 403")
    void withoutMembersViewTheFiguresAreRefused() throws Exception {
        mockMvc.perform(get("/api/members/register/overview")).andExpect(status().isForbidden());

        verifyNoInteractions(register);
    }

    @Test
    @DisplayName("🔒 an organiser signed in for area A is asked for with area A's scope -- the page and the figures")
    void aSignedInCallersScopeIsTheScopeAsked() throws Exception {
        CallerScope areaA = CallerScope.areas(Set.of("A"));
        UsernamePasswordAuthenticationToken organiser = new UsernamePasswordAuthenticationToken(
                new SignedIn("invented.organiser", "Invented Organiser", areaA), null,
                List.of(new SimpleGrantedAuthority("MEMBERS_VIEW")));
        when(register.page(any(), any())).thenReturn(empty());
        when(register.overview(areaA)).thenReturn(none());

        mockMvc.perform(post("/api/members/register/search").with(authentication(organiser))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/members/register/overview").with(authentication(organiser)))
                .andExpect(status().isOk());

        verify(register).page(any(), eq(areaA));
        verify(register).overview(areaA);
    }
}

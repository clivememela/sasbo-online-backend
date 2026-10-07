package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ModeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SessionDto;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSignInSliceTest;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Required mode (signin.md; the owner switches it before go-live): every {@code /api} route needs a session except
 * {@code POST /api/auth/login}, {@code GET /api/auth/mode} and the health check -- and there is no anonymous caller, so
 * the register's {@code isAnonymous()} opens nothing.
 */
@TestPropertySource(properties = "sasbo.signin.mode=required")
@DisplayName("Sign-in, required mode")
class SignInRequiredModeTest extends AbstractSignInSliceTest {

    @Test
    @DisplayName("nobody signed in is refused a route that names no permission: 401 SIGN_IN_REQUIRED")
    void anOpenRouteNeedsASession() throws Exception {
        mockMvc.perform(get("/api/test/open"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
    }

    @Test
    @DisplayName("signing in, the mode and the health check answer nobody signed in")
    void theThreeDoorsStayOpen() throws Exception {
        when(signIn.signIn(any(), anyString(), any())).thenReturn(new SessionDto(SignInTestUsers.tokenFor("t"),
                "2026-10-07T20:00:00Z", null));
        when(signIn.mode()).thenReturn(new ModeDto("required"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.organiser\", \"password\": \"an invented passphrase\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("required"));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("…but only those: GET on the login address, and anything else under /api/auth, needs a session")
    void onlyThoseThree() throws Exception {
        mockMvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
    }

    @Test
    @DisplayName("the register's isAnonymous(): nobody signed in never reaches it -- 401, not the anonymous caller's answer")
    void theRegisterIsNotOpenToNobody() throws Exception {
        mockMvc.perform(get("/api/test/register"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
    }

    @Test
    @DisplayName("a signed-in user reaches an open route; a held one by its permission alone")
    void aSignedInUserIsJudgedByTheirPermissions() throws Exception {
        SignedInUser cfo = SignInTestUsers.user("invented.cfo", CallerScope.all(), Permission.DOD_FILE);
        mockMvc.perform(get("/api/test/open").with(as(cfo))).andExpect(status().isOk());
        mockMvc.perform(get("/api/test/register").with(as(cfo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("MEMBERS_VIEW"));
        SignedInUser viewer = SignInTestUsers.user("invented.viewer", CallerScope.all(), Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/register").with(as(viewer))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token that opens nothing any more is 401 SESSION_EXPIRED everywhere a session is needed")
    void anEndedSessionIsSaid() throws Exception {
        mockMvc.perform(get("/api/test/open").with(ended()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("a temporary password opens the user's own sign-in routes and nothing else: 403 PASSWORD_CHANGE_REQUIRED")
    void aTemporaryPasswordOpensOnlyItsOwnSignIn() throws Exception {
        SignedInUser temporary = SignInTestUsers.temporary("invented.newcomer", Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/open").with(as(temporary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/auth/me").with(as(temporary))).andExpect(status().isOk());
    }
}

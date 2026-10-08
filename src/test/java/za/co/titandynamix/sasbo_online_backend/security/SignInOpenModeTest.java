package za.co.titandynamix.sasbo_online_backend.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSignInSliceTest;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Open mode, the default (signin.md; the owner, 6 Oct: the link runs open until SASBO's staff have accounts): every
 * route answers as it did, a route that names a permission needs a signed-in holder, and a token, when sent, says who
 * is calling everywhere.
 */
@DisplayName("Sign-in, open mode")
class SignInOpenModeTest extends AbstractSignInSliceTest {

    @Test
    @DisplayName("a route that names no permission answers nobody signed in, as every route did before sign-in")
    void anOpenRouteAnswersAnyone() throws Exception {
        mockMvc.perform(get("/api/test/open")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a route that names a permission refuses nobody signed in: 401 SIGN_IN_REQUIRED, the contract's sentence")
    void aHeldRouteRefusesNobody() throws Exception {
        mockMvc.perform(get("/api/test/members"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Please sign in to use this part of SASBO Online."))
                .andExpect(jsonPath("$.path").value("/api/test/members"))
                .andExpect(jsonPath("$.permission").doesNotExist());
    }

    @Test
    @DisplayName("…and answers its holder through the real token filter")
    void aHeldRouteAnswersItsHolder() throws Exception {
        SignedInUser holder = SignInTestUsers.user("invented.viewer", CallerScope.all(), Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/members").with(as(holder))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a signed-in user without it is 403 PERMISSION_REQUIRED, the permission named at the top level and in details")
    void aHeldRouteRefusesAUserWithoutIt() throws Exception {
        SignedInUser other = SignInTestUsers.user("invented.cfo", CallerScope.all(), Permission.DOD_FILE);
        mockMvc.perform(get("/api/test/members").with(as(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.message").value("You do not have permission to do this. Ask a user "
                        + "administrator if your work needs it."))
                .andExpect(jsonPath("$.permission").value("MEMBERS_VIEW"))
                .andExpect(jsonPath("$.details.permission").value("MEMBERS_VIEW"));
    }

    @Test
    @DisplayName("a route held by two permissions names neither rather than guess")
    void twoPermissionsNameNeither() throws Exception {
        SignedInUser other = SignInTestUsers.user("invented.viewer", CallerScope.all(), Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/either").with(as(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").doesNotExist());
    }

    @Test
    @DisplayName("register-paging's shape, isAnonymous() or MEMBERS_VIEW: the anonymous filter is on, so nobody signed in is answered")
    void theRegisterIsOpenToNobodySignedIn() throws Exception {
        mockMvc.perform(get("/api/test/register")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("…a signed-in user without MEMBERS_VIEW is judged by what they hold: 403; with it, answered")
    void theRegisterJudgesASignedInUserByTheirPermissions() throws Exception {
        SignedInUser cfo = SignInTestUsers.user("invented.cfo", CallerScope.all(), Permission.DOD_FILE);
        mockMvc.perform(get("/api/test/register").with(as(cfo)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("MEMBERS_VIEW"));
        SignedInUser viewer = SignInTestUsers.user("invented.viewer", CallerScope.all(), Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/register").with(as(viewer))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a token that opens nothing any more: an open route still answers; a held one says SESSION_EXPIRED")
    void anEndedSessionIsSaidWhereASessionIsNeeded() throws Exception {
        mockMvc.perform(get("/api/test/open").with(ended())).andExpect(status().isOk());
        mockMvc.perform(get("/api/test/members").with(ended()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("Your session has ended. Please sign in again."));
    }

    @Test
    @DisplayName("a header that is no token of ours is never looked up, and the request goes on as nobody signed in")
    void aMalformedTokenIsNeverLookedUp() throws Exception {
        mockMvc.perform(get("/api/test/open").with(SignInTestUsers.bearer("not-a-token"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/test/members").with(SignInTestUsers.bearer("not-a-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        org.mockito.Mockito.verifyNoInteractions(sessions);
    }

    @Test
    @DisplayName("a token says who is calling everywhere: the History tab's who, and how far they see")
    void aTokenIdentifiesTheCallerEverywhere() throws Exception {
        SignedInUser regional = SignInTestUsers.user("invented.regional", CallerScope.regions(Set.of("DBN")),
                Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/who").with(as(regional)))
                .andExpect(status().isOk())
                .andExpect(content().string("Invented invented.regional (invented.regional) | REGION [DBN]"));
        mockMvc.perform(get("/api/test/who"))
                .andExpect(status().isOk())
                .andExpect(content().string("system | ALL []"));
    }

    @Test
    @DisplayName("a user whose password is temporary holds nothing a permission opens: 403 PASSWORD_CHANGE_REQUIRED")
    void aTemporaryPasswordHoldsNothing() throws Exception {
        SignedInUser temporary = SignInTestUsers.temporary("invented.newcomer", Permission.MEMBERS_VIEW);
        mockMvc.perform(get("/api/test/members").with(as(temporary)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/test/open").with(as(temporary))).andExpect(status().isOk());
    }
}

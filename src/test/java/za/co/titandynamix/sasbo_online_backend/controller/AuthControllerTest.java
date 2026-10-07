package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CodeNameDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.LoginRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ModeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PasswordChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ScopeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SessionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.UserAnswerDto;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.SignInRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSignInSliceTest;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/auth}: each route against nobody signed in, a session that has ended, a signed-in user, and a body that
 * breaks its rules -- with the application's real security configuration (AbstractSignInSliceTest).
 */
@DisplayName("AuthController")
class AuthControllerTest extends AbstractSignInSliceTest {

    private static final SignedInUser ORGANISER = SignInTestUsers.user("invented.organiser",
            CallerScope.areas(java.util.Set.of("L")), Permission.MEMBERS_VIEW);

    private static SignInUserDto dto(SignedInUser user) {
        return new SignInUserDto(user.userId(), user.username(), user.displayName(), null,
                new CodeNameDto("ORGANISER", "Organiser"), new CodeNameDto("ORGANISER", "Organiser"),
                List.of("MEMBERS_VIEW"), new ScopeDto("AREA", List.of("L")), false, null, true, 0, false, null);
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ------------------------------------------------------------------------------------------------ login

    @Test
    @DisplayName("POST /login answers anyone: the token, when it ends, and the user")
    void loginAnswersAnyone() throws Exception {
        when(signIn.signIn(any(), anyString(), any())).thenReturn(new SessionDto(SignInTestUsers.tokenFor("t"),
                "2026-10-07T20:00:00Z", dto(ORGANISER)));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("invented.organiser", "an invented passphrase"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(SignInTestUsers.tokenFor("t")))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-07T20:00:00Z"))
                .andExpect(jsonPath("$.user.username").value("invented.organiser"))
                .andExpect(jsonPath("$.user.scope.type").value("AREA"));
    }

    @Test
    @DisplayName("POST /login with a token that opens nothing any more still signs in -- the old token is no barrier")
    void loginWithAnEndedTokenStillSignsIn() throws Exception {
        when(signIn.signIn(any(), anyString(), any())).thenReturn(new SessionDto(SignInTestUsers.tokenFor("t"),
                "2026-10-07T20:00:00Z", dto(ORGANISER)));

        mockMvc.perform(post("/api/auth/login").with(ended()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest("invented.organiser", "an invented passphrase"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /login with no username is VALIDATION_FAILED, field by field -- and nobody is asked to sign in")
    void loginWithoutAUsernameIsRefused() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"\", \"password\": \"an invented passphrase\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.username").value("Enter your username."));
        verifyNoInteractions(signIn);
    }

    @Test
    @DisplayName("POST /login with a body that is not JSON is REQUEST_MALFORMED, quoting nothing of it")
    void loginThatCannotBeReadIsMalformed() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"x\", \"password\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
    }

    @Test
    @DisplayName("the service's refusals answer with their own status and code: 401, 423, 403")
    void loginRefusalsKeepTheirStatus() throws Exception {
        for (Map.Entry<SignInRefusalCode, Integer> refusal : Map.of(SignInRefusalCode.SIGN_IN_FAILED, 401,
                SignInRefusalCode.ACCOUNT_LOCKED, 423, SignInRefusalCode.ACCOUNT_INACTIVE, 403).entrySet()) {
            doThrow(new SignInRefusal(refusal.getKey(), "No.")).when(signIn).signIn(any(), anyString(), any());
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json(new LoginRequest("invented.organiser", "an invented passphrase"))))
                    .andExpect(status().is(refusal.getValue()))
                    .andExpect(jsonPath("$.code").value(refusal.getKey().name()))
                    .andExpect(jsonPath("$.message").value("No."));
        }
    }

    // ------------------------------------------------------------------------------------------------ logout

    @Test
    @DisplayName("POST /logout: nobody signed in is 401 SIGN_IN_REQUIRED")
    void logoutNeedsASession() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Please sign in to use this part of SASBO Online."));
        verify(signIn, never()).signOut(any());
    }

    @Test
    @DisplayName("POST /logout with a token that opens nothing is 401 SESSION_EXPIRED, so the browser drops it")
    void logoutWithAnEndedSessionSaysSo() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(ended()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    @DisplayName("POST /logout ends the caller's own session: 204")
    void logoutEndsTheSession() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(as(ORGANISER)))
                .andExpect(status().isNoContent());
        verify(signIn).signOut(ORGANISER);
    }

    @Test
    @DisplayName("POST /logout works for a user whose password is temporary -- they can always leave")
    void logoutWorksWithATemporaryPassword() throws Exception {
        SignedInUser temporary = SignInTestUsers.temporary("invented.newcomer", Permission.USERS_ADMIN);
        mockMvc.perform(post("/api/auth/logout").with(as(temporary)))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------------------------------------ me

    @Test
    @DisplayName("GET /me: nobody signed in is 401 SIGN_IN_REQUIRED, an ended session 401 SESSION_EXPIRED")
    void meNeedsASession() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        mockMvc.perform(get("/api/auth/me").with(ended()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        verify(signIn, never()).me(any());
    }

    @Test
    @DisplayName("GET /me answers any signed-in user, whatever they hold -- a user with no permission at all too")
    void meAnswersAnySignedInUser() throws Exception {
        SignedInUser nobodyMuch = SignInTestUsers.user("invented.reader", CallerScope.all());
        when(signIn.me(nobodyMuch)).thenReturn(new UserAnswerDto(dto(nobodyMuch)));

        mockMvc.perform(get("/api/auth/me").with(as(nobodyMuch)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("invented.reader"));
    }

    @Test
    @DisplayName("GET /me answers a user whose password is temporary -- the screen must learn it has to be changed")
    void meAnswersATemporaryPassword() throws Exception {
        SignedInUser temporary = SignInTestUsers.temporary("invented.newcomer", Permission.USERS_ADMIN);
        when(signIn.me(temporary)).thenReturn(new UserAnswerDto(dto(temporary)));

        mockMvc.perform(get("/api/auth/me").with(as(temporary)))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------------------------------------ password

    @Test
    @DisplayName("POST /password: nobody signed in is 401, and nothing is changed")
    void passwordNeedsASession() throws Exception {
        mockMvc.perform(post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PasswordChangeRequest("the one in use", "a new invented phrase"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verify(signIn, never()).changePassword(any(), any());
    }

    @Test
    @DisplayName("POST /password changes the caller's own: 204")
    void passwordIsChanged() throws Exception {
        PasswordChangeRequest change = new PasswordChangeRequest("the one in use", "a new invented phrase");
        mockMvc.perform(post("/api/auth/password").with(as(ORGANISER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(change)))
                .andExpect(status().isNoContent());
        verify(signIn).changePassword(eq(ORGANISER), eq(change));
    }

    @Test
    @DisplayName("POST /password with no new password is VALIDATION_FAILED; a rule broken is PASSWORD_RULES naming it")
    void passwordRulesAreSaid() throws Exception {
        mockMvc.perform(post("/api/auth/password").with(as(ORGANISER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"the one in use\", \"newPassword\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.newPassword").value("Enter a new password."));

        doThrow(new SignInRefusal(SignInRefusalCode.PASSWORD_RULES, "A password needs at least 10 characters.",
                Map.of("rule", "TOO_SHORT"))).when(signIn).changePassword(any(), any());
        mockMvc.perform(post("/api/auth/password").with(as(ORGANISER)).contentType(MediaType.APPLICATION_JSON)
                        .content(json(new PasswordChangeRequest("the one in use", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PASSWORD_RULES"))
                .andExpect(jsonPath("$.details.rule").value("TOO_SHORT"));
    }

    // ------------------------------------------------------------------------------------------------ mode

    @Test
    @DisplayName("GET /mode answers anyone")
    void modeAnswersAnyone() throws Exception {
        when(signIn.mode()).thenReturn(new ModeDto("open"));
        mockMvc.perform(get("/api/auth/mode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("open"));
    }
}

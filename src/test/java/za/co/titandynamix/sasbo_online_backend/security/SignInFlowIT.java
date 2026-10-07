package za.co.titandynamix.sasbo_online_backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.repository.StaffPositionRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.StaffUserRepository;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.SignInSeeder;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-in end to end (signin.md): the real token filter, the real services and PostgreSQL, with the fixtures' clock.
 * The first administrator signs in on a temporary password, changes it, creates a user with a position and an area
 * (the 5 Oct ask), and the sessions end as the contract says -- sign-out, a reset, a deactivation.
 */
@DisplayName("Sign-in, end to end on PostgreSQL")
class SignInFlowIT extends AbstractSwitchedOnIT {

    private static final String FIRST = "invented.first";
    private static final String FIRST_PHRASE = "an-invented-first-phrase";
    private static final String SETTLED = "an-invented-settled-phrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignInSeeder seeder;

    @Autowired
    private StaffRoleRepository roles;

    @Autowired
    private StaffPositionRepository positions;

    @Autowired
    private StaffUserRepository users;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void theFirstAdministratorAndAnArea() {
        seeder.seedPositionsAndRoles();
        new SignInSeeder(roles, positions, users, encoder, new SignInProperties("open", false, null,
                new SignInProperties.BootstrapAdmin(FIRST, FIRST_PHRASE)), new SasboEnvironment(null),
                Clock.fixed(SummaryFixture.NOW, java.time.ZoneOffset.UTC), transactions).seedBootstrapAdministrator();
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('DBN', 'DBN')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) VALUES ('L', (SELECT id FROM sasbo_regions WHERE code = "
                + "'DBN'))");
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String signIn(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("token").asText();
    }

    /** The first administrator, signed in with a settled password. */
    private String administrator() throws Exception {
        String token = signIn(FIRST, FIRST_PHRASE);
        mockMvc.perform(post("/api/auth/password").with(SignInTestUsers.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + FIRST_PHRASE + "\", \"newPassword\": \"" + SETTLED + "\"}"))
                .andExpect(status().isNoContent());
        return token;
    }

    /** An organiser in area L, created by the administrator: the user's id, version and temporary password. */
    private JsonNode anOrganiser(String adminToken) throws Exception {
        return json(mockMvc.perform(post("/api/admin/users").with(SignInTestUsers.bearer(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.organiser\", \"displayName\": \"Invented Organiser\", "
                                + "\"positionCode\": \"ORGANISER\", \"scope\": {\"type\": \"AREA\", \"codes\": [\"L\"]}}"))
                .andExpect(status().isCreated())
                .andReturn());
    }

    @Test
    @DisplayName("the first administrator's day: a temporary password opens nothing, a changed one opens their work")
    void theFirstAdministratorsDay() throws Exception {
        MvcResult signedIn = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"Invented.First\", \"password\": \"" + FIRST_PHRASE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true))
                .andExpect(jsonPath("$.user.position.code").value("SYSTEM_ADMINISTRATOR"))
                .andExpect(jsonPath("$.user.scope.type").value("ALL"))
                .andExpect(jsonPath("$.expiresAt").value(SummaryFixture.NOW.plus(Duration.ofHours(12)).toString()))
                .andReturn();
        String token = json(signedIn).path("token").asText();
        mockMvc.perform(get("/api/auth/me").with(SignInTestUsers.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(FIRST));
        mockMvc.perform(get("/api/admin/users").with(SignInTestUsers.bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        mockMvc.perform(post("/api/auth/password").with(SignInTestUsers.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + FIRST_PHRASE + "\", \"newPassword\": \"" + SETTLED + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/users").with(SignInTestUsers.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value(FIRST))
                .andExpect(jsonPath("$.content[0].mustChangePassword").value(false));

        JsonNode created = anOrganiser(token);
        assertThat(created.path("user").path("scope").path("codes").get(0).asText()).isEqualTo("L");
        assertThat(created.path("temporaryPassword").asText()).matches("([A-Za-z2-9]{4}-){3}[A-Za-z2-9]{4}");
        // The History tab's "who": the administrator, named, on the row they wrote.
        assertThat(jdbc.queryForObject("SELECT created_by FROM staff_users WHERE username = 'invented.organiser'",
                String.class)).isEqualTo("System Administrator (invented.first)");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.organiser\", \"password\": \""
                                + created.path("temporaryPassword").asText() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mustChangePassword").value(true));

        assertThat(jdbc.queryForList("SELECT action FROM audit_events ORDER BY id", String.class))
                .containsSubsequence("SIGN_IN", "PASSWORD_CHANGED", "USER_CREATED", "SIGN_IN");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events")).hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("🔒 only the token's SHA-256 is stored -- the token itself is nowhere in the table")
    void theTokenIsNeverStored() throws Exception {
        String token = signIn(FIRST, FIRST_PHRASE);
        assertThat(jdbc.queryForList("SELECT token_hash FROM user_sessions", String.class))
                .containsExactly(SessionTokens.hash(token));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE token_hash = ? OR ip_address = ? OR "
                + "user_agent = ? OR revoked_reason = ?", Long.class, token, token, token, token)).isZero();
    }

    @Test
    @DisplayName("sign-out ends the session: the same token is 401 SESSION_EXPIRED")
    void signOutEndsTheSession() throws Exception {
        String token = signIn(FIRST, FIRST_PHRASE);
        mockMvc.perform(post("/api/auth/logout").with(SignInTestUsers.bearer(token))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/auth/me").with(SignInTestUsers.bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT revoked_reason FROM user_sessions", String.class)).isEqualTo("SIGNED_OUT");
    }

    @Test
    @DisplayName("five wrong passwords lock fifteen minutes; while locked the right one is refused too")
    void fiveWrongPasswordsLock() throws Exception {
        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\": \"" + FIRST + "\", \"password\": \"invented wrong " + i + "\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SIGN_IN_FAILED"));
        }
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + FIRST + "\", \"password\": \"invented wrong 5\"}"))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"))
                .andExpect(jsonPath("$.details.lockedUntil").value(SummaryFixture.NOW.plus(Duration.ofMinutes(15))
                        .toString()));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + FIRST + "\", \"password\": \"" + FIRST_PHRASE + "\"}"))
                .andExpect(status().isLocked());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions", Long.class)).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM audit_events WHERE action = 'ACCOUNT_LOCKED'", String.class))
                .hasSize(1);
    }

    @Test
    @DisplayName("an unknown username answers exactly as a wrong password does")
    void anUnknownUsernameAnswersTheSame() throws Exception {
        String unknown = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.nobody\", \"password\": \"" + FIRST_PHRASE + "\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrong = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"" + FIRST + "\", \"password\": \"invented wrong\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(unknown).path("message")).isEqualTo(objectMapper.readTree(wrong).path("message"));
        assertThat(objectMapper.readTree(unknown).path("code")).isEqualTo(objectMapper.readTree(wrong).path("code"));
    }

    @Test
    @DisplayName("a reset ends the user's sessions; a deactivation ends them and the right password then hears ACCOUNT_INACTIVE")
    void resetAndDeactivationEndSessions() throws Exception {
        String admin = administrator();
        JsonNode organiser = anOrganiser(admin);
        long id = organiser.path("user").path("id").asLong();
        String organiserToken = signIn("invented.organiser", organiser.path("temporaryPassword").asText());

        MvcResult reset = mockMvc.perform(post("/api/admin/users/" + id + "/reset-password")
                        .with(SignInTestUsers.bearer(admin)))
                .andExpect(status().isOk())
                .andReturn();
        String temporary = json(reset).path("temporaryPassword").asText();
        mockMvc.perform(get("/api/auth/me").with(SignInTestUsers.bearer(organiserToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));

        String again = signIn("invented.organiser", temporary);
        long version = users.findWithAccess(id).orElseThrow().getVersion();
        mockMvc.perform(put("/api/admin/users/" + id).with(SignInTestUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\": \"Invented Organiser\", \"positionCode\": \"ORGANISER\", "
                                + "\"scope\": {\"type\": \"AREA\", \"codes\": [\"L\"]}, \"active\": false, \"version\": "
                                + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.active").value(false));
        mockMvc.perform(get("/api/auth/me").with(SignInTestUsers.bearer(again)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.organiser\", \"password\": \"" + temporary + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
        assertThat(jdbc.queryForList("SELECT revoked_reason FROM user_sessions WHERE user_id = ? ORDER BY id",
                String.class, id)).containsExactly("PASSWORD_RESET", "DEACTIVATED");
    }

    @Test
    @DisplayName("🔒 the last administrator is never deactivated; a stale edit and a taken username are refused")
    void theRefusalsOfAnAdministrator() throws Exception {
        String admin = administrator();
        long firstId = users.findForSignIn(FIRST).orElseThrow().getId();
        long version = users.findWithAccess(firstId).orElseThrow().getVersion();
        String edit = "{\"displayName\": \"System Administrator\", \"positionCode\": \"SYSTEM_ADMINISTRATOR\", "
                + "\"scope\": {\"type\": \"ALL\", \"codes\": []}, \"active\": %s, \"version\": %d}";
        mockMvc.perform(put("/api/admin/users/" + firstId).with(SignInTestUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(edit.formatted("false", version)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMINISTRATOR"));
        mockMvc.perform(put("/api/admin/users/" + firstId).with(SignInTestUsers.bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(edit.formatted("true", version + 7)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
        anOrganiser(admin);
        mockMvc.perform(post("/api/admin/users").with(SignInTestUsers.bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"Invented.Organiser\", \"displayName\": \"Twice\", \"positionCode\": "
                                + "\"ORGANISER\", \"scope\": {\"type\": \"AREA\", \"codes\": [\"L\"]}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
        mockMvc.perform(post("/api/admin/users").with(SignInTestUsers.bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.elsewhere\", \"displayName\": \"Elsewhere\", "
                                + "\"positionCode\": \"ORGANISER\", \"scope\": {\"type\": \"AREA\", \"codes\": [\"Q\"]}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SCOPE_INVALID"))
                .andExpect(jsonPath("$.details.unknown").value("Q"));
        assertThat(jdbc.queryForList("SELECT username FROM staff_users ORDER BY id", String.class))
                .isEqualTo(List.of(FIRST, "invented.organiser"));
    }
}

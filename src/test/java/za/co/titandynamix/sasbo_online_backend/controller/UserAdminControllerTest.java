package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CodeNameDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.CreatedUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PermissionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.PositionDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.RoleDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.ScopeDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInUserDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.TemporaryPasswordDto;
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
import java.util.Set;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/admin}: every route is {@code USERS_ADMIN}. Each against nobody signed in (401), a signed-in user without
 * the permission (403 naming it), a user whose role holds it but whose password is temporary (403
 * PASSWORD_CHANGE_REQUIRED), and the permission's holder -- and a body that breaks its rules.
 */
@DisplayName("UserAdminController")
class UserAdminControllerTest extends AbstractSignInSliceTest {

    private static final SignedInUser ADMINISTRATOR = SignInTestUsers.user("invented.useradmin", CallerScope.all(),
            Permission.USERS_ADMIN);
    private static final SignedInUser ORGANISER = SignInTestUsers.user("invented.organiser",
            CallerScope.areas(Set.of("L")), Permission.MEMBERS_VIEW, Permission.MEMBERS_CAPTURE);

    private static final String NEW_USER = "{\"username\": \"invented.newcomer\", \"displayName\": \"Invented "
            + "Newcomer\", \"email\": \"newcomer@example.org\", \"positionCode\": \"ORGANISER\", \"scope\": "
            + "{\"type\": \"AREA\", \"codes\": [\"L\"]}}";
    private static final String USER_EDIT = "{\"displayName\": \"Invented Newcomer\", \"email\": null, "
            + "\"positionCode\": \"ORGANISER\", \"roleCode\": \"ORGANISER\", \"scope\": {\"type\": \"AREA\", "
            + "\"codes\": [\"L\"]}, \"active\": true, \"version\": 3}";
    private static final String POSITION = "{\"code\": \"MEMBERSHIP_CLERK\", \"name\": \"Invented Clerk\", "
            + "\"defaultRoleCode\": \"ORGANISER\", \"active\": true, \"ordinal\": 90}";
    private static final String ROLE = "{\"code\": \"CLERK\", \"name\": \"Invented Clerk\", \"permissions\": "
            + "[\"MEMBERS_VIEW\"]}";

    /** Every route of the controller, with a body it accepts. */
    static Stream<MockHttpServletRequestBuilder> routes() {
        return Stream.of(
                get("/api/admin/users"),
                post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(NEW_USER),
                put("/api/admin/users/4").contentType(MediaType.APPLICATION_JSON).content(USER_EDIT),
                post("/api/admin/users/4/reset-password"),
                get("/api/admin/positions"),
                post("/api/admin/positions").contentType(MediaType.APPLICATION_JSON).content(POSITION),
                put("/api/admin/positions/MEMBERSHIP_CLERK").contentType(MediaType.APPLICATION_JSON).content(POSITION),
                get("/api/admin/roles"),
                post("/api/admin/roles").contentType(MediaType.APPLICATION_JSON).content(ROLE),
                put("/api/admin/roles/CLERK").contentType(MediaType.APPLICATION_JSON).content(ROLE),
                get("/api/admin/permissions"));
    }

    private static SignInUserDto userDto() {
        return new SignInUserDto(4, "invented.newcomer", "Invented Newcomer", null,
                new CodeNameDto("ORGANISER", "Organiser"), new CodeNameDto("ORGANISER", "Organiser"),
                List.of("MEMBERS_VIEW"), new ScopeDto("AREA", List.of("L")), true, null, true, 3, false, null);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    @DisplayName("nobody signed in is 401 SIGN_IN_REQUIRED, and the service is never asked")
    void anonymousIsRefused(MockHttpServletRequestBuilder route) throws Exception {
        mockMvc.perform(route)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verifyNoInteractions(admin);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    @DisplayName("a signed-in user without USERS_ADMIN is 403 PERMISSION_REQUIRED, naming it")
    void withoutThePermissionIsRefused(MockHttpServletRequestBuilder route) throws Exception {
        mockMvc.perform(route.with(as(ORGANISER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("USERS_ADMIN"))
                .andExpect(jsonPath("$.details.permission").value("USERS_ADMIN"));
        verifyNoInteractions(admin);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("routes")
    @DisplayName("a user whose role holds USERS_ADMIN but whose password is temporary is 403 PASSWORD_CHANGE_REQUIRED")
    void aTemporaryPasswordHoldsNothing(MockHttpServletRequestBuilder route) throws Exception {
        mockMvc.perform(route.with(as(SignInTestUsers.temporary("invented.newadmin", Permission.USERS_ADMIN))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("GET /users: a page of users, the query passed as asked")
    void usersArePaged() throws Exception {
        when(admin.users(1, 10, "inv")).thenReturn(new SignInPage<>(List.of(userDto()), 1, 10, 11, 2));

        mockMvc.perform(get("/api/admin/users?page=1&size=10&q=inv").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("invented.newcomer"))
                .andExpect(jsonPath("$.content[0].active").value(true))
                .andExpect(jsonPath("$.content[0].version").value(3))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("GET /users with a page that is not a number is REQUEST_MALFORMED, naming the field")
    void aPageThatIsNotANumberIsMalformed() throws Exception {
        mockMvc.perform(get("/api/admin/users?page=first").with(as(ADMINISTRATOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"));
    }

    @Test
    @DisplayName("POST /users: 201, the user and the temporary password shown once")
    void aUserIsCreated() throws Exception {
        when(admin.create(any())).thenReturn(new CreatedUserDto(userDto(), "Abcd-Efgh-Jkmn-Pqrs"));

        mockMvc.perform(post("/api/admin/users").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_USER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.position.code").value("ORGANISER"))
                .andExpect(jsonPath("$.user.scope.codes[0]").value("L"))
                .andExpect(jsonPath("$.temporaryPassword").value("Abcd-Efgh-Jkmn-Pqrs"));
    }

    @Test
    @DisplayName("POST /users with no position and a username of spaces is VALIDATION_FAILED, field by field")
    void aUserWithoutAPositionIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"   \", \"displayName\": \"Invented\", \"positionCode\": \"\", "
                                + "\"scope\": {\"type\": \"ALL\", \"codes\": []}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.positionCode").value("Choose a position."))
                .andExpect(jsonPath("$.validationErrors.username").exists());
        verifyNoInteractions(admin);
    }

    @Test
    @DisplayName("POST /users with a scope that is no scope is VALIDATION_FAILED on the scope's own field")
    void aScopeOfNoKnownTypeIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/users").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"invented.x\", \"displayName\": \"Invented\", \"positionCode\": "
                                + "\"ORGANISER\", \"scope\": {\"type\": \"PLANET\", \"codes\": []}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors['scope.type']").exists());
    }

    @Test
    @DisplayName("the service's refusals keep their status and code: USERNAME_TAKEN, SCOPE_INVALID, ROLE_ABOVE_YOUR_OWN")
    void createRefusalsKeepTheirStatus() throws Exception {
        for (Map.Entry<SignInRefusalCode, Integer> refusal : Map.of(SignInRefusalCode.USERNAME_TAKEN, 409,
                SignInRefusalCode.SCOPE_INVALID, 400, SignInRefusalCode.ROLE_ABOVE_YOUR_OWN, 403).entrySet()) {
            doThrow(new SignInRefusal(refusal.getKey(), "No.")).when(admin).create(any());
            mockMvc.perform(post("/api/admin/users").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                            .content(NEW_USER))
                    .andExpect(status().is(refusal.getValue()))
                    .andExpect(jsonPath("$.code").value(refusal.getKey().name()));
        }
    }

    @Test
    @DisplayName("PUT /users/{id}: the edit with the version the screen read")
    void aUserIsChanged() throws Exception {
        when(admin.update(eq(4L), any())).thenReturn(new UserAnswerDto(userDto()));

        mockMvc.perform(put("/api/admin/users/4").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(USER_EDIT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.version").value(3));
    }

    @Test
    @DisplayName("PUT /users/{id} with no version is VALIDATION_FAILED -- nobody overwrites what they have not seen")
    void anEditWithoutAVersionIsRefused() throws Exception {
        mockMvc.perform(put("/api/admin/users/4").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(USER_EDIT.replace(", \"version\": 3", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors.version").exists());
    }

    @Test
    @DisplayName("PUT /users/{id}: STALE_VERSION and LAST_ADMINISTRATOR are 409s")
    void editRefusalsAreConflicts() throws Exception {
        for (SignInRefusalCode code : List.of(SignInRefusalCode.STALE_VERSION, SignInRefusalCode.LAST_ADMINISTRATOR)) {
            doThrow(new SignInRefusal(code, "No.")).when(admin).update(anyLong(), any());
            mockMvc.perform(put("/api/admin/users/4").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                            .content(USER_EDIT))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(code.name()));
        }
    }

    @Test
    @DisplayName("POST /users/{id}/reset-password: the new temporary password, shown once")
    void aPasswordIsReset() throws Exception {
        when(admin.resetPassword(4L)).thenReturn(new TemporaryPasswordDto("Wxyz-Abcd-Efgh-Jkmn"));

        mockMvc.perform(post("/api/admin/users/4/reset-password").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("Wxyz-Abcd-Efgh-Jkmn"));
    }

    @Test
    @DisplayName("positions: listed, created (201) and changed by the code in the address")
    void positionsAreKept() throws Exception {
        PositionDto clerk = new PositionDto("MEMBERSHIP_CLERK", "Invented Clerk", "ORGANISER", true, 90, 0);
        when(admin.positions()).thenReturn(List.of(clerk));
        when(admin.createPosition(any())).thenReturn(clerk);
        when(admin.updatePosition(eq("MEMBERSHIP_CLERK"), any())).thenReturn(clerk);

        mockMvc.perform(get("/api/admin/positions").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].defaultRoleCode").value("ORGANISER"));
        mockMvc.perform(post("/api/admin/positions").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(POSITION))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEMBERSHIP_CLERK"));
        mockMvc.perform(put("/api/admin/positions/MEMBERSHIP_CLERK").with(as(ADMINISTRATOR))
                        .contentType(MediaType.APPLICATION_JSON).content(POSITION))
                .andExpect(status().isOk());
        verify(admin).updatePosition(eq("MEMBERSHIP_CLERK"), any());
    }

    @Test
    @DisplayName("a position's code of the wrong shape is VALIDATION_FAILED")
    void aPositionCodeOfTheWrongShapeIsRefused() throws Exception {
        mockMvc.perform(post("/api/admin/positions").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(POSITION.replace("MEMBERSHIP_CLERK", "membership clerk")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.code").exists());
    }

    @Test
    @DisplayName("roles: listed, created (201) and changed; a role with no permissions list is VALIDATION_FAILED")
    void rolesAreKept() throws Exception {
        RoleDto clerk = new RoleDto("CLERK", "Invented Clerk", List.of("MEMBERS_VIEW"), false, 0);
        when(admin.roles()).thenReturn(List.of(clerk));
        when(admin.createRole(any())).thenReturn(clerk);
        when(admin.updateRole(eq("CLERK"), any())).thenReturn(clerk);

        mockMvc.perform(get("/api/admin/roles").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].permissions[0]").value("MEMBERS_VIEW"));
        mockMvc.perform(post("/api/admin/roles").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(ROLE))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/admin/roles/CLERK").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content(ROLE))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/roles").with(as(ADMINISTRATOR)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"CLERK\", \"name\": \"Invented Clerk\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.permissions").exists());
    }

    @Test
    @DisplayName("GET /permissions: the catalogue, each with its sentence")
    void theCatalogueIsListed() throws Exception {
        when(admin.permissions()).thenReturn(List.of(new PermissionDto("USERS_ADMIN", "Create and edit users")));

        mockMvc.perform(get("/api/admin/permissions").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("USERS_ADMIN"))
                .andExpect(jsonPath("$[0].description").value("Create and edit users"));
    }

    @Test
    @DisplayName("GET /users with no query asks the service for its defaults")
    void defaultsAreTheServices() throws Exception {
        when(admin.users(isNull(), isNull(), isNull())).thenReturn(new SignInPage<>(List.of(), 0, 25, 0, 0));
        mockMvc.perform(get("/api/admin/users").with(as(ADMINISTRATOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(25));
    }
}

package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.signin.AuditEventDto;
import za.co.titandynamix.sasbo_online_backend.dto.signin.SignInPage;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code GET /api/audit} -- {@code AUDIT_VIEW}: nobody signed in, without the permission, with it; filters as asked. */
@DisplayName("AuditController")
class AuditControllerTest extends AbstractSignInSliceTest {

    private static final SignedInUser AUDITOR = SignInTestUsers.user("invented.auditor", CallerScope.all(),
            Permission.AUDIT_VIEW);

    @Test
    @DisplayName("nobody signed in is 401 SIGN_IN_REQUIRED")
    void anonymousIsRefused() throws Exception {
        mockMvc.perform(get("/api/audit"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("a signed-in user without AUDIT_VIEW is 403 PERMISSION_REQUIRED, naming it -- a user administrator too")
    void withoutThePermissionIsRefused() throws Exception {
        SignedInUser userAdmin = SignInTestUsers.user("invented.useradmin", CallerScope.all(), Permission.USERS_ADMIN);
        mockMvc.perform(get("/api/audit").with(as(userAdmin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("AUDIT_VIEW"));
        verifyNoInteractions(audit);
    }

    @Test
    @DisplayName("the holder reads a page, newest first, each filter passed as asked")
    void theHolderReadsAPage() throws Exception {
        when(audit.search("USER", "4", "invented.useradmin", "2026-10-01", "2026-10-07", 0, 50)).thenReturn(
                new SignInPage<>(List.of(new AuditEventDto("2026-10-07T07:00:00Z", "invented.useradmin",
                        "Invented Useradmin", "USER_CREATED", "USER", "4", "Created with position Organiser.")),
                        0, 50, 1, 1));

        mockMvc.perform(get("/api/audit?entityType=USER&entityId=4&username=invented.useradmin&from=2026-10-01"
                        + "&to=2026-10-07&page=0&size=50").with(as(AUDITOR)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("USER_CREATED"))
                .andExpect(jsonPath("$.content[0].at").value("2026-10-07T07:00:00Z"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a filter the record cannot read is AUDIT_FILTER_INVALID, naming the field; a size that is no number is malformed")
    void filtersThatCannotBeReadAreRefused() throws Exception {
        doThrow(new SignInRefusal(SignInRefusalCode.AUDIT_FILTER_INVALID, "That day could not be read.",
                Map.of("field", "from"))).when(audit).search(any(), any(), any(), any(), any(), any(), any());
        mockMvc.perform(get("/api/audit?from=yesterday").with(as(AUDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUDIT_FILTER_INVALID"))
                .andExpect(jsonPath("$.details.field").value("from"));

        mockMvc.perform(get("/api/audit?size=many").with(as(AUDITOR)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("size"));
    }
}

package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonRequest;
import za.co.titandynamix.sasbo_online_backend.dto.reference.NoDeductionReasonsDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.NoDeductionReasons;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The reasons for no payroll deduction, under {@code /api/reference/no-deduction-reasons} (Stephne, MEN-17: "Can we
 * have a dropdown sayin a reason for no payroll deduction") -- under the application's real security configuration,
 * with the filter chain on. The list is open, as every list of SASBO's is; adding and editing are held by
 * {@code REFERENCE_MAINTAIN}. The rules themselves are {@code NoDeductionReasonsIT}'s, on PostgreSQL: here, what the
 * route does with the request and with a refusal.
 */
@WebMvcTest(NoDeductionReasonController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("NoDeductionReasonController, with the application's real security configuration")
class NoDeductionReasonControllerTest {

    private static final String ROUTE = "/api/reference/no-deduction-reasons";
    private static final String STUDY_LEAVE_ASKED = "{\"name\": \"Study leave\", \"suspensionCode\": 11}";

    private static final SignedInUser MAINTAINER = SignInTestUsers.user("invented.maintainer", CallerScope.all(),
            Permission.REFERENCE_MAINTAIN);
    /** An organiser: reads members, keeps no list. */
    private static final SignedInUser ORGANISER = SignInTestUsers.user("invented.organiser",
            CallerScope.areas(Set.of("T")), Permission.MEMBERS_VIEW);

    private static final NoDeductionReasonDto STUDY_LEAVE = new NoDeductionReasonDto(6, "Study leave", 11,
            "NOT ON PAYROLL", 6, true, false, 0, "Invented invented.maintainer (invented.maintainer)",
            "2026-10-07T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NoDeductionReasons reasons;

    @Test
    @DisplayName("MEN-17: the list is open to anyone -- every reason, and the limits the screen holds the words to")
    void theListIsOpen() throws Exception {
        when(reasons.list()).thenReturn(new NoDeductionReasonsDto(List.of(STUDY_LEAVE), 50, 60, 500));

        mockMvc.perform(get(ROUTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].name").value("Study leave"))
                .andExpect(jsonPath("$.rows[0].suspensionName").value("NOT ON PAYROLL"))
                .andExpect(jsonPath("$.ceiling").value(50))
                .andExpect(jsonPath("$.nameLimit").value(60))
                .andExpect(jsonPath("$.noteLimit").value(500));
    }

    @Test
    @DisplayName("🔒 adding or editing a reason: nobody signed in is 401, an organiser without REFERENCE_MAINTAIN 403 naming it -- and nothing is asked of the list")
    void addingAndEditingAreHeld() throws Exception {
        mockMvc.perform(post(ROUTE).contentType(MediaType.APPLICATION_JSON).content(STUDY_LEAVE_ASKED))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        mockMvc.perform(post(ROUTE).with(SignInTestUsers.signedIn(ORGANISER)).contentType(MediaType.APPLICATION_JSON)
                        .content(STUDY_LEAVE_ASKED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("REFERENCE_MAINTAIN"));
        mockMvc.perform(put(ROUTE + "/6").with(SignInTestUsers.signedIn(ORGANISER))
                        .contentType(MediaType.APPLICATION_JSON).content(STUDY_LEAVE_ASKED))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.permission").value("REFERENCE_MAINTAIN"));
        verifyNoInteractions(reasons);
    }

    @Test
    @DisplayName("the holder adds a reason: 201 and the row, the request passed on as it was sent")
    void theHolderAdds() throws Exception {
        when(reasons.create(new NoDeductionReasonRequest("Study leave", 11, null, null, null))).thenReturn(STUDY_LEAVE);

        mockMvc.perform(post(ROUTE).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(STUDY_LEAVE_ASKED))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.provisional").value(false))
                .andExpect(jsonPath("$.updatedBy").value("Invented invented.maintainer (invented.maintainer)"));
    }

    @Test
    @DisplayName("the holder edits a reason on the version the screen read: 200 and the row")
    void theHolderEdits() throws Exception {
        when(reasons.update(6L, new NoDeductionReasonRequest("Study leave", 11, null, false, 0))).thenReturn(
                new NoDeductionReasonDto(6, "Study leave", 11, "NOT ON PAYROLL", 6, false, false, 1,
                        "Invented invented.maintainer (invented.maintainer)", "2026-10-07T09:05:00Z"));

        mockMvc.perform(put(ROUTE + "/6").with(SignInTestUsers.signedIn(MAINTAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Study leave\", \"suspensionCode\": 11, \"active\": false, \"version\": 0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("a refusal of the list's rules is answered by its code and status -- NO_DEDUCTION_REASON_INVALID 400 with the field and the limit, NO_DEDUCTION_REASON_CHANGED_SINCE 409")
    void theRulesRefusalsAreAnswered() throws Exception {
        when(reasons.create(any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.NO_DEDUCTION_REASON_INVALID,
                "A reason's name may be at most 60 characters of ordinary text, on one line. Nothing was changed.",
                MonthEndRefusal.details("field", "name", "limit", "60")));
        when(reasons.update(eq(6L), any())).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.NO_DEDUCTION_REASON_CHANGED_SINCE, "Someone else changed this reason since you "
                + "opened it. Nothing was changed: look at theirs, then edit it again.", MonthEndRefusal.details(
                "version", "2", "updatedBy", "Invented colleague (invented.colleague)",
                "updatedAt", "2026-10-07T09:01:00Z")));

        mockMvc.perform(post(ROUTE).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content(STUDY_LEAVE_ASKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_DEDUCTION_REASON_INVALID"))
                .andExpect(jsonPath("$.details.field").value("name"))
                .andExpect(jsonPath("$.details.limit").value("60"));
        mockMvc.perform(put(ROUTE + "/6").with(SignInTestUsers.signedIn(MAINTAINER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Study leave\", \"suspensionCode\": 11, \"version\": 0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_DEDUCTION_REASON_CHANGED_SINCE"))
                .andExpect(jsonPath("$.details.version").value("2"));
    }

    @Test
    @DisplayName("🔒 an unreadable request is 400 REQUEST_MALFORMED naming the field, never its value -- and nothing is asked of the list")
    void anUnreadableRequest() throws Exception {
        mockMvc.perform(post(ROUTE).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Study leave\", \"suspensionCode\": \"PLANTEDELEVEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("suspensionCode"))
                .andExpect(jsonPath("$.message").value(not(containsString("PLANTEDELEVEN"))));
        mockMvc.perform(put(ROUTE + "/PLANTEDSIX").with(SignInTestUsers.signedIn(MAINTAINER))
                        .contentType(MediaType.APPLICATION_JSON).content(STUDY_LEAVE_ASKED))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
        verifyNoInteractions(reasons);
    }
}

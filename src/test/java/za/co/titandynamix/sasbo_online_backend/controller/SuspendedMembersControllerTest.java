package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.SuspendedMembersPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.SuspendedMembers;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The suspended members by name, and the lapsed, under {@code /api/month-end/suspensions} (Stephne, MEN-18: "We should
 * also know who the suspended members are") -- under the application's real security configuration, with the filter
 * chain on. A listing that names members, held by {@code MEMBERS_VIEW} AND {@code MONTH_END_VIEW}: Stephne, DOD-14,
 * organisers do not see suspensions (OB30), and an organiser holds the first alone. Who is listed, in what order and
 * scope, is {@code SuspendedMembersIT}'s, on PostgreSQL.
 */
@WebMvcTest(SuspendedMembersController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("SuspendedMembersController, with the application's real security configuration")
class SuspendedMembersControllerTest {

    /** Head office: reads members and the month's figures. */
    private static final SignedInUser HEAD_OFFICE = SignInTestUsers.user("invented.headoffice", CallerScope.all(),
            Permission.MEMBERS_VIEW, Permission.MONTH_END_VIEW);

    private static final SuspendedMembersPageDto ONE_LAPSED = new SuspendedMembersPageDto("2026-10-14", 3, 0, 50, 1,
            1, 1, 0, List.of(new SuspendedMemberDto(812, "M-096000", "A Invented", "INVENTED BANK",
            "R/A 900001 INVENTED MAIN STREET", "T", "Payroll Deduction", 11, "NOT ON PAYROLL", "Unpaid leave",
            "2026-05-06", "2026-08-06", true)));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SuspendedMembers suspended;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"members", "lapsed"})
    @DisplayName("🔒 nobody signed in is 401 SIGN_IN_REQUIRED -- before anything is read")
    void nobodySignedInReadsNothing(String list) throws Exception {
        mockMvc.perform(get("/api/month-end/suspensions/" + list))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verifyNoInteractions(suspended);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"members", "lapsed"})
    @DisplayName("🔒 OB30, Stephne DOD-14 -- organisers do not see suspensions: MEMBERS_VIEW alone is 403, and so is MONTH_END_VIEW alone")
    void eitherPermissionAloneReadsNothing(String list) throws Exception {
        SignedInUser organiser = SignInTestUsers.user("invented.organiser", CallerScope.areas(Set.of("T")),
                Permission.MEMBERS_VIEW, Permission.MEMBERS_CAPTURE);
        SignedInUser counter = SignInTestUsers.user("invented.counter", CallerScope.all(), Permission.MONTH_END_VIEW);

        for (SignedInUser oneOfTwo : List.of(organiser, counter)) {
            mockMvc.perform(get("/api/month-end/suspensions/" + list).with(SignInTestUsers.signedIn(oneOfTwo)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
        }
        verifyNoInteractions(suspended);
    }

    @Test
    @DisplayName("MEN-18: holding both, the suspended are read a page at a time, the filters passed on as asked")
    void theHolderReadsTheSuspended() throws Exception {
        when(suspended.members(0, 25, 4L, 11)).thenReturn(ONE_LAPSED);

        mockMvc.perform(get("/api/month-end/suspensions/members?page=0&size=25&employer=4&code=11")
                        .with(SignInTestUsers.signedIn(HEAD_OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lapseMonths").value(3))
                .andExpect(jsonPath("$.items[0].membershipNumber").value("M-096000"))
                .andExpect(jsonPath("$.items[0].lapsesOn").value("2026-08-06"))
                .andExpect(jsonPath("$.items[0].lapsed").value(true));
    }

    @Test
    @DisplayName("MEN-18, \"remind us when the suspension period for a member have lapsed\": the lapsed are their own list, the filters left out passed on as none")
    void theHolderReadsTheLapsed() throws Exception {
        when(suspended.lapsed(null, null, null, null)).thenReturn(ONE_LAPSED);

        mockMvc.perform(get("/api/month-end/suspensions/lapsed").with(SignInTestUsers.signedIn(HEAD_OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.lapsed").value(1));
    }

    @Test
    @DisplayName("a page past the ceiling is SUSPENDED_PAGE_INVALID 400 with the ceiling; a code not on the list SUSPENSION_CODE_UNKNOWN 400")
    void theRefusalsAreAnswered() throws Exception {
        when(suspended.members(0, 500, null, null)).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.SUSPENDED_PAGE_INVALID, "A page of the suspended members is from page 0, of 1 to "
                + "100 members.", MonthEndRefusal.details("page", "0", "size", "500", "ceiling", "100")));
        when(suspended.lapsed(null, null, null, 99)).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.SUSPENSION_CODE_UNKNOWN, "That suspension code is not on the list.",
                MonthEndRefusal.details("suspensionCode", "99", "known", "11,12,13")));

        mockMvc.perform(get("/api/month-end/suspensions/members?page=0&size=500")
                        .with(SignInTestUsers.signedIn(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUSPENDED_PAGE_INVALID"))
                .andExpect(jsonPath("$.details.ceiling").value("100"));
        mockMvc.perform(get("/api/month-end/suspensions/lapsed?code=99").with(SignInTestUsers.signedIn(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUSPENSION_CODE_UNKNOWN"))
                .andExpect(jsonPath("$.details.known").value("11,12,13"));
    }

    @Test
    @DisplayName("🔒 a parameter that is not a number is 400 REQUEST_MALFORMED naming it, never its value -- and nothing is read")
    void aParameterThatIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/month-end/suspensions/members?employer=PLANTEDBANK")
                        .with(SignInTestUsers.signedIn(HEAD_OFFICE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("employer"))
                .andExpect(jsonPath("$.message").value(not(containsString("PLANTEDBANK"))));
        verifyNoInteractions(suspended);
    }
}

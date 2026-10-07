package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthsDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.AuditTrail;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Taking a month is held by {@code MONTH_END_TAKE} (month end 04.2, held until sign-in by MD4; switched on by the
 * October build) -- under the application's real security configuration, with the filter chain on. A take again is
 * the same permission: the same POSITION may take a month again, not only the person who took it (Stephne, MEN-10).
 */
@WebMvcTest(MonthEndController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndController, with the application's real security configuration")
class MonthEndControllerSecurityTest {

    private static final SignedInUser TAKER = SignInTestUsers.user("invented.headoffice", CallerScope.all(),
            Permission.MONTH_END_TAKE, Permission.MONTH_END_VIEW);
    private static final MonthEndMonthDto TAKEN = new MonthEndMonthDto("2026-08", MonthEndMonthDto.TAKEN,
            "2026-09-15T08:00:00Z", "Invented invented.headoffice (invented.headoffice)", 4L, null, null, 1, null,
            "2026-07-31T22:00:00Z", "2026-08-31T22:00:00Z");
    /** Why a month is taken again (MEN-11), as an administrator would type it. */
    private static final String REASON = "Invented: corrected the workplaces";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndTakes takes;

    @MockitoBean
    private AuditTrail audit;

    @Test
    @DisplayName("🔒 nobody signed in is 401 SIGN_IN_REQUIRED, and nothing is taken")
    void nobodySignedInTakesNothing() throws Exception {
        mockMvc.perform(post("/api/month-end/2026-08/take"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verify(takes, never()).take(any(), any(), any(), any());
    }

    @Test
    @DisplayName("🔒 a signed-in user without MONTH_END_TAKE is 403 PERMISSION_REQUIRED naming it -- a CFO who reads the figures")
    void withoutThePermissionTakesNothing() throws Exception {
        SignedInUser reader = SignInTestUsers.user("invented.cfo", CallerScope.all(), Permission.MONTH_END_VIEW);
        mockMvc.perform(post("/api/month-end/2026-08/take").with(SignInTestUsers.signedIn(reader)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("MONTH_END_TAKE"));
        verify(takes, never()).take(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the holder takes the month, recorded as the person who took it -- and the take is a line in the record")
    void theHolderTakesTheMonth() throws Exception {
        when(takes.month("2026-08")).thenReturn(YearMonth.of(2026, 8));
        when(takes.record("2026-08")).thenReturn(TAKEN);

        mockMvc.perform(post("/api/month-end/2026-08/take").with(SignInTestUsers.signedIn(TAKER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("TAKEN"))
                .andExpect(jsonPath("$.takenBy").value("Invented invented.headoffice (invented.headoffice)"));
        verify(takes).take(eq(YearMonth.of(2026, 8)), eq("Invented invented.headoffice (invented.headoffice)"), isNull(),
                isNull());
        verify(audit).record(eq("MONTH_END_TAKEN"), eq("MONTH_END"), eq("2026-08"), anyString());
    }

    @Test
    @DisplayName("MEN-10: another person in the same position takes it again, saying why (MEN-11) -- the permission is the only gate")
    void theSamePositionTakesItAgain() throws Exception {
        SignedInUser colleague = SignInTestUsers.user("invented.colleague", CallerScope.all(), Permission.MONTH_END_TAKE);
        when(takes.month("2026-08")).thenReturn(YearMonth.of(2026, 8));
        when(takes.record("2026-08")).thenReturn(TAKEN);
        when(takes.reason(YearMonth.of(2026, 8), 3L, REASON)).thenReturn(REASON);

        mockMvc.perform(post("/api/month-end/2026-08/take").with(SignInTestUsers.signedIn(colleague))
                        .contentType("application/json").content("{\"replaces\": 3, \"reason\": \"" + REASON + "\"}"))
                .andExpect(status().isOk());
        verify(takes).take(eq(YearMonth.of(2026, 8)), eq("Invented invented.colleague (invented.colleague)"), eq(3L),
                eq(REASON));
        verify(audit).record(eq("MONTH_END_TAKEN_AGAIN"), eq("MONTH_END"), eq("2026-08"), anyString());
    }

    @Test
    @DisplayName("must-pass: the months taken stay open to anyone, as every read of the arc is")
    void theMonthsStayOpen() throws Exception {
        when(takes.months()).thenReturn(new MonthEndMonthsDto("2026-09", List.of()));

        mockMvc.perform(get("/api/month-end/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openMonth").value("2026-09"));
    }
}

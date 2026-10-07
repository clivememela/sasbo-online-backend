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
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndListingDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndListings;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A LISTING names members (month end 04.3, 04.7, 04.8): held until sign-in (MD4), held by {@code MEMBERS_VIEW} with it
 * -- under the application's real security configuration, with the filter chain on. Nothing is read for a caller
 * without it; the statistics beside it are counts, and stay open.
 */
@WebMvcTest(MonthEndStatisticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndStatisticsController, with the application's real security configuration")
class MonthEndStatisticsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndStatistics statistics;

    @MockitoBean
    private MonthEndListings listings;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"new-members", "deleted-members", "recruited", "representatives"})
    @DisplayName("🔒 nobody signed in is 401 SIGN_IN_REQUIRED -- before anything is read")
    void nobodySignedInReadsNothing(String listing) throws Exception {
        mockMvc.perform(get("/api/month-end/2026-08/listings/" + listing))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        verifyNoInteractions(listings);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"new-members", "deleted-members", "recruited", "representatives"})
    @DisplayName("🔒 a signed-in user without MEMBERS_VIEW is 403 PERMISSION_REQUIRED naming it -- before anything is read")
    void withoutThePermissionReadsNothing(String listing) throws Exception {
        SignedInUser counter = SignInTestUsers.user("invented.counter", CallerScope.all(), Permission.MONTH_END_VIEW);
        mockMvc.perform(get("/api/month-end/2026-08/listings/" + listing).with(SignInTestUsers.signedIn(counter)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("MEMBERS_VIEW"));
        verifyNoInteractions(listings);
    }

    @Test
    @DisplayName("the holder reads a page of the listing, the query passed as asked")
    void theHolderReadsAPage() throws Exception {
        SignedInUser organiser = SignInTestUsers.user("invented.organiser", CallerScope.areas(Set.of("L")),
                Permission.MEMBERS_VIEW);
        when(listings.listing("2026-08", "new-members", null, null, 0, 25)).thenReturn(new MonthEndListingDto(
                "new-members", "The month's new members", "Print Member Roll (No Adding) -- the members enrolled",
                "2026-08", "TAKEN", "AREA", null, null, List.of(new MonthEndListingDto.Row("M-900001", "I", "Invented",
                "2026-08-03", null, null, 900001, "Invented Main Street", "L", "DBN", null, null)), 0, 25, 1, 1,
                List.of()));

        mockMvc.perform(get("/api/month-end/2026-08/listings/new-members?page=0&size=25")
                        .with(SignInTestUsers.signedIn(organiser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("AREA"))
                .andExpect(jsonPath("$.content[0].membershipNumber").value("M-900001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("must-pass: the same chain answers a statistic to anyone -- counts stay open")
    void theStatisticsStayOpen() throws Exception {
        when(statistics.branches("2026-08", null, null)).thenReturn(new MonthEndStatisticDto("branches",
                List.of("bank_master_summ"), "2026-08", "TAKEN", null, null, null, null, "ALL", List.of(), List.of()));

        mockMvc.perform(get("/api/month-end/2026-08/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistic").value("branches"));
    }
}

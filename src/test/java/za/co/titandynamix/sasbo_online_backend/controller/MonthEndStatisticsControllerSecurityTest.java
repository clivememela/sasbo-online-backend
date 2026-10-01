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
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A LISTING names members, and is refused to every caller before anything is read (month end 04.3, 04.7, 04.8; MD4) --
 * under the application's real security configuration, with the filter chain on, which permits {@code /api/**} to
 * anyone (B1): so a 403 here can only be month end's, and the must-pass twin reads a statistic through the same chain.
 */
@WebMvcTest(MonthEndStatisticsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndStatisticsController, with the application's real security configuration")
class MonthEndStatisticsControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndStatistics statistics;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"new-members", "deleted-members", "recruited", "representatives", "a-listing-nobody-built"})
    @DisplayName("🔒 MD4: every listing is refused to every caller, IDENTITY_REQUIRED -- before anything is read")
    void everyListingIsRefused(String listing) throws Exception {
        mockMvc.perform(get("/api/month-end/2026-08/listings/" + listing))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(statistics);
    }

    @Test
    @DisplayName("must-pass: the same chain answers a statistic -- so the 403 above is month end's, not the framework's")
    void theChainLetsAStatisticThrough() throws Exception {
        when(statistics.branches("2026-08", null, null)).thenReturn(new MonthEndStatisticDto("branches",
                List.of("bank_master_summ"), "2026-08", "TAKEN", null, null, null, null, "ALL", List.of(), List.of()));

        mockMvc.perform(get("/api/month-end/2026-08/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistic").value("branches"));
    }
}

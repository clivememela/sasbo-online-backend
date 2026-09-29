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
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndMonthsDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Taking a month is refused to EVERY caller (month end 04.2, MD4) -- under the application's real security
 * configuration, with the filter chain on.
 *
 * <h2>Why this cannot live in {@code MonthEndControllerTest}</h2>
 * That slice runs with the filters off, so a claim about who may call would prove nothing there; and a slice without
 * the real {@code SecurityConfig} asserts Spring Security's defaults, which deny an anonymous caller -- so a 403 there
 * could be the framework's. Here the real configuration is loaded, and it permits {@code /api/**} to anyone (B1): a
 * 403 on the take can only be month end refusing -- which the must-pass twin proves, reading the months through the
 * same chain and getting a 200.
 */
@WebMvcTest(MonthEndController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndController, with the application's real security configuration")
class MonthEndControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndTakes takes;

    @Test
    @DisplayName("🔒 MD4: taking a month is refused to every caller, IDENTITY_REQUIRED -- and nothing is taken")
    void theTakeIsRefusedToEveryone() throws Exception {
        when(takes.month("2026-08")).thenReturn(YearMonth.of(2026, 8));

        mockMvc.perform(post("/api/month-end/2026-08/take"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.month").value("2026-08"));
        verify(takes, never()).take(any(), any());
    }

    @Test
    @DisplayName("must-pass: the same chain answers the months taken -- so the 403 above is month end's, not the framework's")
    void theChainLetsTheReadThrough() throws Exception {
        when(takes.months()).thenReturn(new MonthEndMonthsDto("2026-09", List.of()));

        mockMvc.perform(get("/api/month-end/months"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openMonth").value("2026-09"));
    }
}

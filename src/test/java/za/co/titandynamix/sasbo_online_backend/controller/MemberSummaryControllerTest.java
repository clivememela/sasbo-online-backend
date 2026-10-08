package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/members/summary} (02.2): what a request names reaches the service as it was sent, and the one
 * refusal that is not a 400 -- {@code SUMMARY_UNAVAILABLE} -- carries {@code Retry-After}.
 */
@WebMvcTest(MemberSummaryController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MemberSummaryController")
class MemberSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberSummaryService summaries;

    private static MemberSummaryDto.Figures figures(long onRecord) {
        return new MemberSummaryDto.Figures(onRecord, 0, onRecord, 0, 0, 0, onRecord, 0, 0, 0, 10L,
                new BigDecimal("0.5000"), false);
    }

    @Test
    @DisplayName("the month, the grouping and the area reach the service as sent; the answer is its figures")
    void theRequestReachesTheService() throws Exception {
        when(summaries.summary("2026-09", "area", "A", null)).thenReturn(new MemberSummaryDto("2026-09", false, "area",
                "A", null, "ALL", "2026-09-15", figures(5), List.of(new MemberSummaryDto.Row("A", "A", null, false, false,
                figures(5))), new MemberSummaryDto.Unplaced(List.of(), 0L, 0), 0, "2019-01-01", null, null, null, List.of()));

        mockMvc.perform(get("/api/members/summary").param("month", "2026-09").param("by", "area").param("area", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.totals.paying").value(5))
                .andExpect(jsonPath("$.totals.penetration").value(0.5))
                .andExpect(jsonPath("$.rows[0].key").value("A"));
        verify(summaries).summary("2026-09", "area", "A", null);
    }

    @Test
    @DisplayName("🔒 MD47: SUMMARY_UNAVAILABLE is a 503 that says when to ask again")
    void unavailableSaysWhenToAskAgain() throws Exception {
        when(summaries.summary(any(), any(), any(), any())).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.SUMMARY_UNAVAILABLE, "The figures are taking too long to work out just now."));

        mockMvc.perform(get("/api/members/summary"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", MonthEndExceptionHandler.RETRY_AFTER_SECONDS))
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
    }
}

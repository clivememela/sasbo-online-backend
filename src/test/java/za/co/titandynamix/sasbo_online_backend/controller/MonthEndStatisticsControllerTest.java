package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndStatistics;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/month-end/{month}/...} (month end 04.3 to 04.8): each statistic's route reaches its statistic with the
 * month, the area and the region as sent, and answers its sheets -- a cell with no meaning written as {@code null}; a
 * refusal of the service's answers its code. Who may read a listing is {@code MonthEndStatisticsControllerSecurityTest}'s.
 */
@WebMvcTest(MonthEndStatisticsController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MonthEndStatisticsController")
class MonthEndStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndStatistics statistics;

    /** The listings are a service of their own (sign-in); this slice reaches the statistics only. */
    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.MonthEndListings listings;

    private static MonthEndStatisticDto answer(String statistic) {
        MonthEndStatisticDto.Row row = new MonthEndStatisticDto.Row("A", "A - INVENTED NORTH", MonthEndStatisticDto.Row.ROW,
                false, false, Arrays.asList(BigDecimal.ONE, null, new BigDecimal("0.3667")));
        return new MonthEndStatisticDto(statistic, List.of("prtmemsum"), "2026-08", "TAKEN", "2026-09-01T06:00:00Z",
                "seed", "A", null, "ALL", List.of(new MonthEndStatisticDto.Sheet("month-joined", "Joined in August 2026",
                "SUMMARY", List.of(new MonthEndStatisticDto.Column("core", "Core", "COUNT")), List.of(row), row)),
                List.of("August 2026 was taken."));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"new-and-deleted", "membership", "leavers", "branches", "recruitment", "representatives"})
    @DisplayName("each statistic's route: the month, the area and the region reach it as sent; its sheets come back")
    void eachStatistic(String statistic) throws Exception {
        MonthEndStatisticDto answer = answer(statistic);
        switch (statistic) {
            case "new-and-deleted" -> when(statistics.newAndDeleted("2026-08", "A", null)).thenReturn(answer);
            case "membership" -> when(statistics.membership("2026-08", "A", null)).thenReturn(answer);
            case "leavers" -> when(statistics.leavers("2026-08", "A", null)).thenReturn(answer);
            case "branches" -> when(statistics.branches("2026-08", "A", null)).thenReturn(answer);
            case "recruitment" -> when(statistics.recruitment("2026-08", "A", null)).thenReturn(answer);
            default -> when(statistics.representatives("2026-08", "A", null)).thenReturn(answer);
        }

        mockMvc.perform(get("/api/month-end/2026-08/" + statistic).param("area", "A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statistic").value(statistic))
                .andExpect(jsonPath("$.sheets[0].rows[0].cells[0]").value(1))
                .andExpect(jsonPath("$.sheets[0].rows[0].cells[1]").isEmpty())
                .andExpect(jsonPath("$.sheets[0].rows[0].cells[2]").value(0.3667));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"new-and-deleted", "membership"})
    @DisplayName("a refusal of the statistic's -- a past month not taken -- answers its code")
    void aRefusal(String statistic) throws Exception {
        MonthEndRefusal notTaken = new MonthEndRefusal(MonthEndRefusalCode.MONTH_NOT_TAKEN, "not taken");
        when(statistics.newAndDeleted(any(), any(), any())).thenThrow(notTaken);
        when(statistics.membership(any(), any(), any())).thenThrow(notTaken);

        mockMvc.perform(get("/api/month-end/2026-07/" + statistic))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MONTH_NOT_TAKEN"));
    }
}

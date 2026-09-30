package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndPacks;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The statistics pack's routes (month end 04.10): {@code 202} where an ask starts a job and {@code 200} for one READY
 * or in hand; a pack as the screen polls it; a workbook as an {@code .xlsx} to save; a pack's number that is a word is
 * {@code REQUEST_MALFORMED}, not a 500; the service's refusals answer their codes.
 */
@WebMvcTest(MonthEndPackController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MonthEndPackController")
class MonthEndPackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndPacks packs;

    private static MonthEndPackDto pack(String status) {
        return new MonthEndPackDto(7, "2026-08", status, "system", "2026-09-15T08:00:00Z",
                "READY".equals(status) ? "2026-09-15T08:00:05Z" : null, null,
                "READY".equals(status) ? List.of("statistics", "monthly") : List.of());
    }

    @Test
    @DisplayName("an ask that starts a job is 202; one answered with a pack READY or in hand is 200")
    void aJobIsAccepted() throws Exception {
        when(packs.ask("2026-08")).thenReturn(new MonthEndPacks.Asked(pack("PREPARING"), true));
        mockMvc.perform(post("/api/month-end/2026-08/pack"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PREPARING"));

        when(packs.ask("2026-08")).thenReturn(new MonthEndPacks.Asked(pack("READY"), false));
        mockMvc.perform(post("/api/month-end/2026-08/pack"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workbooks[0]").value("statistics"));
    }

    @Test
    @DisplayName("a pack as the screen polls it; a workbook as an .xlsx to save, named for its month")
    void pollAndFetch() throws Exception {
        when(packs.status(7)).thenReturn(pack("READY"));
        mockMvc.perform(get("/api/month-end/packs/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.finishedAt").value("2026-09-15T08:00:05Z"));

        when(packs.file(7, "statistics")).thenReturn(new MonthEndPacks.File("month-end-2026-08-statistics.xlsx",
                new byte[] {'P', 'K', 3, 4}));
        mockMvc.perform(get("/api/month-end/packs/7/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MonthEndPackController.XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("month-end-2026-08-statistics.xlsx")))
                .andExpect(content().bytes(new byte[] {'P', 'K', 3, 4}));
    }

    @Test
    @DisplayName("ME-11: a pack's number that is a word is REQUEST_MALFORMED naming the field")
    void aWordForANumber() throws Exception {
        mockMvc.perform(get("/api/month-end/packs/PLANTEDWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
        verifyNoInteractions(packs);
    }

    @Test
    @DisplayName("the service's refusals answer their codes: PACK_BUSY 409, PACK_NOT_FOUND 404")
    void theRefusals() throws Exception {
        when(packs.ask("2026-08")).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.PACK_BUSY, "busy"));
        mockMvc.perform(post("/api/month-end/2026-08/pack"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PACK_BUSY"));
        when(packs.status(9)).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.PACK_NOT_FOUND, "none"));
        mockMvc.perform(get("/api/month-end/packs/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PACK_NOT_FOUND"));
    }
}

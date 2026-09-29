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
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MemberHistoryService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/members/{id}/history} (month end 03.6): the page a request names reaches the service; a member's
 * number that is a word is {@code REQUEST_MALFORMED}, not a 500; a page that does not exist is a 400 with its code.
 */
@WebMvcTest(MemberHistoryController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MemberHistoryController")
class MemberHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberHistoryService history;

    @Test
    @DisplayName("the page and its size reach the service; the answer is its entries")
    void thePageReachesTheService() throws Exception {
        when(history.page(7, 1, 20)).thenReturn(new MemberHistoryPageDto(List.of(new MemberHistoryPageDto.Entry(
                "FIELD", 3, "2026-09-10T08:00:00Z", "system", "cellTel", "+27 60 555 0001", "+27 60 555 0002", null,
                null, "invented-save-2", null)), 1, 20, 21, 2));

        mockMvc.perform(get("/api/members/7/history").param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].what").value("cellTel"))
                .andExpect(jsonPath("$.totalPages").value(2));
        verify(history).page(7, 1, 20);
    }

    @Test
    @DisplayName("ME-11: a member's number that is a word is REQUEST_MALFORMED naming the field")
    void aWordForANumber() throws Exception {
        mockMvc.perform(get("/api/members/PLANTEDWORD/history"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("a page that does not exist is HISTORY_PAGE_INVALID, a 400")
    void aPageThatDoesNotExist() throws Exception {
        when(history.page(any(), any(), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.HISTORY_PAGE_INVALID,
                "That page of the history does not exist."));

        mockMvc.perform(get("/api/members/7/history").param("page", "99"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("HISTORY_PAGE_INVALID"));
    }
}

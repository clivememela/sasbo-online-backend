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
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplacePageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/workplaces} (month end 01.2): every narrowing a request may name reaches the service as it was sent,
 * and the page answers in the shape of ACB's pages with the reason an empty one is empty. In a controller of the arc,
 * so a page number that is a word is {@code REQUEST_MALFORMED}, naming the field and giving nothing back -- not a 500.
 */
@WebMvcTest(WorkplaceController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("WorkplaceController")
class WorkplaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkplaceService workplaces;

    @Test
    @DisplayName("each narrowing reaches the service as it was sent, and the page answers ACB's shape")
    void eachNarrowingReachesTheService() throws Exception {
        when(workplaces.page(1, 50, 7L, 902, "A", "Q01", "INVENTED")).thenReturn(new WorkplacePageDto(
                List.of(new WorkplaceDto(1, 900001, "INVENTED BRANCH", "INVENTED BANK", 7L, "INVENTED BANK",
                        "INVENTED GROUP", 902, "NAME", "A", "NORTH", "Q01", "INVENTED COMMITTEE", null,
                        null)),
                1, 50, 51, 2, null));

        mockMvc.perform(get("/api/workplaces").param("page", "1").param("size", "50").param("employer", "7")
                        .param("tableCode", "902").param("area", "A").param("committee", "Q01")
                        .param("search", "INVENTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].raNumber").value(900001))
                .andExpect(jsonPath("$.content[0].tableCode").value(902))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalElements").value(51))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(workplaces).page(1, 50, 7L, 902, "A", "Q01", "INVENTED");
    }

    @Test
    @DisplayName("ME-19: an empty page says why")
    void anEmptyPageSaysWhy() throws Exception {
        when(workplaces.page(null, null, null, null, null, null, null))
                .thenReturn(new WorkplacePageDto(List.of(), 0, 200, 0, 0, "NOT_LOADED"));

        mockMvc.perform(get("/api/workplaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.reason").value("NOT_LOADED"));
    }

    @Test
    @DisplayName("ME-11: a page number that is a word -- REQUEST_MALFORMED, the field named, the word not given back")
    void aPageNumberThatIsAWord() throws Exception {
        mockMvc.perform(get("/api/workplaces").param("page", "PLANTEDWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"))
                .andExpect(content().string(not(containsString("PLANTEDWORD"))));

        verifyNoInteractions(workplaces);
    }

    @Test
    @DisplayName("a page that does not exist is the service's WORKPLACE_PAGE_INVALID, answered as a 400")
    void aPageThatDoesNotExist() throws Exception {
        when(workplaces.page(any(), any(), any(), any(), any(), any(), any())).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "There is no such page of the workplaces."));

        mockMvc.perform(get("/api/workplaces").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKPLACE_PAGE_INVALID"));
    }
}

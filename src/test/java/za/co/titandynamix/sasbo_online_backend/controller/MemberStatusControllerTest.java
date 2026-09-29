package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusAnswer;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusRequest;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.MemberStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/members/{id}/status} (month end 03.1): in a controller of the arc, so a request it cannot read is
 * {@code REQUEST_MALFORMED}, naming the field and giving nothing back -- not the 500 it would be under
 * {@code MemberController} (A63, MD49).
 */
@WebMvcTest(MemberStatusController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MemberStatusController")
class MemberStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberStatusService statuses;

    @Test
    @DisplayName("the body binds as it is sent, and the answer is the member's status and the change written")
    void theBodyBinds() throws Exception {
        when(statuses.change(eq(7), any())).thenReturn(new MemberStatusAnswer(7, "M-900001", "Resigned",
                new MemberStatusAnswer.Change(1, "Active", "Resigned", "DELETION_CODE", 2, "Resigned Society", null,
                        null, null, "system", "2026-09-29T08:00:00Z")));
        ArgumentCaptor<MemberStatusRequest> request = ArgumentCaptor.forClass(MemberStatusRequest.class);

        mockMvc.perform(post("/api/members/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"Resigned\",\"expectedStatus\":\"Active\",\"reason\":\"DELETION_CODE\","
                                + "\"deletionCode\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Resigned"))
                .andExpect(jsonPath("$.change.deletionCode").value(2))
                .andExpect(jsonPath("$.change.changedAt").value("2026-09-29T08:00:00Z"));

        verify(statuses).change(eq(7), request.capture());
        assertThat(request.getValue()).isEqualTo(new MemberStatusRequest("Resigned", "Active", "DELETION_CODE", 2,
                null, null));
    }

    @Test
    @DisplayName("ME-11: a member's id that is a word, a body that is not JSON, a code that is a word -- REQUEST_MALFORMED, the field named")
    void aRequestItCannotRead() throws Exception {
        mockMvc.perform(post("/api/members/PLANTEDWORD/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"))
                .andExpect(content().string(not(containsString("PLANTEDWORD"))));
        mockMvc.perform(post("/api/members/7/status").contentType(MediaType.APPLICATION_JSON).content("{\"toStatus\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
        mockMvc.perform(post("/api/members/7/status").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toStatus\":\"Resigned\",\"deletionCode\":\"PLANTEDCODE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("deletionCode"))
                .andExpect(content().string(not(containsString("PLANTEDCODE"))));
    }
}

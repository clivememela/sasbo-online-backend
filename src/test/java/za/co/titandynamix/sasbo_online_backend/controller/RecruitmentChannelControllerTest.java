package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.RecruitmentChannelDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.RecruitmentChannelService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Copied from {@code BankControllerTest}, with one difference worth naming.
 *
 * <p>This controller catches {@code IllegalArgumentException} itself and answers
 * {@code badRequest().body(e.getMessage())} -- a bare string, not the error
 * envelope every other endpoint produces. The tests assert the 400 status,
 * which is what the browser depends on, and deliberately say nothing about the
 * body: it is {@code ex.getMessage()}, which is copy.
 *
 * <p>The inconsistency is recorded as a finding rather than asserted. Making
 * every endpoint answer with one envelope shape is a production change.
 */
@WebMvcTest(RecruitmentChannelController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RecruitmentChannelController")
class RecruitmentChannelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RecruitmentChannelService recruitmentChannelService;

    @Test
    @DisplayName("GET /api/recruitment-channels serialises the DTO's exact field names")
    void getAllSerialisesFieldNames() throws Exception {
        when(this.recruitmentChannelService.findAll()).thenReturn(List.of(channel("WALK_IN")));

        this.mockMvc.perform(get("/api/recruitment-channels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("WALK_IN"))
                .andExpect(jsonPath("$[0].name").value("Walk in"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    @DisplayName("POST /api/recruitment-channels binds every field of the payload")
    void createBindsThePayload() throws Exception {
        when(this.recruitmentChannelService.create(any(RecruitmentChannelDto.class)))
                .thenReturn(channel("WALK_IN"));

        this.mockMvc.perform(post("/api/recruitment-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(channel("WALK_IN"))))
                .andExpect(status().isOk());

        ArgumentCaptor<RecruitmentChannelDto> bound =
                ArgumentCaptor.forClass(RecruitmentChannelDto.class);
        verify(this.recruitmentChannelService).create(bound.capture());
        assertThat(bound.getValue().getId()).isEqualTo("WALK_IN");
        assertThat(bound.getValue().getName()).isEqualTo("Walk in");
        assertThat(bound.getValue().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("🔴 POST rejects a duplicate with 400 and the API's envelope carrying the sentence -- not a bare string")
    void createRejectsWithBadRequest() throws Exception {
        when(this.recruitmentChannelService.create(any(RecruitmentChannelDto.class)))
                .thenThrow(new IllegalArgumentException("A recruitment type with that name already exists."));

        // The browser reads the envelope's `message`; a plain-text body left it "That request was rejected."
        this.mockMvc.perform(post("/api/recruitment-channels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(channel("WALK_IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("A recruitment type with that name already exists."));
    }

    @Test
    @DisplayName("🔴 PUT rejects a rename onto a taken name the same way")
    void updateRejectsADuplicateInTheEnvelope() throws Exception {
        when(this.recruitmentChannelService.update(org.mockito.ArgumentMatchers.eq(4L), any(RecruitmentChannelDto.class)))
                .thenThrow(new IllegalArgumentException("A recruitment type with that name already exists."));

        this.mockMvc.perform(put("/api/recruitment-channels/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(channel("WALK_IN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A recruitment type with that name already exists."));
    }

    @Test
    @DisplayName("PUT /api/recruitment-channels/{id} answers 404 when the record is missing")
    void updateNotFound() throws Exception {
        when(this.recruitmentChannelService.update(eq(99L), any(RecruitmentChannelDto.class)))
                .thenReturn(Optional.empty());

        this.mockMvc.perform(put("/api/recruitment-channels/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(channel("GONE"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE is 204 when it deleted and 404 when it did not")
    void deleteDistinguishesTheTwoOutcomes() throws Exception {
        when(this.recruitmentChannelService.delete(1L)).thenReturn(true);
        when(this.recruitmentChannelService.delete(99L)).thenReturn(false);

        this.mockMvc.perform(delete("/api/recruitment-channels/1"))
                .andExpect(status().isNoContent());
        this.mockMvc.perform(delete("/api/recruitment-channels/99"))
                .andExpect(status().isNotFound());
    }

    private static RecruitmentChannelDto channel(String id) {
        RecruitmentChannelDto dto = new RecruitmentChannelDto();
        dto.setId(id);
        dto.setName("Walk in");
        dto.setEnabled(true);
        return dto;
    }
}

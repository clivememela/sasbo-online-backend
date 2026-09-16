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
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.RepresentativeService;

import java.time.LocalDate;
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

/** Copied from {@code BankControllerTest}. Writes capture what they bound. */
@WebMvcTest(RepresentativeController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RepresentativeController")
class RepresentativeControllerTest {

    /** Fixed. A test computing an expected date from today changes its own answer. */
    private static final LocalDate ELECTED_ON = LocalDate.of(2023, 6, 15);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RepresentativeService representativeService;

    @Test
    @DisplayName("GET /api/representatives serialises the DTO's exact field names")
    void getAllSerialisesFieldNames() throws Exception {
        when(this.representativeService.findAll()).thenReturn(List.of(representative(1)));

        this.mockMvc.perform(get("/api/representatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].firstNames").value("Thabo"))
                .andExpect(jsonPath("$[0].surname").value("Khumalo"))
                .andExpect(jsonPath("$[0].structureIds").isArray())
                // Present, but deliberately not compared to "2023-06-15": this
                // application serialises dates as [2023,6,15]. That is a real
                // defect, asserted by JacksonConfigTest rather than encoded here.
                .andExpect(jsonPath("$[0].electionDate").exists())
                .andExpect(jsonPath("$[0].membersCount").value(12));
    }

    @Test
    @DisplayName("GET /api/representatives/{id} answers 404 when the record does not exist")
    void getByIdNotFound() throws Exception {
        when(this.representativeService.findById(99)).thenReturn(Optional.empty());

        this.mockMvc.perform(get("/api/representatives/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/representatives binds every field of the payload it was sent")
    void createBindsThePayload() throws Exception {
        when(this.representativeService.create(any(RepresentativeDto.class)))
                .thenReturn(representative(5));

        this.mockMvc.perform(post("/api/representatives")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(representative(null))))
                .andExpect(status().isOk());

        ArgumentCaptor<RepresentativeDto> bound = ArgumentCaptor.forClass(RepresentativeDto.class);
        verify(this.representativeService).create(bound.capture());
        assertThat(bound.getValue().getFirstNames()).isEqualTo("Thabo");
        assertThat(bound.getValue().getSurname()).isEqualTo("Khumalo");
        assertThat(bound.getValue().getStructureIds()).containsExactly(1, 2);
        assertThat(bound.getValue().getElectionDate()).isEqualTo(ELECTED_ON);
    }

    @Test
    @DisplayName("PUT /api/representatives/{id} passes the path id and the bound payload through")
    void updateBindsIdAndPayload() throws Exception {
        when(this.representativeService.update(eq(5), any(RepresentativeDto.class)))
                .thenReturn(Optional.of(representative(5)));

        this.mockMvc.perform(put("/api/representatives/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(representative(null))))
                .andExpect(status().isOk());

        ArgumentCaptor<RepresentativeDto> bound = ArgumentCaptor.forClass(RepresentativeDto.class);
        verify(this.representativeService).update(eq(5), bound.capture());
        assertThat(bound.getValue().getSurname()).isEqualTo("Khumalo");
    }

    @Test
    @DisplayName("DELETE /api/representatives/{id} is 204 when it deleted and 404 when it did not")
    void deleteDistinguishesTheTwoOutcomes() throws Exception {
        // Both halves in one test on purpose: the pair is the contract, and a
        // test covering only the happy path cannot tell a delete that found
        // nothing from one that worked.
        when(this.representativeService.delete(5)).thenReturn(true);
        when(this.representativeService.delete(99)).thenReturn(false);

        this.mockMvc.perform(delete("/api/representatives/5")).andExpect(status().isNoContent());
        this.mockMvc.perform(delete("/api/representatives/99")).andExpect(status().isNotFound());
    }

    private static RepresentativeDto representative(Integer id) {
        RepresentativeDto dto = new RepresentativeDto();
        dto.setId(id);
        dto.setFirstNames("Thabo");
        dto.setSurname("Khumalo");
        dto.setName("Thabo Khumalo");
        dto.setStructureIds(List.of(1, 2));
        dto.setIsElectedRep(true);
        dto.setElectionDate(ELECTED_ON);
        dto.setMembersCount(12);
        return dto;
    }
}

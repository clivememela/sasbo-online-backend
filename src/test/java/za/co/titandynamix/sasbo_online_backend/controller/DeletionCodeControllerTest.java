package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeCreateDto;
import za.co.titandynamix.sasbo_online_backend.dto.deletion.DeletionCodeResponseDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.DeletionCodeService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DeletionCodeController.class)
@Import(GlobalExceptionHandler.class)
@AutoConfigureMockMvc(addFilters = false)
class DeletionCodeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private DeletionCodeService service;

    @Test
    @DisplayName("GET /api/deletion-codes should return all root items")
    void shouldReturnAllRootDeletionCodes() throws Exception {
        DeletionCodeResponseDto subReason = new DeletionCodeResponseDto(11L, "Financial", 2L, List.of());
        DeletionCodeResponseDto rootItem = new DeletionCodeResponseDto(2L, "Resigned Society", null, List.of(subReason));

        when(service.getRootDeletionCodes()).thenReturn(List.of(rootItem));

        mockMvc.perform(get("/api/deletion-codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Resigned Society"))
                .andExpect(jsonPath("$[0].subReasons[0].id").value(11));
    }

    @Test
    @DisplayName("GET /api/deletion-codes/{id} should return 404 when missing")
    void shouldReturn404WhenCodeDoesNotExist() throws Exception {
        when(service.getDeletionCodeById(99L)).thenThrow(new EntityNotFoundException("Code not found"));

        mockMvc.perform(get("/api/deletion-codes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("POST /api/deletion-codes should return 400 Bad Request when validation fails")
    void shouldReturn400WhenPayloadIsInvalid() throws Exception {
        DeletionCodeCreateDto invalidDto = new DeletionCodeCreateDto("", null);

        mockMvc.perform(post("/api/deletion-codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.name").exists());
    }
}

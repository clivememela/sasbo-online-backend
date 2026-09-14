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
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.ProvinceService;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Copied from {@code BankControllerTest}. Real DTOs, real serialisation. */
@WebMvcTest(ProvinceController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProvinceController")
class ProvinceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProvinceService provinceService;

    @Test
    @DisplayName("GET /api/provinces serialises the DTO's exact field names")
    void getAllSerialisesFieldNames() throws Exception {
        ProvinceDto dto = new ProvinceDto();
        dto.setId(3L);
        dto.setName("Gauteng");
        when(this.provinceService.findAll()).thenReturn(List.of(dto));

        this.mockMvc.perform(get("/api/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Gauteng"));
    }

    @Test
    @DisplayName("GET /api/provinces answers with an empty array, not a 404, when there are none")
    void getAllEmpty() throws Exception {
        // An empty collection is a successful answer. A 404 here would make the
        // browser treat "no reference data loaded" as a broken endpoint.
        when(this.provinceService.findAll()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("a service failure becomes the error envelope, not a bare stack trace")
    void serviceFailureProducesTheEnvelope() throws Exception {
        when(this.provinceService.findAll()).thenThrow(new IllegalStateException("boom"));

        this.mockMvc.perform(get("/api/provinces"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.path").value("/api/provinces"));
    }
}

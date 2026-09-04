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
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The one slice that mocks a repository, because this controller injects one
 * directly. That is a layering violation, recorded and asserted by
 * {@code LayeringTest} -- the test mirrors the code as it is rather than the
 * code as it should be, and the finding lives in one place instead of two.
 */
@WebMvcTest(AreaController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AreaController")
class AreaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AreaRepository areaRepository;

    @Test
    @DisplayName("GET /api/areas maps rows to the DTO's exact field names")
    void getAllSerialisesFieldNames() throws Exception {
        Area area = new Area();
        area.setId(2);
        area.setName("Northern Gauteng");
        when(this.areaRepository.findAll()).thenReturn(List.of(area));

        this.mockMvc.perform(get("/api/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].name").value("Northern Gauteng"));
    }

    @Test
    @DisplayName("GET /api/areas answers with an empty array when there are none")
    void getAllEmpty() throws Exception {
        when(this.areaRepository.findAll()).thenReturn(List.of());

        this.mockMvc.perform(get("/api/areas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}

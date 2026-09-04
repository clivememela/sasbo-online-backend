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
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.mapper.RepresentativeRoleMapper;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The second slice that mocks a repository, because this controller injects one
 * directly and does its own mapping. That is a layering violation, recorded and
 * asserted by {@code LayeringTest}; the test mirrors the code as it is, and the
 * finding lives in one place rather than two.
 *
 * <p>The mapper is mocked here rather than used for real -- which the reference
 * pattern forbids for <i>content</i> assertions -- so every assertion below is
 * about status, routing or a captured argument, never about a field value the
 * stub supplied. Where content matters, it is asserted in
 * {@code RepresentativeRoleMapperTest} against the real mapper.
 */
@WebMvcTest(RepresentativeRoleController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("RepresentativeRoleController")
class RepresentativeRoleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RepresentativeRoleRepository repository;

    @MockitoBean
    private RepresentativeRoleMapper mapper;

    @Test
    @DisplayName("GET /api/representative-userRoles maps every row it was given")
    void getAllMapsEveryRow() throws Exception {
        // The claim is "every row is mapped", so the stub returns two and the
        // assertion counts two. That is a claim about the controller's loop,
        // not about the values, which is the part a mocked mapper can support.
        RepresentativeRole first = new RepresentativeRole();
        RepresentativeRole second = new RepresentativeRole();
        when(this.repository.findAll()).thenReturn(List.of(first, second));
        when(this.mapper.toDto(any(RepresentativeRole.class))).thenReturn(new RepresentativeRoleDto());

        this.mockMvc.perform(get("/api/representative-userRoles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(this.mapper, org.mockito.Mockito.times(2)).toDto(any(RepresentativeRole.class));
    }

    @Test
    @DisplayName("GET /api/representative-userRoles/{id} answers 404 when the role is missing")
    void getByIdNotFound() throws Exception {
        when(this.repository.findById(99)).thenReturn(Optional.empty());

        this.mockMvc.perform(get("/api/representative-userRoles/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST binds the payload and saves the entity it mapped")
    void createSavesTheMappedEntity() throws Exception {
        RepresentativeRoleDto sent = new RepresentativeRoleDto();
        sent.setName("Branch Representative");

        RepresentativeRole mapped = new RepresentativeRole();
        when(this.mapper.toEntity(any(RepresentativeRoleDto.class))).thenReturn(mapped);
        when(this.repository.save(any(RepresentativeRole.class))).thenReturn(mapped);
        when(this.mapper.toDto(any(RepresentativeRole.class))).thenReturn(sent);

        this.mockMvc.perform(post("/api/representative-userRoles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(sent)))
                .andExpect(status().is2xxSuccessful());

        // What the JSON bound to. Without this the payload could be {} and the
        // stubbed response would look identical.
        ArgumentCaptor<RepresentativeRoleDto> bound =
                ArgumentCaptor.forClass(RepresentativeRoleDto.class);
        verify(this.mapper).toEntity(bound.capture());
        assertThat(bound.getValue().getName()).isEqualTo("Branch Representative");

        // And that the thing saved is the thing the mapper produced, rather
        // than a fresh entity built somewhere in the controller.
        ArgumentCaptor<RepresentativeRole> saved =
                ArgumentCaptor.forClass(RepresentativeRole.class);
        verify(this.repository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(mapped);
    }

    @Test
    @DisplayName("DELETE answers 404 rather than 204 for a role that does not exist")
    void deleteNotFound() throws Exception {
        when(this.repository.existsById(99)).thenReturn(false);

        this.mockMvc.perform(delete("/api/representative-userRoles/99"))
                .andExpect(status().isNotFound());
    }
}

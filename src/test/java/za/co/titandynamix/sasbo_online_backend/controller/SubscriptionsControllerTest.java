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
import za.co.titandynamix.sasbo_online_backend.dto.subscription.SubscriptionsDto;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.SubscriptionsService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
@WebMvcTest(SubscriptionsController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("SubscriptionsController")
class SubscriptionsControllerTest {

    private static final LocalDateTime REGISTERED = LocalDateTime.of(2024, 3, 1, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionsService subscriptionsService;

    @Test
    @DisplayName("GET /api/subscriptions serialises the record's exact field names")
    void getAllSerialisesFieldNames() throws Exception {
        when(this.subscriptionsService.findAll(null)).thenReturn(List.of(subscription(1L)));

        this.mockMvc.perform(get("/api/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].bankName").value("Absa"))
                .andExpect(jsonPath("$[0].acbDebitDay").value(25))
                .andExpect(jsonPath("$[0].subscriptionAmount").value(120.50))
                .andExpect(jsonPath("$[0].isCoreBanking").value(true))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    @DisplayName("GET /api/subscriptions passes the bankId filter through as given")
    void getAllPassesTheFilterThrough() throws Exception {
        // The filter is the only thing this endpoint decides. Without asserting
        // it, the controller could ignore the parameter entirely and the test
        // would still pass on the stubbed response.
        when(this.subscriptionsService.findAll(7L)).thenReturn(List.of());

        this.mockMvc.perform(get("/api/subscriptions").param("bankId", "7"))
                .andExpect(status().isOk());

        verify(this.subscriptionsService).findAll(7L);
    }

    @Test
    @DisplayName("GET /api/subscriptions/{id} answers 404 when the record does not exist")
    void getByIdNotFound() throws Exception {
        when(this.subscriptionsService.findById(99L)).thenReturn(Optional.empty());

        this.mockMvc.perform(get("/api/subscriptions/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/subscriptions binds every field of the payload it was sent")
    void createBindsThePayload() throws Exception {
        SubscriptionsDto sent = subscription(null);
        when(this.subscriptionsService.create(any(SubscriptionsDto.class)))
                .thenReturn(subscription(4L));

        this.mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(sent)))
                // 201, correctly -- unlike POST /api/members, which answers 200
                // with no Location header. The inconsistency between the two is
                // recorded as a finding.
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(4));

        ArgumentCaptor<SubscriptionsDto> bound = ArgumentCaptor.forClass(SubscriptionsDto.class);
        verify(this.subscriptionsService).create(bound.capture());
        assertThat(bound.getValue().bankName()).isEqualTo("Absa");
        assertThat(bound.getValue().acbDebitDay()).isEqualTo(25);
        assertThat(bound.getValue().subscriptionAmount())
                .isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(bound.getValue().isCoreBanking()).isTrue();
        assertThat(bound.getValue().status()).isEqualTo(SubscriptionsStatus.ACTIVE);
    }

    @Test
    @DisplayName("PUT /api/subscriptions/{id} passes the path id and the bound payload through")
    void updateBindsIdAndPayload() throws Exception {
        when(this.subscriptionsService.update(eq(4L), any(SubscriptionsDto.class)))
                .thenReturn(Optional.of(subscription(4L)));

        this.mockMvc.perform(put("/api/subscriptions/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(this.objectMapper.writeValueAsString(subscription(null))))
                .andExpect(status().isOk());

        ArgumentCaptor<SubscriptionsDto> bound = ArgumentCaptor.forClass(SubscriptionsDto.class);
        verify(this.subscriptionsService).update(eq(4L), bound.capture());
        assertThat(bound.getValue().bankName()).isEqualTo("Absa");
    }

    @Test
    @DisplayName("DELETE /api/subscriptions/{id} is 204 when it deleted and 404 when it did not")
    void deleteDistinguishesTheTwoOutcomes() throws Exception {
        // Both halves in one test on purpose: the pair is the contract, and a
        // test that only covers the happy path cannot tell a delete that found
        // nothing from one that worked.
        when(this.subscriptionsService.delete(4L)).thenReturn(true);
        when(this.subscriptionsService.delete(99L)).thenReturn(false);

        this.mockMvc.perform(delete("/api/subscriptions/4")).andExpect(status().isNoContent());
        this.mockMvc.perform(delete("/api/subscriptions/99")).andExpect(status().isNotFound());
    }

    private static SubscriptionsDto subscription(Long id) {
        return new SubscriptionsDto(id, "Absa", 25, new BigDecimal("120.50"), true,
                SubscriptionsStatus.ACTIVE, REGISTERED);
    }
}

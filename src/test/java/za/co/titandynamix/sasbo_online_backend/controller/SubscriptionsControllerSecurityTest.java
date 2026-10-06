package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Only staff change amounts (the 5 Oct Show &amp; Tell): the three writes need {@code SUBSCRIPTIONS_MAINTAIN}, and the
 * reads stay open because the capture form reads them. With the application's real security configuration -- a bare
 * slice would assert Spring Security's defaults, not this application's.
 */
@WebMvcTest(SubscriptionsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
@DisplayName("SubscriptionsController, with the application's real security configuration")
class SubscriptionsControllerSecurityTest {

    private static final LocalDateTime REGISTERED = LocalDateTime.of(2024, 3, 1, 9, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionsService subscriptionsService;

    private String row() throws Exception {
        return this.objectMapper.writeValueAsString(new SubscriptionsDto(null, "Absa", 25, new BigDecimal("174.00"),
                true, SubscriptionsStatus.ACTIVE, REGISTERED));
    }

    /**
     * An anonymous caller's refusal: a 4xx, and never the catch-all's 500. 403 today (Http403ForbiddenEntryPoint); the
     * sign-in stream's entry point makes it 401 SIGN_IN_REQUIRED, which this still holds (contract, signin.md).
     */
    private static ResultMatcher refusedAsAnonymous() {
        return result -> assertThat(result.getResponse().getStatus()).as("a refused anonymous write")
                .isBetween(400, 499);
    }

    @Test
    @WithMockUser(authorities = "SUBSCRIPTIONS_MAINTAIN")
    @DisplayName("staff holding SUBSCRIPTIONS_MAINTAIN add, change and remove a row")
    void staffWithThePermissionMayWrite() throws Exception {
        when(this.subscriptionsService.create(any(SubscriptionsDto.class))).thenReturn(
                new SubscriptionsDto(4L, "Absa", 25, new BigDecimal("174.00"), true, SubscriptionsStatus.ACTIVE, REGISTERED));
        when(this.subscriptionsService.update(eq(4L), any(SubscriptionsDto.class))).thenReturn(Optional.empty());
        when(this.subscriptionsService.delete(4L)).thenReturn(true);

        this.mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(status().isCreated());
        this.mockMvc.perform(put("/api/subscriptions/4").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(status().isNotFound());
        this.mockMvc.perform(delete("/api/subscriptions/4"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a signed-in user without the permission is refused every write, and nothing reaches the service")
    void aUserWithoutThePermissionIsRefused() throws Exception {
        this.mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(status().isForbidden());
        this.mockMvc.perform(put("/api/subscriptions/4").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(status().isForbidden());
        this.mockMvc.perform(delete("/api/subscriptions/4"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(this.subscriptionsService);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("an anonymous caller is refused every write while sign-in is open, and nothing reaches the service")
    void anAnonymousCallerIsRefusedTheWrites() throws Exception {
        this.mockMvc.perform(post("/api/subscriptions").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(refusedAsAnonymous());
        this.mockMvc.perform(put("/api/subscriptions/4").contentType(MediaType.APPLICATION_JSON).content(row()))
                .andExpect(refusedAsAnonymous());
        this.mockMvc.perform(delete("/api/subscriptions/4"))
                .andExpect(refusedAsAnonymous());

        verifyNoInteractions(this.subscriptionsService);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("the must-pass: the reads stay open to anyone -- the capture form shows the fee and the bank's day")
    void theReadsStayOpen() throws Exception {
        when(this.subscriptionsService.findAll(null)).thenReturn(List.of());

        this.mockMvc.perform(get("/api/subscriptions")).andExpect(status().isOk());
    }
}

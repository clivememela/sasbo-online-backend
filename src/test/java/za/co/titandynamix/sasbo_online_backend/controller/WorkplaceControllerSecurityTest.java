package za.co.titandynamix.sasbo_online_backend.controller;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceVisitService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The direct visit route, {@code POST /api/workplaces/{id}/visits}, held as the visit drafts are (reports-visits review,
 * 7 Oct 2026): it records the same visit through the same {@code WorkplaceVisitService.record} and writes the same
 * complement, so a signed-in user without {@code VISITS_CAPTURE} is refused -- while a caller who is not signed in is
 * still answered while sign-in is open, as the Location Visit form's one-step Save visit was (contracts/signin.md).
 * Under the application's real security configuration, with the filter chain on.
 */
@WebMvcTest(WorkplaceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("WorkplaceController's visit route, with the application's real security configuration")
class WorkplaceControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkplaceService workplaces;

    @MockitoBean
    private WorkplaceVisitService visits;

    private static MockHttpServletRequestBuilder aVisit() {
        return post("/api/workplaces/7/visits").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitedOn\": \"2026-09-10\", \"complementFound\": 55}");
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 review: a signed-in user WITHOUT VISITS_CAPTURE cannot record a visit -- and overwrite a complement -- by the direct route")
    void theDirectRouteIsHeldByVisitsCapture() throws Exception {
        mockMvc.perform(aVisit()).andExpect(status().isForbidden());

        verifyNoInteractions(visits);
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("must-pass: the permission's holder records the visit")
    void theHolderRecordsIt() throws Exception {
        when(visits.record(eq(7L), any())).thenReturn(new WorkplaceVisitPageDto.Visit(99, "2026-09-10", true, 55, 40,
                false));

        mockMvc.perform(aVisit()).andExpect(status().isCreated());

        verify(visits).record(eq(7L), any());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("must-pass: nobody signed in, while sign-in is open, still records it -- the form's one-step Save visit")
    void anAnonymousCallerStillRecordsIt() throws Exception {
        when(visits.record(eq(7L), any())).thenReturn(new WorkplaceVisitPageDto.Visit(99, "2026-09-10", true, 55, 40,
                false));

        mockMvc.perform(aVisit()).andExpect(status().isCreated());
    }
}

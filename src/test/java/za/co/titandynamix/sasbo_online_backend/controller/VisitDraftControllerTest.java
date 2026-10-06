package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftRequest;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftSubmitRequest;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceVisitDraftService;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The visit forms' routes (October build), under the application's real security configuration with the filter chain
 * on: each answers {@code VISITS_CAPTURE}, refuses anyone else before anything is read or written, and says a refusal
 * of the service's with its code and status.
 */
@WebMvcTest(VisitDraftController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("VisitDraftController, with the application's real security configuration")
class VisitDraftControllerTest {

    private static final String SAVED = "{\"raNumber\": 900001, \"visitedOn\": \"2026-09-10\", \"timeIn\": \"09:00\", "
            + "\"organiser\": \"INVENTED ORGANISER\", \"complementFound\": 40, \"version\": 0}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkplaceVisitDraftService drafts;

    private static VisitDraftDto draft(String status, int version) {
        return new VisitDraftDto(5, status, 7, 900001, "INVENTED MAIN STREET", "2026-09-10", "09:00", null,
                "INVENTED ORGANISER", 40, null, null, null, null, null, "system", "2026-09-10T08:00:00Z",
                "2026-09-10T08:00:00Z", "SUBMITTED".equals(status) ? "system" : null,
                "SUBMITTED".equals(status) ? "2026-09-10T09:00:00Z" : null, "SUBMITTED".equals(status) ? 3L : null,
                version);
    }

    /** Each route, as a request with a body where it takes one. */
    private static MockHttpServletRequestBuilder route(String which) {
        return switch (which) {
            case "mine" -> get("/api/workplaces/visit-drafts");
            case "one" -> get("/api/workplaces/visit-drafts/5");
            case "create" -> post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON).content(SAVED);
            case "update" -> put("/api/workplaces/visit-drafts/5").contentType(MediaType.APPLICATION_JSON).content(SAVED);
            case "submit" -> post("/api/workplaces/visit-drafts/5/submit").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\": 0}");
            default -> throw new IllegalArgumentException(which);
        };
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("🔒 5 Oct: a draft is saved (201), read back, saved again and submitted -- each answering VISITS_CAPTURE")
    void theDraftsRoutesAnswerTheirPermission() throws Exception {
        when(drafts.create(any())).thenReturn(draft("DRAFT", 0));
        when(drafts.one(5L)).thenReturn(draft("DRAFT", 0));
        when(drafts.update(eq(5L), any())).thenReturn(draft("DRAFT", 1));
        when(drafts.submit(eq(5L), any())).thenReturn(draft("SUBMITTED", 2));
        when(drafts.mine(null, null, null)).thenReturn(new VisitDraftPageDto(List.of(draft("DRAFT", 0)), "DRAFT", 0,
                20, 1, 1));

        mockMvc.perform(route("create")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"));
        mockMvc.perform(route("one")).andExpect(status().isOk()).andExpect(jsonPath("$.raNumber").value(900001));
        mockMvc.perform(route("update")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(route("submit")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.visitId").value(3));
        mockMvc.perform(route("mine")).andExpect(status().isOk()).andExpect(jsonPath("$.drafts[0].id").value(5));

        verify(drafts).create(new VisitDraftRequest(900001, "2026-09-10", "09:00", null, "INVENTED ORGANISER", 40,
                null, null, null, null, 0));
        verify(drafts).submit(5L, new VisitDraftSubmitRequest(0));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mine", "one", "create", "update", "submit"})
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 the contract: a user without VISITS_CAPTURE is refused 403 on every route, and nothing is read or written")
    void aUserWithoutThePermissionIsRefused(String which) throws Exception {
        mockMvc.perform(route(which)).andExpect(status().isForbidden());

        verifyNoInteractions(drafts);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mine", "one", "create", "update", "submit"})
    @WithAnonymousUser
    @DisplayName("🔒 the contract: nobody signed in is refused on every route -- not a 2xx, not a 500 -- and nothing is touched")
    void nobodySignedInIsRefused(String which) throws Exception {
        mockMvc.perform(route(which)).andExpect(status().is4xxClientError());

        verifyNoInteractions(drafts);
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("a validation refusal is a coded 400 naming its field -- VISIT_INVALID")
    void aValidationRefusal() throws Exception {
        when(drafts.create(any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.VISIT_INVALID,
                "A visit cannot be on a day still to come. Nothing was saved.", Map.of("field", "visitedOn")));

        mockMvc.perform(route("create"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VISIT_INVALID"))
                .andExpect(jsonPath("$.details.field").value("visitedOn"));
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("the drafts' own refusals answer their statuses: stale 409, submitted 409, none of mine 404")
    void theDraftsRefusals() throws Exception {
        when(drafts.update(eq(5L), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.VISIT_DRAFT_STALE,
                "This draft was saved again after you opened it.", Map.of("version", "3")));
        mockMvc.perform(route("update")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_DRAFT_STALE"))
                .andExpect(jsonPath("$.details.version").value("3"));

        when(drafts.submit(eq(5L), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.VISIT_ALREADY_SUBMITTED,
                "This visit has been submitted.", Map.of("visitId", "3")));
        mockMvc.perform(route("submit")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VISIT_ALREADY_SUBMITTED"));

        when(drafts.one(5L)).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.VISIT_DRAFT_NOT_FOUND,
                "That visit draft is not one of yours, or is no longer there.", Map.of("draftId", "5")));
        mockMvc.perform(route("one")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("VISIT_DRAFT_NOT_FOUND"));
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("ME-11: a body that is not JSON is REQUEST_MALFORMED -- not a 500, and nothing written")
    void aBodyThatIsNotJson() throws Exception {
        mockMvc.perform(post("/api/workplaces/visit-drafts").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));

        verifyNoInteractions(drafts);
    }
}

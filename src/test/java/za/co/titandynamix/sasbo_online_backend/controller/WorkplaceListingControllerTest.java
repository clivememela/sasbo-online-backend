package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceListingsDto;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceMemberPageDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceListingService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Location Listing's two routes (October build), under the application's real security configuration with the
 * filter chain on: the batch print's workplaces answer anyone, as every read of organisations does; the members at a
 * branch answer only {@code MEMBERS_VIEW}, and say why when they refuse.
 */
@WebMvcTest(WorkplaceListingController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("WorkplaceListingController, with the application's real security configuration")
class WorkplaceListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkplaceListingService listings;

    private static final WorkplaceMemberPageDto PAGE = new WorkplaceMemberPageDto(1, List.of(
            new WorkplaceMemberPageDto.Member(11, "M-090001", "MS", "A", "INVENTED", "Active", false, "2024-03-01",
                    "****0042", new BigDecimal("174.00"), true, false, true)), 0, 50, 1, 1);

    @Test
    @WithAnonymousUser
    @DisplayName("5 Oct: the batch print answers anyone -- the numbers reach the service as typed, comma-separated or repeated")
    void theBatchAnswersAnyone() throws Exception {
        when(listings.listings(List.of("900001", "900002"))).thenReturn(new WorkplaceListingsDto(
                List.of(900001, 900002), List.of(), List.of(900001, 900002), "ALL", null));

        mockMvc.perform(get("/api/workplaces/listings").param("raNumbers", "900001,900002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unknown[1]").value(900002));
        mockMvc.perform(get("/api/workplaces/listings").param("raNumbers", "900001").param("raNumbers", "900002"))
                .andExpect(status().isOk());

        verify(listings, org.mockito.Mockito.times(2)).listings(List.of("900001", "900002"));
    }

    @Test
    @DisplayName("the batch's refusal is a coded 400 -- RA_NUMBERS_INVALID, saying which, and nothing of what was sent")
    void theBatchsRefusal() throws Exception {
        when(listings.listings(any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.RA_NUMBERS_INVALID,
                "Each R/A number is a number of up to nine digits. Nothing was looked up.",
                MonthEndRefusal.details("reason", "NOT_A_NUMBER", "max", "50")));

        mockMvc.perform(get("/api/workplaces/listings").param("raNumbers", "PLANTEDWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RA_NUMBERS_INVALID"))
                .andExpect(jsonPath("$.details.reason").value("NOT_A_NUMBER"))
                .andExpect(content().string(not(containsString("PLANTEDWORD"))));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 5 Oct: the members at a branch answer MEMBERS_VIEW -- masked, a page at a time")
    void theMembersAnswerTheirPermission() throws Exception {
        when(listings.members(1L, 0, 50)).thenReturn(PAGE);

        mockMvc.perform(get("/api/workplaces/1/members").param("page", "0").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].membershipNumber").value("M-090001"))
                .andExpect(jsonPath("$.members[0].employeeNumber").value("****0042"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(authorities = "VISITS_CAPTURE")
    @DisplayName("🔒 the contract: a user without MEMBERS_VIEW is refused 403, and nothing is read")
    void aUserWithoutThePermissionIsRefused() throws Exception {
        mockMvc.perform(get("/api/workplaces/1/members")).andExpect(status().isForbidden());

        verifyNoInteractions(listings);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 the contract: nobody signed in is refused -- not a 2xx, and not a 500 -- and nothing is read")
    void nobodySignedInIsRefused() throws Exception {
        mockMvc.perform(get("/api/workplaces/1/members")).andExpect(status().is4xxClientError());

        verifyNoInteractions(listings);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("a branch outside the caller's area is the service's WORKPLACE_OUT_OF_SCOPE, answered 403 with its code")
    void outsideTheCallersArea() throws Exception {
        when(listings.members(1L, null, null)).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.WORKPLACE_OUT_OF_SCOPE,
                "That workplace is outside your region or area.", Map.of("workplaceId", "1")));

        mockMvc.perform(get("/api/workplaces/1/members"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("WORKPLACE_OUT_OF_SCOPE"));
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("ME-11: a page number that is a word is REQUEST_MALFORMED, naming the field -- not a 500")
    void aPageNumberThatIsAWord() throws Exception {
        mockMvc.perform(get("/api/workplaces/1/members").param("page", "PLANTEDWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"))
                .andExpect(content().string(not(containsString("PLANTEDWORD"))));

        verifyNoInteractions(listings);
    }
}

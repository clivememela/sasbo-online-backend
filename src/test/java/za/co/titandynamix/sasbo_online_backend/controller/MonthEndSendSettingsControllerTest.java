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
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBankDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackSetDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndProviderDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndSendSettingsDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndSendSettings;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who gets what at month end, through its routes (October build) -- under the application's real security
 * configuration, so MONTH_END_SEND is enforced as it is in production: the holder is answered; a user without it is
 * refused 403; nobody signed in is refused; a refusal of the service answers its code; a malformed body is
 * REQUEST_MALFORMED, never a 500.
 */
@WebMvcTest(MonthEndSendSettingsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndSendSettingsController")
class MonthEndSendSettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndSendSettings settings;

    static final MonthEndPackSetDto JHB = new MonthEndPackSetDto("JHB", "Johannesburg (first set)", "JHB", "JHB",
            List.of("A", "F", "K", "X"), "Regional Secretary", "regional.secretary.jhb@example.org", true, 0, "seed",
            "2026-10-01T05:00:00Z");

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("the settings, to MONTH_END_SEND's holder")
    void theSettings() throws Exception {
        when(settings.settings()).thenReturn(new MonthEndSendSettingsDto(List.of(JHB),
                List.of(new MonthEndBankDto("ABSA", "Absa", List.of(24, 78, 90), true, "ABSA_FIXED_WIDTH", true, 0,
                        "seed", "2026-10-01T05:00:00Z")),
                List.of(new MonthEndProviderDto("LAW_CALL", "Law Call (Azoza)", "PROVISIONAL_MEMBER_LIST", true, "SFTP",
                        true, 0, "seed", "2026-10-01T05:00:00Z")), new MonthEndSendSettingsDto.Mail(false),
                new MonthEndSendSettingsDto.Sftp(false, "NOT_CONFIGURED"),
                new MonthEndSendSettingsDto.Layouts(List.of("ABSA_FIXED_WIDTH", "SHEET", "CSV"),
                        List.of("OLD_MUTUAL_FUNERAL", "PROVISIONAL_MEMBER_LIST")),
                new MonthEndSendSettingsDto.Ceilings(20, 60)));

        mockMvc.perform(get("/api/month-end/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sets[0].areaLetters[3]").value("X"))
                .andExpect(jsonPath("$.banks[0].tableCodes[0]").value(24))
                .andExpect(jsonPath("$.sftp.state").value("NOT_CONFIGURED"))
                .andExpect(jsonPath("$.mail.configured").value(false));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("a recipient created is 201; changed is 200; a field refused is its code, naming the field")
    void writes() throws Exception {
        when(settings.createSet(any())).thenReturn(JHB);
        mockMvc.perform(post("/api/month-end/settings/recipients").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\": \"JHB\", \"label\": \"Johannesburg (first set)\", \"regionCode\": \"JHB\", "
                                + "\"areaLetters\": [\"A\"], \"position\": \"Regional Secretary\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("JHB"));

        when(settings.updateSet(eq("JHB"), any())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.PACK_SET_INVALID,
                "That is not an e-mail address.", Map.of("field", "email")));
        mockMvc.perform(put("/api/month-end/settings/recipients/JHB").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-address\", \"version\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PACK_SET_INVALID"))
                .andExpect(jsonPath("$.details.field").value("email"));

        when(settings.updateProvider(eq("CRITICARE"), any())).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.SETTING_CHANGED_SINCE, "Someone saved this since.", Map.of("version", "2")));
        mockMvc.perform(put("/api/month-end/settings/providers/CRITICARE").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\": true, \"version\": 1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETTING_CHANGED_SINCE"));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("ME-11: a body that is not JSON is REQUEST_MALFORMED, never a 500")
    void malformed() throws Exception {
        mockMvc.perform(post("/api/month-end/settings/banks").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableCodes\": \"PLANTEDWORD\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
        verifyNoInteractions(settings);
    }

    @Test
    @WithMockUser(authorities = "MEMBERS_VIEW")
    @DisplayName("🔒 a user without MONTH_END_SEND is refused 403, and the service is never asked")
    void withoutThePermission() throws Exception {
        mockMvc.perform(get("/api/month-end/settings")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/month-end/settings/banks").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(settings);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 nobody signed in is refused -- not answered, and not a 500")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/month-end/settings")).andExpect(status().is4xxClientError());
        verifyNoInteractions(settings);
    }
}

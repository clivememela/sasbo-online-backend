package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.reference.CoreTiersDto;
import za.co.titandynamix.sasbo_online_backend.dto.reference.ReferenceRows;
import za.co.titandynamix.sasbo_online_backend.dto.reference.TableCodeTierDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.security.Permission;
import za.co.titandynamix.sasbo_online_backend.security.SignedInUser;
import za.co.titandynamix.sasbo_online_backend.services.CoreTiers;
import za.co.titandynamix.sasbo_online_backend.support.SignInTestUsers;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Core A, B and C (Stephne, MEN-31: "Decisions are made by Senior Secretariat"), under {@code /api/reference} -- under
 * the application's real security configuration, with the filter chain on. The tiers are open, as every list of
 * SASBO's is; a table code's tier is set by {@code REFERENCE_MAINTAIN}. What setting one does is {@code CoreTiersIT}'s,
 * on PostgreSQL.
 */
@WebMvcTest(CoreTierController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("CoreTierController, with the application's real security configuration")
class CoreTierControllerTest {

    private static final String TIER_OF_44 = "/api/reference/table-codes/44/core-tier";

    private static final SignedInUser MAINTAINER = SignInTestUsers.user("invented.maintainer", CallerScope.all(),
            Permission.REFERENCE_MAINTAIN);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CoreTiers tiers;

    @Test
    @DisplayName("MEN-31: the tiers and their table codes are open to anyone, with the words for every other institution")
    void theTiersAreOpen() throws Exception {
        when(tiers.list()).thenReturn(new CoreTiersDto(List.of(new ReferenceRows.CoreTier("A", "Core A", 1, true,
                List.of(901, 902))), CoreTiers.ALL_OTHER));

        mockMvc.perform(get("/api/reference/core-tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tiers[0].code").value("A"))
                .andExpect(jsonPath("$.tiers[0].provisional").value(true))
                .andExpect(jsonPath("$.tiers[0].tableCodes[1]").value(902))
                .andExpect(jsonPath("$.allOther").value("All other institutions"));
    }

    @Test
    @DisplayName("🔒 setting a tier: nobody signed in is 401, a user without REFERENCE_MAINTAIN 403 naming it -- and nothing is set")
    void settingATierIsHeld() throws Exception {
        SignedInUser reader = SignInTestUsers.user("invented.cfo", CallerScope.all(), Permission.MONTH_END_VIEW);

        mockMvc.perform(put(TIER_OF_44).contentType(MediaType.APPLICATION_JSON).content("{\"tier\": \"B\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"));
        mockMvc.perform(put(TIER_OF_44).with(SignInTestUsers.signedIn(reader)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\": \"B\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"))
                .andExpect(jsonPath("$.permission").value("REFERENCE_MAINTAIN"));
        verifyNoInteractions(tiers);
    }

    @Test
    @DisplayName("the holder sets a code's tier -- and sets it in none, for all other institutions, with no body or a null tier")
    void theHolderSetsATier() throws Exception {
        when(tiers.setTier(44, "B")).thenReturn(new TableCodeTierDto(44, "INVENTED CODE", "B",
                "Invented invented.maintainer (invented.maintainer)", "2026-10-07T09:00:00Z"));
        when(tiers.setTier(44, null)).thenReturn(new TableCodeTierDto(44, "INVENTED CODE", null,
                "Invented invented.maintainer (invented.maintainer)", "2026-10-07T09:01:00Z"));

        mockMvc.perform(put(TIER_OF_44).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\": \"B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coreTier").value("B"))
                .andExpect(jsonPath("$.setBy").value("Invented invented.maintainer (invented.maintainer)"));
        mockMvc.perform(put(TIER_OF_44).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\": null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coreTier").doesNotExist());
        verify(tiers).setTier(44, null);
    }

    @Test
    @DisplayName("a tier that is not A, B or C is CORE_TIER_UNKNOWN 400 with the known; a code not on record TABLE_CODE_UNKNOWN 404")
    void theRefusalsAreAnswered() throws Exception {
        when(tiers.setTier(44, "D")).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.CORE_TIER_UNKNOWN,
                "A table code stands in Core A, B or C, or in none. Nothing was changed.",
                MonthEndRefusal.details("tier", "D", "known", "A,B,C")));
        when(tiers.setTier(999, "A")).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.TABLE_CODE_UNKNOWN,
                "No table code has that number here.", Map.of("code", "999")));

        mockMvc.perform(put(TIER_OF_44).with(SignInTestUsers.signedIn(MAINTAINER)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tier\": \"D\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CORE_TIER_UNKNOWN"))
                .andExpect(jsonPath("$.details.known").value("A,B,C"));
        mockMvc.perform(put("/api/reference/table-codes/999/core-tier").with(SignInTestUsers.signedIn(MAINTAINER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tier\": \"A\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TABLE_CODE_UNKNOWN"))
                .andExpect(jsonPath("$.details.code").value("999"));
    }

    @Test
    @DisplayName("🔒 a code that is not a number is 400 REQUEST_MALFORMED naming it -- and nothing is set")
    void aCodeThatIsNotANumber() throws Exception {
        mockMvc.perform(put("/api/reference/table-codes/PLANTED/core-tier").with(SignInTestUsers.signedIn(MAINTAINER))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tier\": \"A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("code"));
        verifyNoInteractions(tiers);
    }
}

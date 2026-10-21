package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.SecurityConfig;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndBulkEmailDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveriesPageDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndDeliveryDto;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndRegionPackDto;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.services.MonthEndRegionPacks;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The regions' packs, through their routes (October build), under the real security configuration: {@code 201} where
 * an ask made the pack and {@code 200} for the one made already; a file written to the response with its name, size,
 * hash and no-store; an e-mail refused where mail is off; MONTH_END_SEND enforced; a pack's number that is a word is
 * REQUEST_MALFORMED.
 */
@WebMvcTest(MonthEndRegionPackController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class})
@DisplayName("MonthEndRegionPackController")
class MonthEndRegionPackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MonthEndRegionPacks packs;

    static final MonthEndRegionPackDto PACK = new MonthEndRegionPackDto(41, "2026-09", 1, "JHB",
            "Johannesburg (first set)", "JHB", List.of("A", "F", "K", "X"), "Regional Secretary", "2026-10-01T05:00:00Z",
            "system", new MonthEndRegionPackDto.Counts(34, 12, 5),
            List.of(new MonthEndRegionPackDto.PackFile("PDF", "sasbo-month-end-2026-09-JHB.pdf", 4, "ab"),
                    new MonthEndRegionPackDto.PackFile("XLSX", "sasbo-month-end-2026-09-JHB.xlsx", 4, "cd")));

    static final MonthEndDeliveryDto EMAILED = new MonthEndDeliveryDto(90, "2026-10-01T05:02:00Z", "system",
            "REGION_PACK", "JHB", "Johannesburg (first set)", "EMAIL", List.of("PDF"),
            "regional.secretary.jhb@example.org", "SENT", null, "sasbo-month-end-2026-09-JHB.pdf", 41L, null);

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("a pack made by this ask is 201; the one made already is 200; asked again on purpose is passed on")
    void produce() throws Exception {
        when(packs.produce("2026-09", "JHB", false)).thenReturn(new MonthEndRegionPacks.Produced(PACK, true));
        mockMvc.perform(post("/api/month-end/2026-09/region-packs/JHB"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.counts.joined").value(34))
                .andExpect(jsonPath("$.files[1].format").value("XLSX"));

        when(packs.produce("2026-09", "JHB", true)).thenReturn(new MonthEndRegionPacks.Produced(PACK, false));
        mockMvc.perform(post("/api/month-end/2026-09/region-packs/JHB").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"again\": true}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("a pack's file is written to the response: its type, a name to save it by, its size and hash, and never cached")
    void download() throws Exception {
        when(packs.download(41L, "pdf")).thenReturn(new MonthEndRegionPacks.Download("sasbo-month-end-2026-09-JHB.pdf",
                "application/pdf", 4, "ab", out -> {
                    try {
                        out.write("%PDF".getBytes(StandardCharsets.US_ASCII));
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }));

        mockMvc.perform(get("/api/month-end/region-packs/41/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("sasbo-month-end-2026-09-JHB.pdf")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-File-Sha256", "ab"))
                .andExpect(content().bytes("%PDF".getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("an e-mail answers its delivery; mail off is MAIL_NOT_CONFIGURED 409; the month's sending a result a recipient")
    void email() throws Exception {
        when(packs.email(eq(41L), anyList())).thenReturn(EMAILED);
        mockMvc.perform(post("/api/month-end/region-packs/41/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formats\": [\"PDF\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SENT"))
                .andExpect(jsonPath("$.address").value("regional.secretary.jhb@example.org"));

        when(packs.email(eq(42L), anyList())).thenThrow(new MonthEndRefusal(MonthEndRefusalCode.MAIL_NOT_CONFIGURED,
                "This server has no mail set up, so nothing was sent.", Map.of("set", "JHB")));
        mockMvc.perform(post("/api/month-end/region-packs/42/email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formats\": [\"PDF\", \"XLSX\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MAIL_NOT_CONFIGURED"));

        when(packs.emailAll(eq("2026-09"), eq(null), anyList())).thenReturn(new MonthEndBulkEmailDto("2026-09",
                List.of(new MonthEndBulkEmailDto.Result("JHB", "SENT", null, "Sent.", EMAILED))));
        mockMvc.perform(post("/api/month-end/2026-09/region-packs/send").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"formats\": [\"PDF\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].setCode").value("JHB"));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("the month's record of what was sent, a page at a time")
    void deliveries() throws Exception {
        when(packs.deliveries("2026-09", 0, 50)).thenReturn(new MonthEndDeliveriesPageDto("2026-09", 0, 50, 1,
                List.of(EMAILED)));
        mockMvc.perform(get("/api/month-end/2026-09/deliveries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].channel").value("EMAIL"));
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_SEND")
    @DisplayName("ME-11: a pack's number that is a word is REQUEST_MALFORMED naming the field")
    void aWordForANumber() throws Exception {
        mockMvc.perform(get("/api/month-end/region-packs/PLANTEDWORD/pdf"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
        verifyNoInteractions(packs);
    }

    @Test
    @WithMockUser(authorities = "MONTH_END_VIEW")
    @DisplayName("🔒 MONTH_END_VIEW alone is refused 403: reading the figures is not sending the packs")
    void withoutThePermission() throws Exception {
        mockMvc.perform(get("/api/month-end/2026-09/region-packs")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/month-end/2026-09/region-packs/JHB")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/month-end/region-packs/41/pdf")).andExpect(status().isForbidden());
        verifyNoInteractions(packs);
    }

    @Test
    @WithAnonymousUser
    @DisplayName("🔒 nobody signed in is refused -- not answered, and not a 500")
    void anonymous() throws Exception {
        mockMvc.perform(get("/api/month-end/2026-09/region-packs")).andExpect(status().is4xxClientError());
        verifyNoInteractions(packs);
    }
}

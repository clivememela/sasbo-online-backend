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
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Approving a run is refused to EVERY caller -- under the application's real security
 * configuration, with the filter chain on.
 *
 * <h2>Why this cannot live in {@code ACBControllerTest}</h2>
 * That slice runs with the filters off, so any claim it made about who may call would prove
 * nothing; and a slice without the real {@code SecurityConfig} asserts Spring Security's defaults,
 * which DENY anonymous callers -- so a 403 there could be the framework's, not ACB's. Here the real
 * configuration is loaded, and it permits {@code /api/**} to anyone (register B1). So a 403 on
 * approve can only be ACB refusing -- which the must-pass twin proves by reading the runs list
 * through the same chain and getting a 200.
 *
 * <h2>What it protects</h2>
 * Approving a run authorises the union to debit thousands of members. Until the API knows who is
 * calling, no one may do it -- not an administrator, not a script, not someone who found the URL.
 */
@WebMvcTest(ACBController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, JacksonConfig.class})
@DisplayName("ACBController, with the application's real security configuration")
class ACBControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AcbRunService runs;

    @MockitoBean
    private AcbReturnsService returns;

    @MockitoBean
    private AcbCalendarService calendar;

    @MockitoBean
    private AcbOpeningPositionService openingPosition;

    @MockitoBean
    private AcbRateService rates;

    @Test
    @DisplayName("approving a run is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void approvalIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.runId").value("41"));

        // Refused BEFORE anything is looked up: the answer says nothing about which runs exist.
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("03.5 terminating a membership is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void terminationIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/termination-report/7/terminate"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.memberId").value("7"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("03.5 suspending an unpaid's member is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void suspensionIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/lines/9/suspend-member"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.runId").value("41"))
                .andExpect(jsonPath("$.details.lineId").value("9"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("the bureau file is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void theFileIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(get("/api/acb/runs/41/file"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.runId").value("41"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("recording a submission is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void submissionIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/submitted")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 3}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("🔒 D13: sending a held letter is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void sendingALetterIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/letters/9/send"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("🔒 D13, Q43: adding a rate is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void addingARateIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"effectiveFrom\": \"2027-01-01\", \"amount\": 185.00, \"source\": \"AGM\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("🔒 SD3-B: the opening-position upload is refused to every caller, with IDENTITY_REQUIRED, and nothing is read")
    void theOpeningPositionUploadIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/opening-position")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {'P', 'K', 3, 4}))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("🔒 recording a declined file is refused to every caller, with IDENTITY_REQUIRED, and nothing is read (D13, D29)")
    void aDeclineIsRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/declined")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 3, \"reason\": \"WRONG_DEBIT_DATE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"))
                .andExpect(jsonPath("$.details.runId").value("41"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("🔒 the holiday calendar's writes are refused to every caller, with IDENTITY_REQUIRED, and nothing is read (D13, D30)")
    void calendarWritesAreRefusedToEveryone() throws Exception {
        this.mockMvc.perform(post("/api/acb/holidays")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\": \"2026-10-28\", \"name\": \"Declared\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        this.mockMvc.perform(delete("/api/acb/holidays/2026-12-25"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        this.mockMvc.perform(post("/api/acb/calendar/known-until")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"knownUntil\": \"2028-12-31\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("IDENTITY_REQUIRED"));
        verifyNoInteractions(this.runs, this.returns, this.calendar, this.openingPosition, this.rates);
    }

    @Test
    @DisplayName("the must-pass twin: the calendar READS through the same chain -- public holidays are public")
    void theCalendarReadsOpenly() throws Exception {
        when(this.calendar.calendar(null)).thenReturn(new AcbCalendarDto("za-statutory", "2027-12-31", false, 2026, List.of()));
        this.mockMvc.perform(get("/api/acb/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("za-statutory"));
    }

    @Test
    @DisplayName("the must-pass twin: the same chain lets the runs list through -- so the 403 above is ACB's")
    void theChainPermitsTheApiSoTheRefusalIsAcbs() throws Exception {
        when(this.runs.list(0, 50)).thenReturn(new AcbPage<>(List.of(), 0, 50, 0, 0));
        this.mockMvc.perform(get("/api/acb/runs"))
                .andExpect(status().isOk());
    }
}

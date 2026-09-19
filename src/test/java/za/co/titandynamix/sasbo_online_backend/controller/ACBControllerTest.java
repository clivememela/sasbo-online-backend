package za.co.titandynamix.sasbo_online_backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/acb/runs}: transport, binding, status, and the error envelope with its refusal code.
 *
 * <h2>What replaced {@code exposesNothing}</h2>
 * This class used to assert that nothing under {@code /api/acb} succeeded -- a controller with no
 * endpoints, kept honest by a test that would redden the day one arrived. It reddened, and these are
 * the real tests it asked for.
 *
 * <h2>What is NOT here, and where it is</h2>
 * Approval refuses every caller until the API authenticates. That is a claim about who may call,
 * and this slice runs with the filter chain OFF -- so it is asserted in
 * {@code ACBControllerSecurityTest}, which imports the application's real {@code SecurityConfig}.
 * The rules behind each refusal are the service's, and are proved in its own tests; here the
 * service is mocked and only the translation to HTTP is under test.
 *
 * <h2>The not-found half, kept from 00.7</h2>
 * {@code src/test/resources/application.yaml} shadows the production file and carries neither MVC
 * key that decides how an unknown URL is answered, so {@code @TestPropertySource} pins them -- see
 * the nested class for the other configuration, and register row C12 for the mechanism.
 */
@WebMvcTest(ACBController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
// Copied from src/main/resources/application.yaml, because the test yaml shadows it and drops both.
@TestPropertySource(properties = {
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
@DisplayName("ACBController")
class ACBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AcbRunService runs;

    private static final String SYNTHETIC_ACCOUNT = "6200999911";

    static AcbRunDto aRun(AcbRunStatus status) {
        return new AcbRunDto(41L, "2026-10", status, 0, "2026-09-18", null, null, null, null,
                0, new BigDecimal("0.00"), true, null, null, "weekends-only", false, "system", null,
                "2026-09-18T10:00+02:00", "2026-09-18T10:00+02:00", null,
                List.of(new AcbExclusionSummaryDto(AcbExclusionReason.NOT_DEBIT_ORDER, false, "Payroll Deduction", 48)));
    }

    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("POST /runs answers 202 with the run in PREPARING -- never an empty body")
    void preparingAnswers202WithABody() throws Exception {
        when(runs.prepare("2026-10", "system")).thenReturn(aRun(AcbRunStatus.PREPARING));

        this.mockMvc.perform(post("/api/acb/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrepareRunRequest("2026-10"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(41))
                .andExpect(jsonPath("$.status").value("PREPARING"))
                .andExpect(jsonPath("$.actionMonth").value("2026-10"));

        verify(runs).prepare("2026-10", "system");
    }

    @Test
    @DisplayName("POST /runs without a month is a 400 naming the field")
    void aMissingMonthIsNamed() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.actionMonth").exists());
    }

    @Test
    @DisplayName("preparing a month that has a live run is a 409 carrying that run's state and version")
    void aSecondPrepareIsAConflictThatSaysWhy() throws Exception {
        when(runs.prepare("2026-10", "system")).thenThrow(new AcbRefusal(AcbRefusalCode.RUN_ALREADY_EXISTS,
                "The run for 2026-10 already exists, and it is PREPARED.",
                Map.of("runId", "9", "status", "PREPARED", "version", "3")));

        this.mockMvc.perform(post("/api/acb/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrepareRunRequest("2026-10"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RUN_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.details.status").value("PREPARED"))
                .andExpect(jsonPath("$.details.version").value("3"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.path").value("/api/acb/runs"));
    }

    @Test
    @DisplayName("each refusal answers its own status, with its code in the envelope")
    void refusalsCarryTheirStatusAndCode() throws Exception {
        when(runs.get(99L)).thenThrow(new AcbRefusal(AcbRefusalCode.RUN_NOT_FOUND, "There is no run 99."));
        this.mockMvc.perform(get("/api/acb/runs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
                .andExpect(jsonPath("$.details").doesNotExist());

        when(runs.prepare("2026-13", "system")).thenThrow(
                new AcbRefusal(AcbRefusalCode.ACTION_MONTH_INVALID, "The action month must be written YYYY-MM."));
        this.mockMvc.perform(post("/api/acb/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PrepareRunRequest("2026-13"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTION_MONTH_INVALID"));
    }

    @Test
    @DisplayName("an unexpected failure is a 500 that carries no class name, SQL or bound value")
    void aFailureCarriesNoInternals() throws Exception {
        when(runs.get(anyLong())).thenThrow(new IllegalStateException(
                "ERROR: SELECT * FROM acb_run_lines WHERE account_number = '" + SYNTHETIC_ACCOUNT + "'"));

        MvcResult result = this.mockMvc.perform(get("/api/acb/runs/7"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain(SYNTHETIC_ACCOUNT).doesNotContain("SELECT")
                .doesNotContain("IllegalStateException").doesNotContain("acb_run_lines");
    }

    @Test
    @DisplayName("GET /runs is a page: content, page, size and total")
    void theRunsListIsAPage() throws Exception {
        AcbRunSummaryDto summary = new AcbRunSummaryDto(41L, "2026-10", AcbRunStatus.PREPARED, 1,
                "2026-09-18", 3, new BigDecimal("522.00"), true, false, null);
        when(runs.list(0, 50)).thenReturn(new AcbPage<>(List.of(summary), 0, 50, 1, 1));

        this.mockMvc.perform(get("/api/acb/runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actionMonth").value("2026-10"))
                .andExpect(jsonPath("$.content[0].totalAmount").value(522.00))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(50));
    }

    @Test
    @DisplayName("the page asked for reaches the service as asked; the service caps it")
    void paging() throws Exception {
        when(runs.list(2, 500)).thenReturn(new AcbPage<>(List.of(), 2, 200, 0, 0));
        this.mockMvc.perform(get("/api/acb/runs").param("page", "2").param("size", "500"))
                .andExpect(status().isOk());
        verify(runs).list(2, 500);
    }

    @Test
    @DisplayName("a run's dates leave as ISO-8601 strings, never as arrays of numbers")
    void datesAreIsoStrings() throws Exception {
        when(runs.get(41L)).thenReturn(aRun(AcbRunStatus.PREPARED));
        this.mockMvc.perform(get("/api/acb/runs/41"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupDate").value("2026-09-18"))
                .andExpect(jsonPath("$.startedAt").value("2026-09-18T10:00+02:00"))
                .andExpect(jsonPath("$.exclusions[0].reason").value("NOT_DEBIT_ORDER"))
                .andExpect(jsonPath("$.exclusions[0].memberCount").value(48));
    }

    @Test
    @DisplayName("a line shows the account's last four digits, and the full number is nowhere in the JSON")
    void linesAreMasked() throws Exception {
        AcbRunLineDto line = new AcbRunLineDto(3L, 7, "M-200001", "Botha", "AB", "Alpha Bank", "632005",
                "9911", null, 25, "2026-10-26", new BigDecimal("174.00"), AcbLineOutcome.PENDING, null, null,
                null, null, null);
        when(runs.lines(41L, 0, 50)).thenReturn(new AcbPage<>(List.of(line), 0, 50, 1, 1));

        MvcResult result = this.mockMvc.perform(get("/api/acb/runs/41/lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountLastFour").value("9911"))
                .andExpect(jsonPath("$.content[0].actionDate").value("2026-10-26"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("accountNumber");
    }

    @Test
    @DisplayName("the exclusions are a page of members with the reason, and the detail -- never an account")
    void exclusionsArePaged() throws Exception {
        AcbExclusionDto excluded = new AcbExclusionDto(12, "M-200009", "Khumalo", "T",
                AcbExclusionReason.ACCOUNT_SHAPE_INVALID, "12 digits -- the file carries at most 11");
        when(runs.exclusions(41L, 0, 50)).thenReturn(new AcbPage<>(List.of(excluded), 0, 50, 1, 1));

        this.mockMvc.perform(get("/api/acb/runs/41/exclusions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reason").value("ACCOUNT_SHAPE_INVALID"))
                .andExpect(jsonPath("$.content[0].memberId").value(12));
    }

    @Test
    @DisplayName("cancelling needs the version the screen saw, and passes it with the reason")
    void cancelling() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.version").exists());

        when(runs.cancel(41L, 2, "system", "wrong month")).thenReturn(aRun(AcbRunStatus.CANCELLED));
        this.mockMvc.perform(post("/api/acb/runs/41/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RunTransitionRequest(2, "wrong month"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(runs).cancel(41L, 2, "system", "wrong month");
    }

    // ------------------------------------------------------------------------------------------
    // The not-found path -- kept from 00.7, unchanged in what it asserts
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("a path no controller handles is a 404, not a 500")
    void anUnhandledPathIsNotFound() throws Exception {
        // /api/acb itself has no handler -- the runs live under /api/acb/runs.
        MvcResult result = this.mockMvc.perform(get("/api/acb"))
                .andExpect(status().isNotFound())
                .andReturn();

        // The status alone cannot tell the two configurations apart, so the exception is named:
        // without this, dropping @TestPropertySource would leave this test passing while silently
        // testing the OTHER arm, which is the nested class's job.
        assertThat(result.getResolvedException())
                .as("production's configuration raises NoHandlerFoundException")
                .isInstanceOf(NoHandlerFoundException.class);
    }

    /**
     * The same URL under Boot's default {@code spring.web.resources.add-mappings}, which is the
     * configuration {@code NoResourceFoundException} exists for. Deleting that type from the handler
     * was watched turning this class red, and this class only.
     */
    @Nested
    @TestPropertySource(properties = "spring.web.resources.add-mappings=true")
    @DisplayName("with the static resource handler mapped, as Boot defaults it")
    class WithResourceMappingsOn {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("is still a 404, because the other not-found exception is handled too")
        void stillNotFound() throws Exception {
            MvcResult result = this.mockMvc.perform(get("/api/acb"))
                    .andExpect(status().isNotFound())
                    .andReturn();

            assertThat(result.getResolvedException())
                    .as("with resources mapped it is the resource handler that refuses the path")
                    .isInstanceOf(NoResourceFoundException.class);
        }
    }
}

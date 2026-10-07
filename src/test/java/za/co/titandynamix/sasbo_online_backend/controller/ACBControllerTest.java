package za.co.titandynamix.sasbo_online_backend.controller;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterDocument;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBankCodeDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBanksDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCodesDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDaysDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountsResultDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeAddRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.CodeChangeRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.SuspendMemberRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldOverDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.PrepareRunRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RunTransitionRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberSearchRequest;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbOpeningPositionService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRateService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
@Import({GlobalExceptionHandler.class, JacksonConfig.class, ACBControllerTest.Meters.class})
@AutoConfigureMockMvc(addFilters = false)
// Copied from src/main/resources/application.yaml, because the test yaml shadows it and drops both.
@TestPropertySource(properties = {
        "spring.mvc.throw-exception-if-no-handler-found=true",
        "spring.web.resources.add-mappings=false"
})
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("ACBController")
class ACBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.AcbCodeService codes;

    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService unpaidCounts;

    @MockitoBean
    private za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService debitDays;

    @MockitoBean
    private LetterDocument letters;

    @Autowired
    private MeterRegistry meters;

    /** A registry of this slice's own, so a refusal's count is read here and nowhere else (EH2-C). */
    @TestConfiguration
    static class Meters {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final String SYNTHETIC_ACCOUNT = "6200999911";

    static AcbRunDto aRun(AcbRunStatus status) {
        return new AcbRunDto(41L, "2026-10", status, 0, "2026-09-18", null, null, null, null, null, false,
                0, new BigDecimal("0.00"), true, true, null, null, "za-statutory", true, "system", null,
                "2026-09-18T10:00+02:00", "2026-09-18T10:00+02:00", null, null, null, null,
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

    // ------------------------------------------------------------------------------------------
    // The holiday calendar (D30), and requests that cannot be read (EH2-A, R1-21)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("GET /holidays reads the calendar and one year of it -- the year asked, as a number")
    void theCalendarReads() throws Exception {
        when(calendar.calendar(2026)).thenReturn(new AcbCalendarDto("za-statutory", "2027-12-31", false, 2026,
                List.of(new AcbHolidayDto("2026-09-24", "Heritage Day", "STATUTORY"))));
        this.mockMvc.perform(get("/api/acb/holidays").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownUntil").value("2027-12-31"))
                .andExpect(jsonPath("$.endingSoon").value(false))
                .andExpect(jsonPath("$.holidays[0].name").value("Heritage Day"));
    }

    @Test
    @DisplayName("🧪 R1-21: a year that is not a number is REQUEST_MALFORMED, never a 500 -- and nothing sent comes back")
    void aYearThatIsNotANumber() throws Exception {
        String sent = this.mockMvc.perform(get("/api/acb/holidays").param("year", "twenty-twenty-six"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("year"))
                .andReturn().getResponse().getContentAsString();
        assertThat(sent).doesNotContain("twenty-twenty-six");
        verifyNoInteractions(this.calendar);
    }

    @Test
    @DisplayName("🧪 R1-21: malformed JSON is REQUEST_MALFORMED, and the account number it carried does not come back")
    void malformedJson() throws Exception {
        String answer = this.mockMvc.perform(post("/api/acb/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionMonth\": \"" + SYNTHETIC_ACCOUNT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andReturn().getResponse().getContentAsString();
        assertThat(answer).doesNotContain(SYNTHETIC_ACCOUNT);
        verifyNoInteractions(this.runs);
    }

    @Test
    @DisplayName("🧪 R1-21: a string where the version wants a number is REQUEST_MALFORMED, not a 500")
    void aVersionThatIsNotANumber() throws Exception {
        String answer = this.mockMvc.perform(post("/api/acb/runs/41/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": \"three\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("version"))
                .andReturn().getResponse().getContentAsString();
        assertThat(answer).doesNotContain("three");
        verifyNoInteractions(this.runs);
    }

    @Test
    @DisplayName("🔒 EH2-C: a refusal writes ONE INFO line and ONE count -- the code and the route, never its details")
    void aRefusalIsSaidOnce() throws Exception {
        when(runs.get(41L)).thenThrow(new AcbRefusal(
                AcbRefusalCode.LINE_NOT_FOUND, "no such line", Map.of("account", SYNTHETIC_ACCOUNT)));
        double before = meters.counter("acb.refusals", "code", "LINE_NOT_FOUND").count();
        try (LogCapture log = LogCapture.on(AcbExceptionHandler.class)) {
            this.mockMvc.perform(get("/api/acb/runs/41"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.details.account").value(SYNTHETIC_ACCOUNT));
            // Sibling classes log through the same logger concurrently, so this test's line is found by
            // what only it can say: its route and its code.
            List<String> mine = log.renderedAt(Level.INFO).lines()
                    .filter(line -> line.contains("GET /api/acb/runs/41: LINE_NOT_FOUND")).toList();
            assertThat(mine).hasSize(1);
            assertThat(log.rendered()).doesNotContain(SYNTHETIC_ACCOUNT);
        }
        assertThat(meters.counter("acb.refusals", "code", "LINE_NOT_FOUND").count()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("GET /runs is a page: content, page, size and total")
    void theRunsListIsAPage() throws Exception {
        AcbRunSummaryDto summary = new AcbRunSummaryDto(41L, "2026-10", AcbRunStatus.PREPARED, 1,
                "2026-09-18", 3, new BigDecimal("522.00"), true, true, true, null, null);
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
                null, null, null, null, null, null);
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

    @Test
    @DisplayName("taking the returns in and closing the month each need the version the screen saw")
    void returnsInAndClose() throws Exception {
        for (String route : List.of("returned", "close")) {
            this.mockMvc.perform(post("/api/acb/runs/41/" + route)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.version").exists());
        }
        verifyNoInteractions(runs);

        when(runs.recordReturns(41L, 3, "system", null)).thenReturn(aRun(AcbRunStatus.RETURNED));
        this.mockMvc.perform(post("/api/acb/runs/41/returned")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RunTransitionRequest(3, null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RETURNED"));

        when(runs.close(41L, 4, "system", null)).thenThrow(new AcbRefusal(AcbRefusalCode.UNPAIDS_UNDECIDED,
                "1 unpaid(s) in this run have no decision yet.", Map.of("undecided", "1")));
        this.mockMvc.perform(post("/api/acb/runs/41/close")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RunTransitionRequest(4, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNPAIDS_UNDECIDED"))
                .andExpect(jsonPath("$.details.undecided").value("1"));
    }

    // ------------------------------------------------------------------------------------------
    // The returns loop
    // ------------------------------------------------------------------------------------------

    static AcbRunLineDto anUnpaidLine(UnpaidDecision decision) {
        return new AcbRunLineDto(3L, 7, "M-200001", "Botha", "AB", "Alpha Bank", "632005", "9911", 1, 25,
                "2026-10-26", new BigDecimal("174.00"), AcbLineOutcome.UNPAID, null, 1, "4",
                "PAYMENT_STOPPED", decision, decision == null ? UnpaidDecision.SUSPEND : null, null,
                LetterStatus.QUEUED);
    }

    @Test
    @DisplayName("recording an unpaid passes the code, the date and the decision, and answers the line")
    void recordingAnUnpaid() throws Exception {
        RecordUnpaidRequest request = new RecordUnpaidRequest("4", "2026-10-28", UnpaidDecision.RE_PRESENT);
        when(returns.recordUnpaid(41L, 3L, request, "system")).thenReturn(anUnpaidLine(UnpaidDecision.RE_PRESENT));

        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/unpaid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNPAID"))
                .andExpect(jsonPath("$.unpaidCode").value("4"))
                .andExpect(jsonPath("$.decision").value("RE_PRESENT"));
        verify(returns).recordUnpaid(41L, 3L, request, "system");
    }

    @Test
    @DisplayName("an unpaid line says where its letter is")
    void anUnpaidSaysWhereItsLetterIs() throws Exception {
        when(returns.unpaids(41L, null, 0, 50)).thenReturn(new AcbPage<>(List.of(anUnpaidLine(null)), 0, 50, 1, 1));
        this.mockMvc.perform(get("/api/acb/runs/41/unpaids"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].letterStatus").value("QUEUED"))
                .andExpect(jsonPath("$.content[0].suggestedDecision").value("SUSPEND"));
    }

    @Test
    @DisplayName("🔒 the member search is a POST whose body carries the query and a page size, and answers masked accounts")
    void theMemberSearch() throws Exception {
        MemberSearchRequest request = new MemberSearchRequest("bot", null, "0001010000181", 0, 25);
        when(returns.searchMembers(request)).thenReturn(new AcbPage<>(List.of(new AcbMemberDto(7, "M-200001", "Mr",
                "AB", "Botha", "Alpha Bank", "9911", 25, "Active")), 0, 25, 1, 1));

        MvcResult result = this.mockMvc.perform(post("/api/acb/members/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].surname").value("Botha"))
                .andExpect(jsonPath("$.content[0].accountLastFour").value("9911"))
                .andExpect(jsonPath("$.content[0].identityNumber").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("0001010000181");
        verify(returns).searchMembers(request);
    }

    @Test
    @DisplayName("the member's permission for a further re-debit passes its note, and answers the line (D32)")
    void recordingAPermission() throws Exception {
        when(returns.permit(41L, 3L, "Agreed by the Synthetic Member", "system"))
                .thenReturn(anUnpaidLine(UnpaidDecision.RE_PRESENT));
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/permission")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\": \"Agreed by the Synthetic Member\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("RE_PRESENT"));
        verify(returns).permit(41L, 3L, "Agreed by the Synthetic Member", "system");
    }

    @Test
    @DisplayName("a run's held-over re-debits are a page (D32)")
    void theHeldOverList() throws Exception {
        when(returns.heldOver(41L, 0, 50)).thenReturn(new AcbPage<>(List.of(new AcbHeldOverDto(9L, 30L, 7, "M-200001",
                "Botha", "2026-08", new BigDecimal("174.00"), false)), 0, 50, 1, 1));
        this.mockMvc.perform(get("/api/acb/runs/41/held-over"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].month").value("2026-08"))
                .andExpect(jsonPath("$.content[0].permitted").value(false))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("an unpaid without a code or a date is a 400 naming both fields -- the service is never asked")
    void anUnpaidNeedsACodeAndADate() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/unpaid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.code").exists())
                .andExpect(jsonPath("$.validationErrors.notedOn").exists());
        verifyNoInteractions(returns);
    }

    @Test
    @DisplayName("an unpaid already recorded differently is a 409 carrying UNPAID_ALREADY_RECORDED and what is there")
    void aSecondDifferentUnpaidIsRefused() throws Exception {
        RecordUnpaidRequest request = new RecordUnpaidRequest("2", "2026-10-28", null);
        when(returns.recordUnpaid(41L, 3L, request, "system")).thenThrow(new AcbRefusal(
                AcbRefusalCode.UNPAID_ALREADY_RECORDED, "This debit is already recorded as unpaid with code 4.",
                Map.of("lineId", "3", "unpaidCode", "4")));

        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/unpaid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNPAID_ALREADY_RECORDED"))
                .andExpect(jsonPath("$.details.unpaidCode").value("4"));
    }

    @Test
    @DisplayName("a decision needs a decision; given one, it is passed through")
    void deciding() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.decision").exists());

        when(returns.decide(41L, 3L, UnpaidDecision.SUSPEND, "system")).thenReturn(anUnpaidLine(UnpaidDecision.SUSPEND));
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\": \"SUSPEND\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("SUSPEND"));
    }

    @Test
    @DisplayName("a run's unpaids are a page, filtered by reason when one is given")
    void unpaidsArePaged() throws Exception {
        when(returns.unpaids(41L, UnpaidReason.PAYMENT_STOPPED, 0, 50))
                .thenReturn(new AcbPage<>(List.of(anUnpaidLine(null)), 0, 50, 1, 1));
        this.mockMvc.perform(get("/api/acb/runs/41/unpaids").param("reason", "PAYMENT_STOPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].unpaidReason").value("PAYMENT_STOPPED"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(returns).unpaids(41L, UnpaidReason.PAYMENT_STOPPED, 0, 50);
    }

    @Test
    @DisplayName("the returns summary and the termination report are read-only answers with bodies")
    void theReports() throws Exception {
        when(returns.summary(41L)).thenReturn(new AcbReturnsSummaryDto(41L, Map.of("UNPAID", 1, "PENDING", 29),
                Map.of("PAYMENT_STOPPED", 1), 0, 0, Map.of("RENDERED", 1)));
        this.mockMvc.perform(get("/api/acb/runs/41/returns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linesByOutcome.UNPAID").value(1))
                .andExpect(jsonPath("$.lettersByStatus.RENDERED").value(1));

        when(returns.terminationReport(0, 50)).thenReturn(new AcbTerminationReportDto("2026-10", "stated", new AcbPage<>(
                List.of(new AcbTerminationReportDto.Candidate(7, "M-200001", "Botha", 3, true, 3,
                        List.of("2026-10", "2026-11", "2026-11"), 0, "Active")), 0, 50, 1, 1)));
        this.mockMvc.perform(get("/api/acb/termination-report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.countingFrom").value("2026-10"))
                .andExpect(jsonPath("$.members.content[0].unpaidCount").value(3))
                .andExpect(jsonPath("$.members.content[0].status").value("Active"))
                .andExpect(jsonPath("$.members.content[0].terminate").value(true))
                .andExpect(jsonPath("$.members.content[0].unpaidMonths[2]").value("2026-11"))
                .andExpect(jsonPath("$.members.totalElements").value(1))
                // Q27: one reading, so the report no longer carries the other one's columns.
                .andExpect(jsonPath("$.members.content[0].retriesOnly").doesNotExist())
                .andExpect(jsonPath("$.members.content[0].everyUnpaidCounts").doesNotExist());
    }

    // ------------------------------------------------------------------------------------------
    // The October routes (contracts/dod-api.md v2): each answers through its service, and a body that breaks its own
    // shape is refused before any service is asked. Who may call each one is ACBControllerSecurityTest's table.
    // ------------------------------------------------------------------------------------------

    private static AcbBankCodeDto aCode(String bankCode, int version) {
        return new AcbBankCodeDto(bankCode, UnpaidReason.PAYMENT_STOPPED, "Payment stopped", ReDebit.NO, "SHEET", 16,
                "Unpaid debit order", false, "As the sheet has it", "SHEET", "system", "2026-10-07T10:00+02:00", version);
    }

    private static AcbMemberStatusDto aMemberStatus(String status) {
        return new AcbMemberStatusDto(7, "M-200001", status, "Payment stopped", 16, null, null, null, null, null);
    }

    @Test
    @DisplayName("DOD-06: a run's debit days, each with how far its returns report has come back")
    void aRunsDebitDays() throws Exception {
        when(returns.debitDays(41L)).thenReturn(new AcbDebitDaysDto(41L, List.of(
                new AcbDebitDaysDto.Day("2026-10-20", 12, 2, 1), new AcbDebitDaysDto.Day("2026-10-25", 30, 0, 0))));

        this.mockMvc.perform(get("/api/acb/runs/41/debit-days"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId").value(41))
                .andExpect(jsonPath("$.days[0].actionDate").value("2026-10-20"))
                .andExpect(jsonPath("$.days[0].unpaid").value(2))
                .andExpect(jsonPath("$.days[1].batches").value(0));
    }

    @Test
    @DisplayName("a run id that is not a number is REQUEST_MALFORMED naming it -- the debit days are never read")
    void aDebitDaysRunIdMustBeANumber() throws Exception {
        this.mockMvc.perform(get("/api/acb/runs/forty-one/debit-days"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("id"));
        verifyNoInteractions(returns);
    }

    @Test
    @DisplayName("DOD-07: a debit day's returns report goes in whole -- every entry passed through, the batch answered")
    void aReturnsReportGoesInWhole() throws Exception {
        ReturnsBatchRequest request = new ReturnsBatchRequest("2026-10-20", "2026-10-23", List.of(
                new ReturnsBatchRequest.Entry(3L, null, "4", UnpaidDecision.RE_PRESENT),
                new ReturnsBatchRequest.Entry(null, 17, "2", null)));
        when(returns.recordReturnsBatch(41L, request, "system")).thenReturn(new AcbReturnsBatchDto(5L, 41L,
                "2026-10-20", "2026-10-23", "TYPED", 2, 2, 0, 2, "system", "2026-10-23T10:00+02:00", List.of()));

        this.mockMvc.perform(post("/api/acb/runs/41/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.actionDate").value("2026-10-20"))
                .andExpect(jsonPath("$.recorded").value(2))
                .andExpect(jsonPath("$.lettersQueued").value(2));
        verify(returns).recordReturnsBatch(41L, request, "system");
    }

    @Test
    @DisplayName("a returns report without its debit day or entries, with an entry without a code, or over 1,000 "
            + "entries is a 400 naming each -- the service is never asked")
    void aReturnsReportIsShapedAndBounded() throws Exception {
        this.mockMvc.perform(post("/api/acb/runs/41/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.actionDate").exists())
                .andExpect(jsonPath("$.validationErrors.entries").exists());

        this.mockMvc.perform(post("/api/acb/runs/41/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actionDate\": \"2026-10-20\", \"entries\": [{\"lineId\": 3, \"code\": \" \"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors['entries[0].code']").exists());

        String tooMany = objectMapper.writeValueAsString(new ReturnsBatchRequest("2026-10-20", null,
                Collections.nCopies(1001, new ReturnsBatchRequest.Entry(3L, null, "4", null))));
        this.mockMvc.perform(post("/api/acb/runs/41/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.entries").exists());
        verifyNoInteractions(returns);
    }

    @Test
    @DisplayName("a returns report with problems is a 422 RETURNS_BATCH_INVALID with what the service named")
    void aReturnsReportWithProblemsIsRefused() throws Exception {
        ReturnsBatchRequest request = new ReturnsBatchRequest("2026-10-20", null,
                List.of(new ReturnsBatchRequest.Entry(null, 999, "4", null)));
        when(returns.recordReturnsBatch(41L, request, "system")).thenThrow(new AcbRefusal(
                AcbRefusalCode.RETURNS_BATCH_INVALID, "1 entry on this report has a problem, so nothing was recorded.",
                Map.of("problems", "1")));

        this.mockMvc.perform(post("/api/acb/runs/41/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("RETURNS_BATCH_INVALID"))
                .andExpect(jsonPath("$.details.problems").value("1"));
    }

    @Test
    @DisplayName("the returns reports typed in for a run are a page; a page that is not a number is malformed")
    void theReturnsReportsAreAPage() throws Exception {
        when(returns.returnsBatches(41L, 0, 50)).thenReturn(new AcbPage<>(List.of(new AcbReturnsBatchDto(5L, 41L,
                "2026-10-20", null, "TYPED", 1, 1, 0, 1, "system", "2026-10-23T10:00+02:00", List.of())), 0, 50, 1, 1));

        this.mockMvc.perform(get("/api/acb/runs/41/returns/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actionDate").value("2026-10-20"))
                .andExpect(jsonPath("$.totalElements").value(1));

        this.mockMvc.perform(get("/api/acb/runs/41/returns/batches").param("page", "first"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"));
        verify(returns).returnsBatches(41L, 0, 50);
        verifyNoMoreInteractions(returns);
    }

    @Test
    @DisplayName("DOD-28: counts captured by hand go to the service as one list; the counts on record are a page")
    void unpaidCountsCapturedByHand() throws Exception {
        UnpaidCountsRequest request = new UnpaidCountsRequest("Switch-over sheet",
                List.of(new UnpaidCountsRequest.Entry("M-200001", 2), new UnpaidCountsRequest.Entry("M-200002", 3)));
        when(unpaidCounts.capture(request, "system")).thenReturn(new AcbUnpaidCountsResultDto(2, 0, "2026-10-07", 1));

        this.mockMvc.perform(put("/api/acb/unpaid-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.captured").value(2))
                .andExpect(jsonPath("$.atThree").value(1));
        verify(unpaidCounts).capture(request, "system");

        when(unpaidCounts.list(0, 50)).thenReturn(new AcbPage<>(List.of(new AcbUnpaidCountDto(7, "M-200001",
                "Specimen", 2, "2026-10-07", "Switch-over sheet", "system", "2026-10-07T10:00+02:00")), 0, 50, 1, 1));
        this.mockMvc.perform(get("/api/acb/unpaid-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].membershipNumber").value("M-200001"))
                .andExpect(jsonPath("$.content[0].unpaidCount").value(2))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("a count list without its source or rows, or over 2,000 rows, is a 400 naming the field -- nothing "
            + "captured; a list with bad rows is a 422 UNPAID_COUNTS_INVALID")
    void aCountListIsShapedAndBounded() throws Exception {
        this.mockMvc.perform(put("/api/acb/unpaid-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.source").exists())
                .andExpect(jsonPath("$.validationErrors.entries").exists());

        String tooMany = objectMapper.writeValueAsString(new UnpaidCountsRequest("Switch-over sheet",
                Collections.nCopies(2001, new UnpaidCountsRequest.Entry("M-200001", 1))));
        this.mockMvc.perform(put("/api/acb/unpaid-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tooMany))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.entries").exists());
        verifyNoInteractions(unpaidCounts);

        UnpaidCountsRequest four = new UnpaidCountsRequest("Switch-over sheet",
                List.of(new UnpaidCountsRequest.Entry("M-200001", 4)));
        when(unpaidCounts.capture(four, "system")).thenThrow(new AcbRefusal(AcbRefusalCode.UNPAID_COUNTS_INVALID,
                "1 row on this list has a problem, so nothing was captured.", Map.of("problems", "1")));
        this.mockMvc.perform(put("/api/acb/unpaid-counts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(four)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPAID_COUNTS_INVALID"));
    }

    @Test
    @DisplayName("DOD-01: the codes both ways round; a change carries the version the screen saw; an addition is a 201")
    void theCodes() throws Exception {
        when(codes.codes()).thenReturn(new AcbCodesDto(List.of(aCode("4", 1)),
                List.of(new AcbCodesDto.SuspensionCode(16, "Unpaid debit order", List.of("4")))));
        this.mockMvc.perform(get("/api/acb/codes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankCodes[0].bankCode").value("4"))
                .andExpect(jsonPath("$.bankCodes[0].reDebit").value("NO"))
                .andExpect(jsonPath("$.suspensionCodes[0].bankCodes[0]").value("4"));

        CodeChangeRequest change = new CodeChangeRequest(1, ReDebit.YES, 16, "Payment stopped", false,
                "Changed after the returns review");
        when(codes.change("4", change, "system")).thenReturn(aCode("4", 2));
        this.mockMvc.perform(put("/api/acb/codes/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2));
        verify(codes).change("4", change, "system");

        CodeAddRequest addition = new CodeAddRequest("99", UnpaidReason.OTHER, "A code the bank added",
                ReDebit.UNKNOWN, null, "Seen on a returns report");
        when(codes.add(addition, "system")).thenReturn(aCode("99", 0));
        this.mockMvc.perform(post("/api/acb/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addition)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bankCode").value("99"));
        verify(codes).add(addition, "system");
    }

    @Test
    @DisplayName("a code change missing its version, rule, wording, flag or note -- or a bank code that is not one to "
            + "three digits -- is a 400 naming each; the service is never asked")
    void aCodeChangeNamesWhatIsMissing() throws Exception {
        this.mockMvc.perform(put("/api/acb/codes/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.version").exists())
                .andExpect(jsonPath("$.validationErrors.reDebit").exists())
                .andExpect(jsonPath("$.validationErrors.wording").exists())
                .andExpect(jsonPath("$.validationErrors.provisional").exists())
                .andExpect(jsonPath("$.validationErrors.note").exists());

        this.mockMvc.perform(post("/api/acb/codes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CodeAddRequest("1234", UnpaidReason.OTHER,
                                "Four digits", ReDebit.UNKNOWN, null, "A note"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.bankCode").exists());
        verifyNoInteractions(codes);
    }

    @Test
    @DisplayName("a code someone changed first is a 409 CODE_VERSION_CONFLICT carrying its version now")
    void aStaleCodeChangeIsRefused() throws Exception {
        CodeChangeRequest change = new CodeChangeRequest(1, ReDebit.YES, null, "Payment stopped", true, "A note");
        when(codes.change("4", change, "system")).thenThrow(new AcbRefusal(AcbRefusalCode.CODE_VERSION_CONFLICT,
                "Someone changed code 4 first. Open it again and make the change on what is there now.",
                Map.of("bankCode", "4", "version", "2")));

        this.mockMvc.perform(put("/api/acb/codes/4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(change)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CODE_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.details.version").value("2"));
    }

    @Test
    @DisplayName("DOD-17: whether a day the bank is closed moves its debits -- the choice passed on; none is a 400")
    void theBankClosureSetting() throws Exception {
        when(calendar.setting(false, "system")).thenReturn(
                new AcbCalendarDto("za-statutory", "2027-12-31", false, 2026, false, List.of()));
        this.mockMvc.perform(put("/api/acb/calendar/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bankClosuresCount\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankClosuresCount").value(false));
        verify(calendar).setting(false, "system");

        this.mockMvc.perform(put("/api/acb/calendar/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.bankClosuresCount").exists());
        verifyNoMoreInteractions(calendar);
    }

    @Test
    @DisplayName("DOD-19: the banks counted by day; a move previewed changes nothing, and made, carries the count "
            + "the preview showed")
    void aBanksDebitDayMove() throws Exception {
        when(debitDays.banks()).thenReturn(new AcbBanksDto(List.of(new AcbBanksDto.Bank(3, "Invented Bank", 25, 40,
                Map.of("20", 2, "25", 38)))));
        this.mockMvc.perform(get("/api/acb/banks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.banks[0].debitOrderMembers").value(40))
                .andExpect(jsonPath("$.banks[0].byDay['20']").value(2));

        when(debitDays.preview(3, 25)).thenReturn(new AcbDebitDayMoveDto(3, "Invented Bank", 25, 2, 38,
                Map.of("20", 2), List.of(), null));
        this.mockMvc.perform(get("/api/acb/banks/3/debit-day-move").param("day", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toMove").value(2))
                .andExpect(jsonPath("$.alreadyOnDay").value(38));
        verify(debitDays).preview(3, 25);

        DebitDayMoveRequest move = new DebitDayMoveRequest(25, 2);
        when(debitDays.move(3, move, "system")).thenReturn(new AcbDebitDayMoveDto(3, "Invented Bank", 25, 0, 40,
                Map.of(), List.of(), 2));
        this.mockMvc.perform(post("/api/acb/banks/3/debit-day-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(move)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.moved").value(2));
        verify(debitDays).move(3, move, "system");
    }

    @Test
    @DisplayName("a move without its day or expected count is a 400 naming both, and a preview without a day is "
            + "malformed -- nothing is asked; a move the members outran is a 409 MOVE_COUNT_CHANGED")
    void aMoveNeedsItsDayAndCount() throws Exception {
        this.mockMvc.perform(post("/api/acb/banks/3/debit-day-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.newDay").exists())
                .andExpect(jsonPath("$.validationErrors.expectedCount").exists());

        this.mockMvc.perform(get("/api/acb/banks/3/debit-day-move"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("day"));
        verifyNoInteractions(debitDays);

        DebitDayMoveRequest move = new DebitDayMoveRequest(25, 2);
        when(debitDays.move(3, move, "system")).thenThrow(new AcbRefusal(AcbRefusalCode.MOVE_COUNT_CHANGED,
                "The bank's members changed since the preview, so nothing was moved.",
                Map.of("bankId", "3", "expected", "2", "actual", "3")));
        this.mockMvc.perform(post("/api/acb/banks/3/debit-day-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(move)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOVE_COUNT_CHANGED"))
                .andExpect(jsonPath("$.details.expected").value("2"))
                .andExpect(jsonPath("$.details.actual").value("3"));
    }

    @Test
    @DisplayName("DOD-12, DOD-22, DOD-25, DOD-27: suspending, ending, reactivating and rejoining each answer the "
            + "member's status -- a body is optional, and passed through when sent")
    void aMembersStatusChanges() throws Exception {
        when(returns.suspendMember(41L, 3L, null, "system")).thenReturn(aMemberStatus("Suspended"));
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/suspend-member"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Suspended"))
                .andExpect(jsonPath("$.suspensionCode").value(16));
        verify(returns).suspendMember(41L, 3L, null, "system");

        SuspendMemberRequest another = new SuspendMemberRequest(17, "The bank's code names none yet");
        when(returns.suspendMember(41L, 3L, another, "system")).thenReturn(aMemberStatus("Suspended"));
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/suspend-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(another)))
                .andExpect(status().isOk());
        verify(returns).suspendMember(41L, 3L, another, "system");

        when(returns.terminate(7, "system")).thenReturn(aMemberStatus("Terminated"));
        this.mockMvc.perform(post("/api/acb/termination-report/7/terminate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Terminated"));

        when(returns.reactivate(7, null, "system")).thenReturn(aMemberStatus("Active"));
        this.mockMvc.perform(post("/api/acb/members/7/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"));

        MemberReturnRequest rejoining = new MemberReturnRequest("2026-11-01", null);
        when(returns.rejoin(7, rejoining, "system")).thenReturn(aMemberStatus("Active"));
        this.mockMvc.perform(post("/api/acb/members/7/rejoin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejoining)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipNumber").value("M-200001"));
        verify(returns).rejoin(7, rejoining, "system");
    }

    @Test
    @DisplayName("a note over 500 characters, or a member id that is not a number, is refused before anything "
            + "changes; rejoining a member whose membership has not ended is a 409 MEMBER_NOT_ENDED")
    void aMembersStatusChangeIsShaped() throws Exception {
        String note = "n".repeat(501);
        this.mockMvc.perform(post("/api/acb/runs/41/lines/3/suspend-member")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SuspendMemberRequest(16, note))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.note").exists());
        this.mockMvc.perform(post("/api/acb/members/7/reactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MemberReturnRequest(null, note))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.note").exists());
        this.mockMvc.perform(post("/api/acb/members/seven/rejoin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("memberId"));
        verifyNoInteractions(returns);

        when(returns.rejoin(7, null, "system")).thenThrow(new AcbRefusal(AcbRefusalCode.MEMBER_NOT_ENDED,
                "Only a member whose membership ended joins again. This member is Active.",
                Map.of("status", "Active")));
        this.mockMvc.perform(post("/api/acb/members/7/rejoin"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_ENDED"))
                .andExpect(jsonPath("$.details.status").value("Active"));
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

    @Test
    @DisplayName("03.3 a letter specimen is a PDF, inline, named for its template -- and a template that is not one is malformed")
    void aLetterSpecimenIsAPdf() throws Exception {
        when(letters.specimen(za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate.RE_DEBIT))
                .thenReturn("%PDF-1.7 specimen".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

        this.mockMvc.perform(get("/api/acb/letters/specimen/RE_DEBIT"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(org.springframework.http.MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", "inline; filename=\"specimen-re_debit.pdf\""));

        this.mockMvc.perform(get("/api/acb/letters/specimen/NO_SUCH_LETTER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"));
    }

}

package za.co.titandynamix.sasbo_online_backend.exception;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.controller.WorkplaceController;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceService;
import za.co.titandynamix.sasbo_online_backend.services.WorkplaceVisitService;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How month end's routes say no -- and only month end's (MD49, ME-11, ME-20, MR-SEC-20). A request that cannot be read
 * is {@code REQUEST_MALFORMED}, naming the field and giving nothing back; a refusal is the envelope with its code and
 * details, said once in the log without them; anything else is the 500 with a reference, logged scrubbed. THE TWIN
 * (MV-35): a route outside the arc answers a request it cannot read exactly as it always has.
 */
@WebMvcTest(WorkplaceController.class)
@Import({GlobalExceptionHandler.class, MonthEndExceptionHandler.class, JacksonConfig.class,
        MonthEndExceptionHandlerTest.OutsideTheArc.class})
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MonthEndExceptionHandler")
class MonthEndExceptionHandlerTest {

    /** A route of no arc -- the twin's. */
    @RestController
    static class OutsideTheArc {
        @GetMapping("/month-end-twin/number")
        String answer(@RequestParam Integer number) {
            return String.valueOf(number);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkplaceService workplaces;

    /** The controller's other collaborator since A.1 -- a workplace's visits. Not what this slice is about. */
    @MockitoBean
    private WorkplaceVisitService visits;

    @Test
    @DisplayName("ME-11: a word where a page number is wanted is REQUEST_MALFORMED, naming the field and giving nothing back")
    void aWordForANumber() throws Exception {
        mockMvc.perform(get("/api/workplaces").param("page", "PLANTEDWORD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("page"))
                .andExpect(content().string(not(containsString("PLANTEDWORD"))));
        mockMvc.perform(get("/api/workplaces").param("employer", "x1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.field").value("employer"));
    }

    @Test
    @DisplayName("🔒 MV-35's twin: a route outside the arc answers a request it cannot read as it always has -- the 500 of A63")
    void theTwinOutsideTheArc() throws Exception {
        mockMvc.perform(get("/month-end-twin/number").param("number", "not-a-number"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    @DisplayName("ME-20: a refusal answers its envelope with its code and details, and is said once in the log without them")
    void aRefusalIsSaidOnce() throws Exception {
        when(workplaces.page(any(), any(), any(), any(), any(), any(), any())).thenThrow(new MonthEndRefusal(
                MonthEndRefusalCode.WORKPLACE_PAGE_INVALID, "That page does not exist.",
                Map.of("page", "9", "size", "50", "maxSize", "200")));

        try (LogCapture log = LogCapture.on(MonthEndExceptionHandler.class)) {
            mockMvc.perform(get("/api/workplaces").param("page", "9"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WORKPLACE_PAGE_INVALID"))
                    .andExpect(jsonPath("$.details.maxSize").value("200"))
                    .andExpect(jsonPath("$.message").value("That page does not exist."));
            assertThat(log.renderedAt(Level.INFO)).contains("WORKPLACE_PAGE_INVALID").doesNotContain("maxSize");
        }
    }

    @Test
    @DisplayName("🔒 MR-SEC-20: anything else is the 500 with a reference -- and its message reaches neither the answer nor the log")
    void anythingElseIsScrubbed() throws Exception {
        when(workplaces.page(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("SELECT ... WHERE name LIKE 'PLANTED-VALUE'"));

        try (LogCapture log = LogCapture.on(MonthEndExceptionHandler.class)) {
            mockMvc.perform(get("/api/workplaces"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message", containsString("Quote reference")))
                    .andExpect(content().string(not(containsString("PLANTED-VALUE"))));
            assertThat(log.rendered())
                    .contains("IllegalStateException")
                    .doesNotContain("PLANTED-VALUE");
        }
    }

    @Test
    @DisplayName("🔒 ME-12, MD71: a constraint nobody named is this server's defect -- the 500 with a reference, never the global handler's 409 about identity numbers")
    void aConstraintNobodyNamedIsTheServersDefect() throws Exception {
        when(workplaces.page(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"planted_index\" Key (code)=(PLANTED-VALUE)"));

        try (LogCapture log = LogCapture.on(MonthEndExceptionHandler.class)) {
            mockMvc.perform(get("/api/workplaces"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message", containsString("Quote reference")))
                    .andExpect(content().string(not(containsString("identity number"))))
                    .andExpect(content().string(not(containsString("PLANTED-VALUE"))));
            assertThat(log.rendered()).contains("DataIntegrityViolationException").doesNotContain("PLANTED-VALUE");
        }
    }
}

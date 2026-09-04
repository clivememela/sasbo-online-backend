package za.co.titandynamix.sasbo_online_backend.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The endpoint behind the known job-grades defect, and the one place in the
 * suite where a controller's own error handling is worth a comment.
 *
 * <p>{@code JobGradeController} catches {@code Exception} itself and answers
 * {@code 500} with {@code List.of("ERROR: " + e.getMessage())} -- the exception
 * message, in the response body, as though it were data. That is the same
 * internals leak as the catch-all handler's, in a second place, and it reaches
 * the browser as a list of grades containing one string starting "ERROR:".
 * Recorded as a finding; the tests below assert the status and the shape,
 * never the message.
 */
@WebMvcTest(JobGradeController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class})
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("JobGradeController")
class JobGradeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobGradeService jobGradeService;

    @Test
    @DisplayName("GET /api/job-grades returns the grade strings, not the entities")
    void returnsGradeStrings() throws Exception {
        when(this.jobGradeService.findByBankIdentifier("632005"))
                .thenReturn(List.of(grade("BA1"), grade("BA2")));

        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "632005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("BA1"))
                .andExpect(jsonPath("$[1]").value("BA2"));
    }

    @Test
    @DisplayName("bankCode wins when both parameters are sent")
    void bankCodeTakesPrecedence() throws Exception {
        // The controller's only real decision, and nothing was asserting it.
        // Without this, it could read either parameter and the stubbed response
        // would look identical.
        when(this.jobGradeService.findByBankIdentifier("632005")).thenReturn(List.of());

        this.mockMvc.perform(get("/api/job-grades")
                        .param("bankCode", "632005")
                        .param("bank", "Absa"))
                .andExpect(status().isOk());

        verify(this.jobGradeService).findByBankIdentifier("632005");
    }

    @Test
    @DisplayName("falls back to bank when bankCode is absent or blank")
    void fallsBackToBankName() throws Exception {
        when(this.jobGradeService.findByBankIdentifier("Absa")).thenReturn(List.of());

        this.mockMvc.perform(get("/api/job-grades").param("bank", "Absa"))
                .andExpect(status().isOk());
        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "  ").param("bank", "Absa"))
                .andExpect(status().isOk());

        verify(this.jobGradeService, org.mockito.Mockito.times(2))
                .findByBankIdentifier("Absa");
    }

    @Test
    @DisplayName("answers with an empty array, not a 404, when the bank has no grades")
    void emptyIsASuccessfulAnswer() throws Exception {
        when(this.jobGradeService.findByBankIdentifier("632005")).thenReturn(List.of());

        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "632005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    private static JobGrade grade(String value) {
        JobGrade jobGrade = new JobGrade();
        jobGrade.setGrade(value);
        return jobGrade;
    }
}

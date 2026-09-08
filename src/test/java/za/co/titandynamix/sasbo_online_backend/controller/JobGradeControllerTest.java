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
 * The endpoint behind the known job-grades defect.
 *
 * <h2>🔑 Two things changed here upstream, and both are recorded rather than
 * quietly absorbed</h2>
 *
 * <p><b>The internals leak is gone.</b> This controller used to catch
 * {@code Exception} itself and answer {@code 500} with
 * {@code List.of("ERROR: " + e.getMessage())} -- the exception message, in the
 * response body, as though it were data, reaching the browser as a list of
 * grades containing one string starting "ERROR:". The catch-all has since been
 * removed and the controller now has no error handling of its own, so
 * {@code GlobalExceptionHandler} answers for it. The tests below still assert
 * status and shape and never a message, which is why none of them had to change
 * for that.
 *
 * <p><b>🔴 The RESPONSE SHAPE changed, and this class did not fail to compile
 * -- it failed at runtime, on an assertion, which is the only reason anyone
 * looked.</b> The endpoint returned a bare array of grade strings
 * ({@code ["BA1","BA2"]}) and now returns objects
 * ({@code [{"value":"BA1","label":"BA1 - Clerk - Permanent"}]}), because the
 * client needs something to show that is not the code. A response-shape change
 * is invisible to the compiler on both sides of the wire; the assertions below
 * are what make it visible here rather than in a browser.
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
    @DisplayName("GET /api/job-grades returns {value,label} objects, not the entities")
    void returnsValueLabelObjects() throws Exception {
        when(this.jobGradeService.findByBankIdentifier("632005"))
                .thenReturn(List.of(grade("BA1"), grade("BA2")));

        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "632005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("BA1"))
                .andExpect(jsonPath("$[0].label").value("BA1"))
                .andExpect(jsonPath("$[1].value").value("BA2"))
                .andExpect(jsonPath("$[1].label").value("BA2"))
                // Neither the entity nor any of its other columns may reach the
                // wire. Asserting the shape alone would pass on a response that
                // ALSO carried `id`, `description` and `bank` -- and the reason
                // this endpoint returns a projection at all is that it must not.
                .andExpect(jsonPath("$[0].length()").value(2));
    }

    @Test
    @DisplayName("the label is the grade, its description and its contract type, joined")
    void labelIsComposed() throws Exception {
        // 🔑 THE LABEL IS THE WHOLE REASON THE SHAPE CHANGED, so it is the thing
        // worth asserting. `value` is what gets submitted and `label` is what a
        // human picks from; a label that silently equalled the value would make
        // the dropdown useless while every shape assertion above still passed.
        when(this.jobGradeService.findByBankIdentifier("632005"))
                .thenReturn(List.of(grade("C1", "Clerk", "Permanent")));

        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "632005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("C1"))
                .andExpect(jsonPath("$[0].label").value("C1 - Clerk - Permanent"));
    }

    @Test
    @DisplayName("every value is non-blank, and a grade without one is dropped rather than sent")
    void gradesWithoutAValueAreOmitted() throws Exception {
        // A null `value` reaches the client as an option that cannot be
        // submitted, and `Map.of` would throw on a null value before that --
        // turning a data problem into a 500. The controller filters instead, so
        // assert the filter rather than trusting it.
        when(this.jobGradeService.findByBankIdentifier("632005"))
                .thenReturn(List.of(grade(null), grade("BA1")));

        this.mockMvc.perform(get("/api/job-grades").param("bankCode", "632005"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].value").value("BA1"));
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
        return grade(value, null, null);
    }

    private static JobGrade grade(String value, String description, String contractType) {
        JobGrade jobGrade = new JobGrade();
        jobGrade.setGrade(value);
        jobGrade.setDescription(description);
        jobGrade.setContractType(contractType);
        return jobGrade;
    }
}

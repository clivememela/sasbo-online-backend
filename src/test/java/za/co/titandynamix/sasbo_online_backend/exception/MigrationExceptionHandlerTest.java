package za.co.titandynamix.sasbo_online_backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.config.MigrationApi;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * How the data migration's routes say no -- and only theirs: a refusal is the envelope with its code and details, said
 * once in the log WITHOUT the details; a request that cannot be read names the field and gives nothing back; anything
 * else is the 500 with a reference, logged scrubbed -- the class and the stack, never the message, which here carries an
 * identity number. THE TWIN: a route outside the migration answers a request it cannot read exactly as it always has.
 */
@WebMvcTest(controllers = {MigrationExceptionHandlerTest.Migration.class, MigrationExceptionHandlerTest.Elsewhere.class})
@Import({GlobalExceptionHandler.class, MigrationExceptionHandler.class, JacksonConfig.class,
        MigrationExceptionHandlerTest.Migration.class, MigrationExceptionHandlerTest.Elsewhere.class})
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets Logback
// whenever a context starts, so a capture beside another class's start can come back empty.
@Isolated
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MigrationExceptionHandler")
class MigrationExceptionHandlerTest {

    /** Invented and valid in shape: the value no log line may ever carry. */
    private static final String PLANTED_ID = "8001015009087";

    @RestController
    @MigrationApi
    static class Migration {

        @GetMapping("/migration-test/refused")
        String refused() {
            throw new MigrationRefusal(MigrationRefusalCode.BATCH_NOT_IN_STATE, "This batch cannot be moved while it is DRAFT.",
                    MigrationRefusal.details("status", "DRAFT", "row", PLANTED_ID));
        }

        @GetMapping("/migration-test/number")
        String number(@RequestParam Integer number) {
            return String.valueOf(number);
        }

        @GetMapping("/migration-test/failed")
        String failed() {
            throw new IllegalStateException("insert failed for identity number " + PLANTED_ID);
        }
    }

    @RestController
    static class Elsewhere {

        @GetMapping("/elsewhere-test/number")
        String number(@RequestParam Integer number) {
            return String.valueOf(number);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("a refusal: its status, code, sentence and details -- logged once by its code, the details left out")
    void refusal() throws Exception {
        try (LogCapture log = LogCapture.on(MigrationExceptionHandler.class)) {
            mockMvc.perform(get("/migration-test/refused"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("BATCH_NOT_IN_STATE"))
                    .andExpect(jsonPath("$.message").value("This batch cannot be moved while it is DRAFT."))
                    .andExpect(jsonPath("$.details.status").value("DRAFT"));
            assertThat(log.rendered()).contains("BATCH_NOT_IN_STATE").doesNotContain(PLANTED_ID);
        }
    }

    @Test
    @DisplayName("REQUEST_MALFORMED: a word where a number is wanted -- the field named, the word not given back")
    void malformed() throws Exception {
        mockMvc.perform(get("/migration-test/number").param("number", "eleven"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_MALFORMED"))
                .andExpect(jsonPath("$.details.field").value("number"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("eleven"))));
    }

    @Test
    @DisplayName("🔒 anything else: the 500 with a reference, the message neither answered nor logged")
    void scrubbed() throws Exception {
        try (LogCapture log = LogCapture.on(MigrationExceptionHandler.class)) {
            mockMvc.perform(get("/migration-test/failed"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                            "Something went wrong on our side. Quote reference")));
            String logged = log.rendered();
            assertThat(logged).contains("Migration failed on GET /migration-test/failed");
            assertThat(logged).as("the identity number in the exception's message").doesNotContain(PLANTED_ID);
        }
    }

    @Test
    @DisplayName("the twin: a route outside the migration answers an unreadable request as it always has")
    void elsewhereUnmoved() throws Exception {
        mockMvc.perform(get("/elsewhere-test/number").param("number", "eleven"))
                .andExpect(jsonPath("$.code").doesNotExist());
    }
}

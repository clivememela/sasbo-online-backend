package za.co.titandynamix.sasbo_online_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application's {@code ObjectMapper}, which is the shape of every response.
 *
 * <h2>Why a config class earns a test</h2>
 * This bean <b>replaces</b> Spring Boot's auto-configured {@code ObjectMapper}
 * rather than customising it. Everything Boot's default did and this one does
 * not is silently lost, and the loss shows up as a wire-format change that no
 * controller test is looking for -- each one asserts its own fields and none of
 * them asserts how a date is written.
 *
 * <p>That is exactly what happened here, and this file is where it is recorded.
 */
@DisplayName("JacksonConfig")
class JacksonConfigTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    @Test
    @DisplayName("ignores unknown properties, so an older server tolerates a newer client")
    void ignoresUnknownProperties() throws Exception {
        // Deliberate, and worth pinning: the browser sends fields this server
        // does not know about, and failing on them would make every client
        // release a breaking change.
        record Known(String name) { }

        Known parsed = this.objectMapper.readValue(
                "{\"name\":\"Absa\",\"somethingNewTheClientSent\":true}", Known.class);

        assertThat(parsed.name()).isEqualTo("Absa");
    }

    @Test
    @DisplayName("understands java.time at all")
    void registersJavaTime() throws Exception {
        // findAndAddModules() picks up JavaTimeModule. Without it a LocalDate
        // fails to serialise entirely, so this is the half that does work.
        assertThat(this.objectMapper.writeValueAsString(LocalDate.of(2023, 6, 15)))
                .isNotBlank();
    }

    @Test
    @Disabled(KnownDefects.DATES_SERIALISE_AS_ARRAYS)
    @DisplayName("writes dates as ISO-8601 strings, not as numeric arrays")
    void datesAreIso8601() throws Exception {
        // Boot's auto-configured mapper disables WRITE_DATES_AS_TIMESTAMPS.
        // This hand-built one never does, so every date on the wire is
        // [2023,6,15] instead of "2023-06-15" -- and every consumer, including
        // the browser, has to special-case it.
        //
        // Written to the correct behaviour and left red on purpose: fixing it
        // is a breaking wire change that has to move the client in the same
        // step, which makes it a decision rather than a repair.
        assertThat(this.objectMapper.writeValueAsString(LocalDate.of(2023, 6, 15)))
                .isEqualTo("\"2023-06-15\"");
        assertThat(this.objectMapper.writeValueAsString(
                LocalDateTime.of(2023, 6, 15, 9, 30)))
                .isEqualTo("\"2023-06-15T09:30:00\"");
    }
}

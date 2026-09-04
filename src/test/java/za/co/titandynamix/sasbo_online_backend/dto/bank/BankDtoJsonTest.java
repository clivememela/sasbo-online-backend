package za.co.titandynamix.sasbo_online_backend.dto.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.json.JacksonTester;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;
import za.co.titandynamix.sasbo_online_backend.support.Synthetic;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payload contract for the {@code bank} response DTO. The reference for
 * every other DTO the client reads.
 *
 * <h2>Why this exists beside the API-surface guard</h2>
 * The surface guard checks which <b>paths</b> exist. Nothing checks the
 * <b>shape of what comes back</b>. Renaming a DTO field leaves the surface
 * guard perfectly green and breaks the client silently -- the endpoint is still
 * there, still answers 200, and the field the browser reads is simply absent.
 * These two guards cover different things and neither substitutes for the
 * other.
 *
 * <p>The JSON <i>is</i> the contract and the server is its author, which is
 * where "the server is authoritative, the client fetches" stops being an
 * intention and becomes a test.
 */
// No @JsonTest, and no Spring context. JacksonConfig.objectMapper() takes no
// arguments, so the application's real mapper is one constructor call away --
// and a context boot is the most expensive thing in the fast lane. The
// assertions are unchanged: this is still the application's mapper, not Boot's
// default, which is the whole point of the file.
@DisplayName("BankDto payload contract")
class BankDtoJsonTest {

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    private JacksonTester<BankDto> json;

    @BeforeEach
    void bindTester() {
        JacksonTester.initFields(this, this.objectMapper);
    }

    @Test
    @DisplayName("serialises exactly the three field names the client reads")
    void serialisesExactFieldNames() throws Exception {
        BankDto dto = new BankDto();
        dto.setId(1);
        dto.setName("Absa");
        dto.setUniversalBranchCode("632005");

        var written = this.json.write(dto);

        assertThat(written).extractingJsonPathNumberValue("$.id").isEqualTo(1);
        assertThat(written).extractingJsonPathStringValue("$.name").isEqualTo("Absa");
        assertThat(written).extractingJsonPathStringValue("$.universalBranchCode")
                .isEqualTo("632005");
    }

    @Test
    @DisplayName("emits no field the client does not know about")
    void emitsNoUnexpectedField() throws Exception {
        // The other half of the contract, and the half usually left out. A DTO
        // that grows a field is a change to the response every consumer parses,
        // and a @Data class grows one the moment somebody adds a property.
        // Discovered from the type rather than listed, so this cannot go stale.
        var tree = this.objectMapper.readTree(
                this.json.write(Synthetic.populated(BankDto.class)).getJson());

        var declared = Synthetic.fieldsOf(BankDto.class).stream()
                .map(java.lang.reflect.Field::getName)
                .toList();
        var emitted = new java.util.ArrayList<String>();
        tree.fieldNames().forEachRemaining(emitted::add);

        assertThat(emitted).containsExactlyInAnyOrderElementsOf(declared);
    }

    @Test
    @DisplayName("a null field is emitted as null rather than omitted")
    void nullsAreEmittedNotOmitted() throws Exception {
        // BankDto carries no @JsonInclude, so absent means null and the client
        // can rely on the key being there. Worth asserting because adding
        // @JsonInclude(NON_NULL) later would be an invisible breaking change:
        // the field simply stops appearing.
        BankDto dto = new BankDto();
        dto.setId(1);

        var written = this.json.write(dto);

        assertThat(written).hasJsonPath("$.name");
        assertThat(written).extractingJsonPathStringValue("$.name").isNull();
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.titandynamix.sasbo_online_backend.config.JacksonConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The COSATU positions deserialiser, which accepts two different JSON types for
 * one column and had no test at all.
 *
 * <h2>Why this needs its own test rather than coverage through a DTO</h2>
 * It is the only place in the application where <b>the wire type and the stored
 * type disagree on purpose</b>. The browser sends
 * {@code ["PEC","CEC"]} because the control is a multi-select; the column is a
 * single {@code VARCHAR(500)} holding {@code "PEC,CEC"}. Every other field is
 * the same shape on both sides, so this is the one converter whose behaviour is
 * not implied by the field's declaration.
 *
 * <p>{@code JacksonConfigTest} exists and does not reach this class: it asserts
 * the mapper's own configuration, and this converter only runs when a
 * {@code MemberDto} field carrying the annotation is read.
 *
 * <h2>🔴 The branch that matters, and why it is asserted separately</h2>
 * <b>An empty array becomes {@code ""}, not {@code null}</b> -- since V55. It was
 * {@code null}, pinned here so that a change would be "a deliberate act rather than
 * a discovery", and V55 is that act: the member merge ignores a null (decision
 * ⑨(b): absent keeps, blank clears), so unticking every box on Edit Member kept
 * the stored list. The browser cannot tell the two apart ({@code normalizeMember}
 * unfolds both to {@code []}); the merge can, which is why the pin moved.
 *
 * <p>No Spring context and no {@code @JsonTest}: {@code JacksonConfig}'s mapper
 * is one constructor call away, and a context boot is the most expensive thing
 * in the fast lane. This is the pattern {@code BankDtoJsonTest} established, for
 * the same reason.
 */
@DisplayName("StringArrayToCommaSeparatedDeserializer")
class StringArrayToCommaSeparatedDeserializerTest {

    /**
     * One mapper for the class, not one per test.
     *
     * <p>{@code JacksonConfig.objectMapper()} calls {@code findAndAddModules()},
     * which is a {@code ServiceLoader} scan of the whole test classpath, and each
     * fresh mapper then re-introspects {@code MemberDto}'s 65 fields from an
     * empty deserializer cache. As an instance field under the default
     * per-method lifecycle that happened eight times for eight assertions.
     *
     * <p>Safe under this module's {@code classes.default = concurrent}: an
     * {@code ObjectMapper} is thread-safe once configured, and methods within one
     * class run {@code same_thread} here regardless.
     */
    private static final ObjectMapper OBJECT_MAPPER = new JacksonConfig().objectMapper();

    /**
     * Read through {@code MemberDto}, not by invoking the deserialiser directly.
     *
     * <p>🔑 The annotation is half the behaviour. Calling
     * {@code deserialize(parser, context)} by hand would prove the conversion
     * works while saying nothing about whether {@code cosatuPositions} is wired
     * to it -- and an unwired converter is the failure that would actually
     * happen, because it is one deleted annotation away.
     */
    private static String positionsFrom(String json) throws Exception {
        return OBJECT_MAPPER.readValue(json, MemberDto.class).getCosatuPositions();
    }

    @Test
    @DisplayName("a JSON array becomes a comma-separated string")
    void anArrayIsJoined() throws Exception {
        assertThat(positionsFrom("{\"cosatuPositions\":[\"PEC\",\"CEC\"]}"))
                .isEqualTo("PEC,CEC");
    }

    @Test
    @DisplayName("a single-element array gets no trailing separator")
    void aSingleElementArrayHasNoTrailingComma() throws Exception {
        // The classic off-by-one in a hand-rolled join. `String.join` does not
        // make this mistake, which is exactly why the assertion is cheap and
        // worth keeping: it pins the behaviour against a future hand-rolled loop.
        assertThat(positionsFrom("{\"cosatuPositions\":[\"PEC\"]}")).isEqualTo("PEC");
    }

    @Test
    @DisplayName("🔴 V55 an EMPTY array becomes an empty string, which clears -- not null, which the merge keeps")
    void anEmptyArrayBecomesBlank() throws Exception {
        // The deliberate change the header asked for. It pinned null, and null is what
        // MemberMapper.updateFromMember IGNORES (decision ⑨(b): absent keeps, blank clears) -- so
        // unticking every position on Edit Member kept the stored positions and still answered
        // 200. Harmless while no screen offered the boxes; the record offers them again with V55.
        assertThat(positionsFrom("{\"cosatuPositions\":[]}")).isEmpty();
    }

    @Test
    @DisplayName("V55 the BEC structures ride the same converter -- the annotation is half the behaviour")
    void theBecStructuresAreJoinedToo() throws Exception {
        MemberDto read = OBJECT_MAPPER.readValue("{\"becStructures\":[\"INC / IC\",\"VP\"]}", MemberDto.class);
        assertThat(read.getBecStructures()).isEqualTo("INC / IC,VP");
    }

    @Test
    @DisplayName("a plain string passes through untouched")
    void aPlainStringPassesThrough() throws Exception {
        // The read-back path. The column holds "PEC,CEC", so a member fetched
        // and re-submitted arrives as a STRING rather than an array -- and the
        // round trip has to survive that without re-splitting or re-joining.
        assertThat(positionsFrom("{\"cosatuPositions\":\"PEC,CEC\"}")).isEqualTo("PEC,CEC");
    }

    @Test
    @DisplayName("an explicit JSON null stays null")
    void anExplicitNullStaysNull() throws Exception {
        // Jackson short-circuits null before the deserialiser is consulted, so
        // this asserts the FIELD's behaviour rather than the converter's. It is
        // here because "the member is not in COSATU" is the common case and the
        // one whose breakage would be widest.
        assertThat(positionsFrom("{\"cosatuPositions\":null}")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "PEC", "PEC,CEC" })
    @DisplayName("the array and string forms of the same value agree")
    void bothWireFormsAgree(String stored) throws Exception {
        // 🔑 THE PROPERTY THE TWO BRANCHES EXIST TO GUARANTEE, asserted as one
        // thing rather than inferred from the two cases above passing
        // separately. The browser sends an array on capture and a string on
        // re-submit; if those two paths ever disagreed, a member's positions
        // would change simply by being saved twice, and neither branch's own
        // test would notice.
        //
        // Two rows, and they are the only two shapes that differ here: one
        // token, and two with the separator between them. A third single-token
        // value would be the first case again under a different name.
        //
        // 🔑 THE EXPECTED VALUE IS THE INPUT, which is the point rather than a
        // shortcut -- `stored` is what the column holds, and the claim is that
        // both wire forms round-trip TO IT. A separate expected column stating
        // the same string twice hid that.
        String asArray = positionsFrom(
                "{\"cosatuPositions\":[\"" + stored.replace(",", "\",\"") + "\"]}");

        assertThat(asArray).isEqualTo(stored);
        assertThat(positionsFrom("{\"cosatuPositions\":\"" + stored + "\"}")).isEqualTo(stored);
    }
}

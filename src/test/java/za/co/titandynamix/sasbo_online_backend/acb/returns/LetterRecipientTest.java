package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** 🔒 R3-16, SD3-C: a letter goes to ONE address -- nothing that could carry a header or a second recipient. */
@DisplayName("LetterRecipient.sendable -- one address, no more")
class LetterRecipientTest {

    @ParameterizedTest
    @ValueSource(strings = {"member@example.org", "first.last+acb@mail.example.org"})
    @DisplayName("the must-pass twins: an ordinary address is sendable")
    void anOrdinaryAddress(String address) {
        assertThat(LetterRecipient.sendable(address)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"member@example.org\r\nBcc: other@example.org", "member@example.org\nX: y",
            "a@example.org, b@example.org", "a@example.org;b@example.org", "Member <member@example.org>",
            "member at example.org", "", "member@example"})
    @DisplayName("a line break, a second recipient, a display name or no domain is never sent to")
    void anythingElseIsNot(String address) {
        assertThat(LetterRecipient.sendable(address)).isFalse();
    }
}

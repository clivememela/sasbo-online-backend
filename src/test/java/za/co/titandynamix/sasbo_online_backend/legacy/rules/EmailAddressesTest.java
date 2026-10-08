package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The capture screen's rule for an e-mail address's shape, so a moved address is one the screen would accept. */
@DisplayName("EmailAddresses")
class EmailAddressesTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ann.example@example.org", "a@b.co", "first+tag@sub.example.co.za"})
    @DisplayName("an address of the screen's shape")
    void valid(String address) {
        assertThat(EmailAddresses.valid(address)).isTrue();
    }

    @ParameterizedTest(name = "''{0}''")
    @ValueSource(strings = {"not-an-email", "a@b", "@example.org", "ann example@example.org", "ann@exam ple.org", ""})
    @DisplayName("EMAIL_INVALID: no at sign, no dot after it, nothing before it, or a space")
    void invalid(String address) {
        assertThat(EmailAddresses.valid(address)).isFalse();
    }

    @Test
    @DisplayName("no longer than 254 characters, and null is not an address")
    void bounds() {
        assertThat(EmailAddresses.valid("a".repeat(250) + "@b.co")).isFalse();
        assertThat(EmailAddresses.valid(null)).isFalse();
    }
}

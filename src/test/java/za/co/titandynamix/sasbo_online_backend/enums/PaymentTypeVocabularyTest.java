package za.co.titandynamix.sasbo_online_backend.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The server half of the vocabulary the browser and the server share.
 *
 * <h2>Two guards, and both are needed</h2>
 * A static comparison of two lists shows that the vocabularies differ. Only an
 * <b>executed</b> test shows which side actually breaks -- and that is what
 * tells you which side to fix. This is the executed half: every value the
 * browser can send, resolved by the server.
 *
 * <h2>Where these values come from</h2>
 * Transcribed from the browser's own options list. Three independent
 * repositories cannot share a constants module, so the list is duplicated
 * deliberately and this comment names its counterpart:
 * {@code PAYMENT_TYPE_OPTIONS} and {@code IDENTITY_TYPE_OPTIONS} in the
 * browser's member constants.
 *
 * <p>The drift this exists to catch is already here.
 * {@code fromString} carries a {@code PAYROLL_DEDUCTION} shim while the
 * browser's options offer {@code 'Payroll'} -- so the shim is for a value
 * nothing sends any more, and one hook still branches on
 * {@code 'Payroll Deduction'}, which nothing produces.
 */
@DisplayName("Payment type vocabulary")
class PaymentTypeVocabularyTest {

    /**
     * Every value the browser can put in {@code paymentType}. Its counterpart
     * is {@code PAYMENT_TYPE_OPTIONS} in the browser's member constants.
     */
    private static final String[] WHAT_THE_BROWSER_SENDS = {"Debit Order", "Payroll"};

    @ParameterizedTest
    @ValueSource(strings = {"Debit Order", "Payroll"})
    @DisplayName("resolves every value the browser can send")
    void resolvesEveryBrowserValue(String sent) {
        // If this reddens, the browser is sending something the server rejects
        // -- which reaches a real person as a failed capture, not as a warning.
        assertThat(PaymentType.fromString(sent)).isNotNull();
    }

    @Test
    @DisplayName("the transcribed list still matches what the server offers as metadata")
    void theTranscribedListMatchesTheServersOwn() {
        // The other direction. The server publishes its payment types through
        // /api/members/metadata/payment-types, and if it grows one that the
        // browser's list does not have, this notices -- which is the case a
        // per-value test cannot see, because it only iterates what it knows.
        assertThat(java.util.Arrays.stream(PaymentType.values())
                        .map(PaymentType::getDisplayName)
                        .toList())
                .as("the server's display names against the browser's options. A difference "
                        + "here is drift between the two halves, whichever side moved")
                .containsExactlyInAnyOrder(WHAT_THE_BROWSER_SENDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"debit order", "DEBIT ORDER", "DeBiT oRdEr", "  Debit Order  "})
    @DisplayName("resolves a display name whatever its case or surrounding space")
    void resolvesCaseAndWhitespaceVariants(String sent) {
        assertThat(PaymentType.fromString(sent)).isEqualTo(PaymentType.DEBIT_ORDER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DEBIT_ORDER", "PAYROLL"})
    @DisplayName("resolves the enum name as well as the display name")
    void resolvesEnumNames(String sent) {
        assertThat(PaymentType.fromString(sent)).isNotNull();
    }

    @Test
    @DisplayName("still accepts the legacy Payroll Deduction value")
    void acceptsTheLegacyShim() {
        // A shim for stored data, not a requirement. Named as legacy so nobody
        // reads this test as a specification and adds the value back to a
        // dropdown.
        assertThat(PaymentType.fromString("Payroll Deduction")).isEqualTo(PaymentType.PAYROLL);
        assertThat(PaymentType.fromString("PAYROLL_DEDUCTION")).isEqualTo(PaymentType.PAYROLL);
    }

    @Test
    @DisplayName("only Debit Order requires a bank account")
    void onlyDebitOrderRequiresABankAccount() {
        assertThat(PaymentType.DEBIT_ORDER.requiresBankAccount()).isTrue();
        assertThat(PaymentType.PAYROLL.requiresBankAccount()).isFalse();
    }

    @Test
    @DisplayName("returns nothing for a blank value, and throws for an unknown one")
    void blankIsNothingAndUnknownThrows() {
        // Two different outcomes for two different situations, and the
        // difference matters: blank means "not captured yet" and unknown means
        // "somebody sent something wrong".
        assertThat(PaymentType.fromString(null)).isNull();
        assertThat(PaymentType.fromString("   ")).isNull();

        assertThatThrownBy(() -> PaymentType.fromString("Cash"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

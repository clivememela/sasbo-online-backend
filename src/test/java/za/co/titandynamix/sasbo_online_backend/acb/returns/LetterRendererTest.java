package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The union's three letters, filled from a synthetic unpaid.
 *
 * <p>Synthetic data only -- the letter scans hold real members, and nothing of theirs is used here.
 */
@DisplayName("LetterRenderer -- the union's letters, in the union's words")
class LetterRendererTest {

    private static final String SYNTHETIC_ACCOUNT = "6200999911";

    static LetterRenderer.LetterData data(UnpaidReason reason) {
        return new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 12), "K", "Mr", "AB", "Botha",
                List.of("12 Synthetic Street", "Testville"), "2021",
                "M-100116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), reason.wording(), 25);
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("every placeholder in every template is filled, and the account is masked to its last four")
    void everyTemplateRendersWithNoGapAndNoAccount(LetterTemplate template) {
        String letter = LetterRenderer.render(template, data(UnpaidReason.PAYMENT_STOPPED));
        assertThat(letter).doesNotContain("{{").doesNotContain("}}");
        assertThat(letter).doesNotContain(SYNTHETIC_ACCOUNT);
        assertThat(letter).doesNotContain("<<<");
        assertThat(letter).contains("M-100116");
    }

    @Test
    @DisplayName("the return-debit letter names the month in capitals and the reason, with the masked account")
    void theReturnDebitLetter() {
        String letter = LetterRenderer.render(LetterTemplate.RETURN_DEBIT, data(UnpaidReason.PAYMENT_STOPPED));
        assertThat(letter).contains("for the month of SEPTEMBER has been returned")
                .contains("unpaid with the answer : Payment Stopped.")
                .contains("ACCOUNT NO :   ****9911")
                .contains("Dear Member")
                .contains("12/10/26")
                .contains("UNIVERSAL");
        // The typing slip is corrected; the words are otherwise the union's.
        assertThat(letter).contains("thereby ensuring that we can update our records.").doesNotContain("therby");
    }

    @Test
    @DisplayName("the account-closed letter addresses the member by name, with its full stop restored")
    void theAccountClosedLetter() {
        String letter = LetterRenderer.render(LetterTemplate.ACCOUNT_CLOSED, data(UnpaidReason.ACCOUNT_CLOSED));
        assertThat(letter).contains("Dear Mr Botha")
                .contains("RE : YOUR MEMBERSHIP NUMBER : M-100116")
                .contains("in order to retain membership of the union.")
                // A word choice, not a typing slip: it stays as SASBO wrote it.
                .contains("or alternately, supply your new banking details");
    }

    @Test
    @DisplayName("🔒 Q32: the re-debit letter prints the member's OWN debit day -- the 25th here, never a literal 20th")
    void theReDebitLetter() {
        String letter = LetterRenderer.render(LetterTemplate.RE_DEBIT, data(UnpaidReason.NOT_PROVIDED_FOR));
        assertThat(letter).contains("month of September was returned 'Not Provided For'.")
                .contains("your Union subscription for the")
                .contains("on the usual debit day")
                .contains("your account on the 25th of each month")
                .doesNotContain("on the 20 of each month")
                .contains("three months, the membership will be");
        assertThat(letter).doesNotContain("subsription").doesNotContain("usualdebit");
    }

    @Test
    @DisplayName("a day is written as an ordinal: 20th, 21st, 22nd, 23rd, 25th, 31st")
    void ordinals() {
        assertThat(LetterRenderer.ordinal(20)).isEqualTo("20th");
        assertThat(LetterRenderer.ordinal(21)).isEqualTo("21st");
        assertThat(LetterRenderer.ordinal(22)).isEqualTo("22nd");
        assertThat(LetterRenderer.ordinal(23)).isEqualTo("23rd");
        assertThat(LetterRenderer.ordinal(25)).isEqualTo("25th");
        assertThat(LetterRenderer.ordinal(31)).isEqualTo("31st");
    }

    @Test
    @DisplayName("🔒 R3-16, SD3-C: a surname and address holding markup render as text, character for character")
    void markupIsText() {
        LetterRenderer.LetterData hostile = new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 12), "K", "Mr", "AB", "<b>Botha</b>",
                List.of("<img src=x onerror=alert(1)>", "Testville"), "2021",
                "M-100116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), "Account Closed", 25);
        String letter = LetterRenderer.render(LetterTemplate.ACCOUNT_CLOSED, hostile);
        assertThat(letter).contains("<b>Botha</b>").contains("<img src=x onerror=alert(1)>");
    }

    @Test
    @DisplayName("a member with no title is greeted 'Dear Member', and an empty address line stays a line")
    void missingParts() {
        LetterRenderer.LetterData untitled = new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 12), null, null, "AB", "Botha", List.of(), null,
                "M-100116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), "Account Closed", 25);
        String letter = LetterRenderer.render(LetterTemplate.ACCOUNT_CLOSED, untitled);
        assertThat(letter).contains("Dear Member");
        assertThat(letter.lines().filter(String::isBlank).count()).isGreaterThan(5);
    }

    @Test
    @DisplayName("masking keeps the last four digits and never the length")
    void masking() {
        assertThat(LetterRenderer.masked("1234567890")).isEqualTo("****7890");
        assertThat(LetterRenderer.masked("123")).isEqualTo("****");
        assertThat(LetterRenderer.masked(null)).isEqualTo("****");
        assertThat(data(UnpaidReason.OTHER).toString()).doesNotContain(SYNTHETIC_ACCOUNT);
    }

    @Test
    @DisplayName("each reason names the letter it sends -- the letters review's table (October build); a deceased "
            + "account holder and an unknown code get none")
    void reasonsToLetters() {
        assertThat(UnpaidReason.NOT_PROVIDED_FOR.letter()).isEqualTo(LetterTemplate.RE_DEBIT);
        assertThat(UnpaidReason.PAYMENT_STOPPED.letter()).isEqualTo(LetterTemplate.PAYMENT_STOPPED);
        assertThat(UnpaidReason.ACCOUNT_FROZEN.letter()).isEqualTo(LetterTemplate.RETURN_DEBIT);
        assertThat(UnpaidReason.INVALID_ACCOUNT_NUMBER.letter()).isEqualTo(LetterTemplate.RETURN_DEBIT);
        assertThat(UnpaidReason.ACCOUNT_CLOSED.letter()).isEqualTo(LetterTemplate.ACCOUNT_CLOSED);
        assertThat(UnpaidReason.NO_AUTHORITY_TO_DEBIT.letter()).isEqualTo(LetterTemplate.NO_AUTHORITY);
        assertThat(UnpaidReason.AUTHORISATION_CANCELLED.letter()).isEqualTo(LetterTemplate.NO_AUTHORITY);
        assertThat(UnpaidReason.ACCOUNT_HOLDER_DECEASED.letter()).isNull();
        assertThat(UnpaidReason.OTHER.letter()).isNull();
    }
}

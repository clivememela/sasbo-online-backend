package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Text a spreadsheet will not read as a formula (month end 04.10, MV-13): the six first characters, each written behind
 * an apostrophe -- and ordinary text, including a hyphen that is not first, written as it is.
 */
@DisplayName("Text a spreadsheet shows, and never runs")
class SpreadsheetTextTest {

    @Test
    @DisplayName("🔒 MV-13: each of the six first characters a spreadsheet reads as a formula is written behind an apostrophe")
    void theSixFirstCharacters() {
        assertThat(SpreadsheetText.FIRST_CHARACTERS).hasSize(SpreadsheetText.FIRST_CHARACTERS_COUNT)
                .isEqualTo("=+-@\t\r");
        for (char first : new char[] {'=', '+', '-', '@', '\t', '\r'}) {
            String planted = first + "HYPERLINK(\"http://example.invalid\",\"open\")";
            assertThat(SpreadsheetText.safe(planted)).as("text beginning %s", (int) first).isEqualTo("'" + planted);
        }
    }

    @Test
    @DisplayName("must-pass: ordinary text is written as it is -- a name, a code and its name, a hyphen that is not first")
    void ordinaryText() {
        assertThat(SpreadsheetText.safe("INVENTED BANK")).isEqualTo("INVENTED BANK");
        assertThat(SpreadsheetText.safe("901 INVENTED BANK")).isEqualTo("901 INVENTED BANK");
        assertThat(SpreadsheetText.safe("A - INVENTED NORTH")).isEqualTo("A - INVENTED NORTH");
        assertThat(SpreadsheetText.safe("")).isEmpty();
        assertThat(SpreadsheetText.safe(null)).isNull();
    }
}

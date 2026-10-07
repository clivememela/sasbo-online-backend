package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Words an administrator types (October build): a status change's note and the reason a month was taken again, each
 * at most {@value FreeText#NOTE_LIMIT} characters of ordinary text, and a reason's name on a list.
 */
@DisplayName("FreeText -- what an administrator types, held to ordinary text of a length the column holds")
class FreeTextTest {

    @Test
    @DisplayName("trimmed, and blank is none")
    void trimmedAndBlankIsNone() {
        assertThat(FreeText.clean(null)).isNull();
        assertThat(FreeText.clean("")).isNull();
        assertThat(FreeText.clean(" \t\n ")).isNull();
        assertThat(FreeText.clean("  Unpaid leave agreed  ")).isEqualTo("Unpaid leave agreed");
        assertThat(FreeText.problem(null, 10, false)).as("none is never refused").isNull();
    }

    @Test
    @DisplayName("🔒 5 Oct, \"And how of our characters?\": a note of 500 characters is kept, one of 501 refused")
    void theLimitToTheCharacter() {
        String fiveHundred = "a".repeat(FreeText.NOTE_LIMIT);

        assertThat(FreeText.NOTE_LIMIT).isEqualTo(500);
        assertThat(FreeText.problem(fiveHundred, FreeText.NOTE_LIMIT, true)).isNull();
        assertThat(FreeText.problem(fiveHundred + "a", FreeText.NOTE_LIMIT, true)).isEqualTo(FreeText.Problem.TOO_LONG);
    }

    @Test
    @DisplayName("counted as PostgreSQL counts a VARCHAR -- by character: one outside Java's one unit counts once")
    void countedByCharacterNotByUtf16Unit() {
        // U+1F600 is two UTF-16 units and one character: 500 of them fit the column, as PostgreSQL counts.
        String faces = "😀".repeat(FreeText.NOTE_LIMIT);

        assertThat(faces.length()).isEqualTo(2 * FreeText.NOTE_LIMIT);
        assertThat(FreeText.problem(faces, FreeText.NOTE_LIMIT, true)).isNull();
        assertThat(FreeText.problem(faces + "😀", FreeText.NOTE_LIMIT, true))
                .isEqualTo(FreeText.Problem.TOO_LONG);
    }

    @Test
    @DisplayName("a note keeps its line breaks; a name, one line, does not")
    void lineBreaksWhereTheyAreOrdinary() {
        assertThat(FreeText.problem("Booked off.\nBack on the 3rd.\r\nInvented.", 500, true)).isNull();
        assertThat(FreeText.problem("Study\nleave", 60, false)).isEqualTo(FreeText.Problem.CONTROL_CHARACTERS);
        assertThat(FreeText.problem("Study\rleave", 60, false)).isEqualTo(FreeText.Problem.CONTROL_CHARACTERS);
    }

    @ParameterizedTest(name = "U+{0}")
    @ValueSource(ints = {0x00, 0x07, 0x09, 0x1B, 0x7F, 0x85, 0x9F, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x2066,
            0x2067, 0x2068, 0x2069, 0x2028, 0x2029})
    @DisplayName("🔒 no control character, no direction control that makes a note read otherwise, no line separator")
    void controlCharactersAreRefused(int character) {
        String text = "Invented " + new String(Character.toChars(character)) + " note";

        assertThat(FreeText.problem(text, 500, true)).isEqualTo(FreeText.Problem.CONTROL_CHARACTERS);
    }

    @Test
    @DisplayName("🔒 no half of a broken character, which the database would refuse outright")
    void aLoneSurrogateIsRefused() {
        assertThat(FreeText.problem("Invented \uD83D note", 500, true)).isEqualTo(FreeText.Problem.CONTROL_CHARACTERS);
        assertThat(FreeText.problem("Invented \uDE00 note", 500, true)).isEqualTo(FreeText.Problem.CONTROL_CHARACTERS);
    }

    @Test
    @DisplayName("must-pass: ordinary text in SASBO's languages, with accents, apostrophes and punctuation, is kept")
    void ordinaryTextIsKept() {
        assertThat(FreeText.problem("Siyabonga -- ma'am's café, 50% off (R174.00); ‘quoted’ – done!", 500, true))
                .isNull();
        assertThat(FreeText.problem("Maternity leave", 60, false)).isNull();
    }

    @Test
    @DisplayName("too long is said before the characters: the limit is what the caller is told first")
    void tooLongIsSaidFirst() {
        assertThat(FreeText.problem("\u0007".repeat(61), 60, false)).isEqualTo(FreeText.Problem.TOO_LONG);
    }
}

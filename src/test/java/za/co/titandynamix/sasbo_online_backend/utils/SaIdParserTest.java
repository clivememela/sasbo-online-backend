package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * South African identity numbers, and the two things this parser gets wrong.
 *
 * <h2>Named vectors, duplicated deliberately</h2>
 * The identity numbers below are a <b>frozen specification</b>, and the browser
 * has the same set under the same names. Three independent repositories cannot
 * share a fixture module -- that would be exactly the sibling-path coupling the
 * plans forbid -- so the vectors are duplicated on purpose, and each file names
 * its counterpart in a comment. The counterpart here is the browser's
 * {@code validateSAID} test.
 *
 * <p>Every one is synthetic. A real identity number in a fixture is a privacy
 * breach with a URL attached, because coverage reports, failure output and
 * traces all get forwarded.
 *
 * <h2>Clock-invariant properties only</h2>
 * {@code extractDateOfBirth} reads {@code LocalDate.now()} inside a
 * {@code static} method, so a test that mirrors that arithmetic asserts nothing
 * and <b>changes its own answer on 1 January</b>. Everything enabled below is
 * therefore a property that holds whatever the year is; the rollover itself is
 * asserted once, disabled, with the fix named.
 */
@DisplayName("SaIdParser")
class SaIdParserTest {

    /** Valid Luhn checksum, born 27 April 1994, female, SA citizen. */
    private static final String VALID_CHECK_DIGIT = "9404275800086";

    /** The same number with the final digit changed. Length is fine; the checksum is not. */
    private static final String WRONG_CHECK_DIGIT = "9404275800087";

    /** Month 13 -- unparseable as a date, whatever the century. */
    private static final String IMPOSSIBLE_MONTH = "9413275800086";

    /** Day 32 in a valid month. */
    private static final String IMPOSSIBLE_DAY = "9404325800086";

    @ParameterizedTest
    @ValueSource(strings = {VALID_CHECK_DIGIT, WRONG_CHECK_DIGIT})
    @DisplayName("accepts any thirteen digits as a well-formed identity number")
    void acceptsThirteenDigits(String idNumber) {
        assertThat(SaIdParser.isValidIdFormat(idNumber)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"940427580008", "94042758000866", "94042758000a6", "", "   "})
    @DisplayName("rejects anything that is not exactly thirteen digits")
    void rejectsWrongShapes(String idNumber) {
        assertThat(SaIdParser.isValidIdFormat(idNumber)).isFalse();
    }

    @Test
    @DisplayName("rejects null without throwing")
    void rejectsNull() {
        assertThat(SaIdParser.isValidIdFormat(null)).isFalse();
        assertThat(SaIdParser.extractDateOfBirth(null)).isEmpty();
        assertThat(SaIdParser.extractGender(null)).isEmpty();
    }

    @Test
    @DisplayName("reads the month and day from the identity number, whatever the century is")
    void readsMonthAndDayRegardlessOfCentury() {
        // Clock-invariant: the century this resolves to depends on the current
        // year, so only the month and day are asserted. A test that asserted
        // 1994 here would start failing in 2094 -- and, more to the point,
        // would be asserting the machine's clock rather than the parser.
        LocalDate dateOfBirth = SaIdParser.extractDateOfBirth(VALID_CHECK_DIGIT).orElseThrow();

        assertThat(dateOfBirth.getMonthValue()).isEqualTo(4);
        assertThat(dateOfBirth.getDayOfMonth()).isEqualTo(27);
        assertThat(dateOfBirth.getYear() % 100).isEqualTo(94);
    }

    @Test
    @DisplayName("never returns a date of birth in the future")
    void neverReturnsAFutureDate() {
        // The other clock-invariant property, and the one the century logic
        // exists to provide. True on any day the suite runs.
        assertThat(SaIdParser.extractDateOfBirth(VALID_CHECK_DIGIT).orElseThrow())
                .isBeforeOrEqualTo(LocalDate.now());
    }

    @ParameterizedTest
    @ValueSource(strings = {IMPOSSIBLE_MONTH, IMPOSSIBLE_DAY})
    @DisplayName("returns nothing for a date that cannot exist")
    void impossibleDatesYieldNothing(String idNumber) {
        assertThat(SaIdParser.extractDateOfBirth(idNumber)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
        "9404274999086, Female",
        "9404275000086, Male",
        "9404270000086, Female",
        "9404279999086, Male",
    })
    @DisplayName("reads gender from the sequence number, on both sides of the boundary")
    void readsGenderAtTheBoundary(String idNumber, String expected) {
        // 4999/5000 is the whole rule. Testing either side of it is the
        // difference between covering the rule and covering an example of it.
        assertThat(SaIdParser.extractGender(idNumber)).contains(expected);
    }

    @Test
    @Disabled(KnownDefects.SA_ID_CHECKSUM_NOT_VALIDATED)
    @DisplayName("rejects an identity number whose checksum does not add up")
    void rejectsAWrongCheckDigit() {
        // The browser runs the full Luhn check and rejects this number. The
        // server checks thirteen digits and nothing else, while this class's
        // own Javadoc documents "Z -- Luhn checksum digit". With permitAll() on
        // /api/**, a POST straight to the API bypasses the only validation that
        // exists anywhere.
        //
        // Same vectors as the browser's, so the day the server implements the
        // checksum the two agree by construction rather than by hope.
        assertThat(SaIdParser.isValidIdFormat(WRONG_CHECK_DIGIT)).isFalse();
    }

    @Test
    @Disabled(KnownDefects.CENTURY_INFERENCE_READS_THE_WALL_CLOCK)
    @DisplayName("resolves the century from an injected clock, not from today")
    void centuryComesFromAnInjectedClock() {
        // The rollover this parser cannot express. "94" resolves to 1994 today
        // and to 2094 once the two-digit current year passes 94 -- so the same
        // stored identity number silently changes its owner's date of birth,
        // and no test can pin the behaviour without moving the machine's clock.
        //
        // Written as the assertion an injected Clock would make possible. The
        // fix is a production change: SaIdParser is static and takes no
        // dependencies, so a Clock has to be threaded in.
        assertThat(SaIdParser.extractDateOfBirth(VALID_CHECK_DIGIT).orElseThrow().getYear())
                .isEqualTo(1994);
    }
}

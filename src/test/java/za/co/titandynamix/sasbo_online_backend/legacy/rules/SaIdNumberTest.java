package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity number checked the whole way, against numbers this repository already has an opinion about: the one
 * {@code MemberCaptureRoundTripIT} posts to PostgreSQL ({@code 9001010000080}, valid), and the one that shipped as the
 * member fixture's before SR-5 ({@code 9404275800086}), which {@code MemberFixtureTest} records as FAILING its check
 * digit -- the thirteenth should be 9. A number the capture screen refuses is refused here too. Every number is synthetic.
 */
@DisplayName("SaIdNumber")
class SaIdNumberTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 15);

    /** Born 27 April 1994, male, the check digit right. */
    private static final String VALID_CHECK_DIGIT = "9404275800089";
    /** The old fixture number: the same person, the check digit wrong (MemberFixtureTest). */
    private static final String WRONG_CHECK_DIGIT = "9404275800086";
    private static final String IMPOSSIBLE_MONTH = "9413275800086";
    private static final String IMPOSSIBLE_DAY = "9404325800086";

    @Test
    @DisplayName("a valid number has no flaw, and gives its date of birth and gender -- the round-trip IT's number too")
    void valid() {
        assertThat(SaIdNumber.flaw(VALID_CHECK_DIGIT, TODAY)).isEmpty();
        assertThat(SaIdNumber.dateOfBirth(VALID_CHECK_DIGIT, TODAY)).contains(LocalDate.of(1994, 4, 27));
        assertThat(SaIdNumber.gender(VALID_CHECK_DIGIT)).isEqualTo("Male");
        assertThat(SaIdNumber.flaw("9001010000080", TODAY)).isEmpty();
    }

    @Test
    @DisplayName("ID_NUMBER_CHECK_DIGIT: the thirteenth digit wrong -- the number that shipped as the fixture's, and the browser's")
    void checkDigit() {
        assertThat(SaIdNumber.flaw(WRONG_CHECK_DIGIT, TODAY)).contains(SaIdNumber.Flaw.CHECK_DIGIT);
        assertThat(SaIdNumber.flaw("9404275800087", TODAY)).contains(SaIdNumber.Flaw.CHECK_DIGIT);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({IMPOSSIBLE_MONTH, IMPOSSIBLE_DAY, "9002300000088", "0102290000082"})
    @DisplayName("ID_NUMBER_DATE: a month or day that cannot be, 30 February, and 29 February of a year with none")
    void impossibleDates(String id) {
        assertThat(SaIdNumber.flaw(id, TODAY)).contains(SaIdNumber.Flaw.DATE);
    }

    @ParameterizedTest(name = "''{0}''")
    @CsvSource({"940427580008", "94042758000866", "94042758000a6", "''"})
    @DisplayName("ID_NUMBER_FORMAT: anything but thirteen digits")
    void format(String id) {
        assertThat(SaIdNumber.flaw(id, TODAY)).contains(SaIdNumber.Flaw.FORMAT);
        assertThat(SaIdNumber.flaw(null, TODAY)).contains(SaIdNumber.Flaw.FORMAT);
    }

    @Test
    @DisplayName("the century from the day handed in, never the wall clock: 94 is 1994 in 2026, and 2094 once 2094 has come")
    void century() {
        assertThat(SaIdNumber.dateOfBirth(VALID_CHECK_DIGIT, TODAY)).contains(LocalDate.of(1994, 4, 27));
        assertThat(SaIdNumber.dateOfBirth(VALID_CHECK_DIGIT, LocalDate.of(2094, 4, 27))).contains(LocalDate.of(2094, 4, 27));
        assertThat(SaIdNumber.dateOfBirth("0001010000088", TODAY)).contains(LocalDate.of(2000, 1, 1));
        assertThat(SaIdNumber.dateOfBirth("2601010000083", TODAY)).as("a birthday earlier this year is this century's")
                .contains(LocalDate.of(2026, 1, 1));
        assertThat(SaIdNumber.dateOfBirth("2612010000083", TODAY)).as("a birthday later this year is last century's")
                .contains(LocalDate.of(1926, 12, 1));
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"9404274999086, Female", "9404275000086, Male", "9404270000086, Female", "9404279999086, Male"})
    @DisplayName("gender from the sequence, either side of the 4999/5000 boundary")
    void gender(String id, String expected) {
        assertThat(SaIdNumber.gender(id)).isEqualTo(expected);
    }

    @Test
    @DisplayName("agrees with the check digit the repository's own member fixture computes, a second implementation, on 500 numbers")
    void agreesWithTheFixture() {
        for (int i = 0; i < 500; i++) {
            String id = MemberFixture.syntheticIdFor(i);
            assertThat(SaIdNumber.checkDigitIsValid(id)).as(id).isTrue();
            assertThat(SaIdNumber.flaw(id, TODAY)).as(id).isEmpty();
        }
    }
}

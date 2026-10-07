package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** A South African number written one way, +27 and nine digits -- and anything else not a number at all. Invented numbers. */
@DisplayName("PhoneNumbers")
class PhoneNumbersTest {

    @ParameterizedTest(name = "''{0}''")
    @CsvSource(delimiter = '|', value = {
        "082 000 0101|+27820000101",
        "0820000101|+27820000101",
        "27820000101|+27820000101",
        "+27 82 000 0101|+27820000101",
        "0027 82 000 0101|+27820000101",
        "+27 (0)82 000 0101|+27820000101",
        "082-000-0101|+27820000101",
        "(011) 000.0101|+27110000101",
        "820000101|+27820000101"})
    @DisplayName("every way of writing one number gives the one number")
    void normalises(String raw, String expected) {
        assertThat(PhoneNumbers.normalise(raw)).contains(expected);
    }

    @ParameterizedTest(name = "''{0}''")
    @ValueSource(strings = {"12345", "+44 20 0000 0000", "0920000101", "0020000101", "08200001011", "call me", "", "+27"})
    @DisplayName("PHONE_INVALID: too short, too long, foreign, a trunk digit no South African number has, or not a number")
    void refuses(String raw) {
        assertThat(PhoneNumbers.normalise(raw)).isEmpty();
    }
}

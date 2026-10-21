package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The one place penetration is worked out (rule P, PA-8): Home's figures and the branches' statistic both ask it.
 */
@DisplayName("Penetration -- the paying over the complement")
class PenetrationTest {

    @Test
    @DisplayName("🔒 PA-8, rule P: the paying over the complement on record, to four places half up -- above one shown, "
            + "never capped")
    void payingOverTheComplement() {
        assertThat(Penetration.of(2, 3L)).isEqualByComparingTo("0.6667");
        assertThat(Penetration.of(1, 32L)).as("half up: 0.03125, neither down nor to the even").isEqualByComparingTo("0.0313");
        assertThat(Penetration.of(1, 8L)).isEqualByComparingTo("0.1250");
        assertThat(Penetration.of(9, 8L)).as("more paying than the complement counts").isEqualByComparingTo("1.1250");
        assertThat(Penetration.of(0, 8L)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("PA-8: none where no complement is on record -- none at all, or nought")
    void noneWithoutAComplement() {
        assertThat(Penetration.of(5, null)).isNull();
        assertThat(Penetration.of(5, 0L)).isNull();
    }

    @Test
    @DisplayName("🔒 MEN-26, \"This should be flagged so we can fix it\": more paying than the complement is out of date")
    void moreThanTheComplementIsFlagged() {
        assertThat(Penetration.complementOutOfDate(9, 8L)).as("nine paying, eight on staff").isTrue();
        assertThat(Penetration.complementOutOfDate(8, 8L)).as("every one of them paying: a hundred, not over").isFalse();
        assertThat(Penetration.complementOutOfDate(3, 8L)).isFalse();
    }

    @Test
    @DisplayName("MEN-26: nothing is compared with nothing -- no complement on record is never flagged")
    void noComplementIsNeverFlagged() {
        assertThat(Penetration.complementOutOfDate(5, null)).isFalse();
        assertThat(Penetration.complementOutOfDate(5, 0L)).isFalse();
        assertThat(Penetration.OUT_OF_DATE_NOTE).contains("shown as it is").contains("flagged");
    }
}

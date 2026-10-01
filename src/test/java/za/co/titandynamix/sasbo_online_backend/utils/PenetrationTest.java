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
            + "never capped -- and none where no complement is on record")
    void payingOverTheComplement() {
        assertThat(Penetration.of(2, 3L)).isEqualByComparingTo("0.6667");
        assertThat(Penetration.of(1, 32L)).as("half up: 0.03125, neither down nor to the even").isEqualByComparingTo("0.0313");
        assertThat(Penetration.of(1, 8L)).isEqualByComparingTo("0.1250");
        assertThat(Penetration.of(9, 8L)).as("more paying than the complement counts").isEqualByComparingTo("1.1250");
        assertThat(Penetration.of(0, 8L)).isEqualByComparingTo("0");
        assertThat(Penetration.of(5, null)).as("no complement on record").isNull();
        assertThat(Penetration.of(5, 0L)).as("a complement of nought is none").isNull();
    }
}

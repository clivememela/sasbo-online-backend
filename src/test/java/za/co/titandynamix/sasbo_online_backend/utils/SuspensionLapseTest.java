package za.co.titandynamix.sasbo_online_backend.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stephne, MEN-18: "The system should also remind us when the suspension period for a member have lapsed, so we can
 * delete. (suspensions is only for 3months)". The one place the lapse is decided, and the boundary the list's query
 * asks with, held to agree with it on every day.
 */
@DisplayName("SuspensionLapse -- MEN-18: three calendar months after the day a suspension began")
class SuspensionLapseTest {

    @Test
    @DisplayName("🔒 MEN-18: begun on 2 June, it lapses on 2 September -- on the day, not the day after")
    void threeCalendarMonthsToTheDay() {
        LocalDate began = LocalDate.of(2026, 6, 2);

        assertThat(SuspensionLapse.MONTHS).isEqualTo(3);
        assertThat(SuspensionLapse.lapsesOn(began)).isEqualTo(LocalDate.of(2026, 9, 2));
        assertThat(SuspensionLapse.lapsedBy(began, LocalDate.of(2026, 9, 1))).as("the day before").isFalse();
        assertThat(SuspensionLapse.lapsedBy(began, LocalDate.of(2026, 9, 2))).as("on the day").isTrue();
        assertThat(SuspensionLapse.lapsedBy(began, LocalDate.of(2026, 9, 3))).as("after").isTrue();
        assertThat(SuspensionLapse.lapsedBy(began, began)).as("the day it began").isFalse();
    }

    @Test
    @DisplayName("🔒 MEN-18: a month's end is a calendar's -- 30 November lapses on 28 February, and on the 29th in a leap year")
    void monthEndsAsACalendarCountsThem() {
        assertThat(SuspensionLapse.lapsesOn(LocalDate.of(2026, 11, 30))).isEqualTo(LocalDate.of(2027, 2, 28));
        assertThat(SuspensionLapse.lapsesOn(LocalDate.of(2027, 11, 30))).isEqualTo(LocalDate.of(2028, 2, 29));
        assertThat(SuspensionLapse.lapsesOn(LocalDate.of(2026, 5, 31))).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(SuspensionLapse.lapsesOn(LocalDate.of(2026, 3, 31))).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(SuspensionLapse.lapsedBy(LocalDate.of(2026, 11, 30), LocalDate.of(2027, 2, 28))).isTrue();
        assertThat(SuspensionLapse.lapsedBy(LocalDate.of(2026, 11, 29), LocalDate.of(2027, 2, 28))).isTrue();
        assertThat(SuspensionLapse.lapsedBy(LocalDate.of(2026, 12, 1), LocalDate.of(2027, 2, 28))).isFalse();
    }

    @Test
    @DisplayName("🔒 the list's boundary agrees with the rule on every day of three years, month ends and a leap year included")
    void theQuerysBoundaryAgreesWithTheRule() {
        List<String> disagreements = new ArrayList<>();
        int asked = 0;
        for (LocalDate asAt = LocalDate.of(2026, 1, 1); asAt.isBefore(LocalDate.of(2029, 1, 1)); asAt = asAt.plusDays(1)) {
            LocalDate last = SuspensionLapse.lastStartLapsedBy(asAt);
            ZonedDateTime before = SuspensionLapse.lapsedIfBegunBefore(asAt).atZone(MonthRange.ZONE);
            if (!before.toLocalDate().equals(last.plusDays(1)) || before.getHour() != 0 || before.getMinute() != 0) {
                disagreements.add(asAt + ": the instant " + before + " is not the start of the day after " + last);
            }
            for (LocalDate began = asAt.minusMonths(4); !began.isAfter(asAt); began = began.plusDays(1)) {
                asked++;
                boolean byTheRule = SuspensionLapse.lapsedBy(began, asAt);
                boolean byTheQuery = !began.isAfter(last);
                if (byTheRule != byTheQuery) {
                    disagreements.add(asAt + ", begun " + began + ": the rule says " + byTheRule + ", the query "
                            + byTheQuery);
                }
            }
        }

        assertThat(asked).as("the days compared").isGreaterThan(100_000);
        assertThat(disagreements).as("a suspension the list calls lapsed and the rule does not, or the other way").isEmpty();
    }

    @Test
    @DisplayName("the deck's clock, Wednesday 14 October 2026: begun on 14 July it has lapsed, on 15 July it has not")
    void onTheDecksClock() {
        LocalDate deck = LocalDate.of(2026, 10, 14);

        assertThat(SuspensionLapse.lastStartLapsedBy(deck)).isEqualTo(LocalDate.of(2026, 7, 14));
        assertThat(SuspensionLapse.lapsedIfBegunBefore(deck))
                .isEqualTo(LocalDate.of(2026, 7, 15).atStartOfDay(MonthRange.ZONE).toInstant());
    }
}

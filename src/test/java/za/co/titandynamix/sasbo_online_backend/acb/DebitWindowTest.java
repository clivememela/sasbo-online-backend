package za.co.titandynamix.sasbo_online_backend.acb;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The run-window rule, proved three ways: the specification's worked examples verbatim, the
 * fourteen witness dates each chosen to separate a right rule from a plausible one, and the rule's
 * invariants over every cell of two years in both holiday sets.
 *
 * <h2>Why the examples alone prove nothing</h2>
 * All three of the specification's examples are satisfied by BOTH readings of the rule -- backward
 * first, or forward first -- and the two readings differ on 56 of 288 cells in 2026 and 2027. A
 * suite of examples was green while the client ran the other reading. The witnesses are what
 * separate them: W6, a Saturday 25th, is Friday the 24th under this rule and Monday the 27th under
 * the other.
 *
 * <h2>Proved by deletion</h2>
 * Removing the {@code >= 20} half of the window reddens W4, W5 and W11; making the first move
 * forward reddens W1, W2, W6 and W7; stepping one day instead of iterating reddens W10 and W12.
 * Each was watched, and is recorded in the phase's BUILT line.
 */
@DisplayName("DebitWindow -- the run-window rule")
class DebitWindowTest {

    private static final DebitWindow WEEKENDS = new DebitWindow(HolidayCalendar.WEEKENDS_ONLY);
    private static final DebitWindow STATUTORY = new DebitWindow(AcbCalendars.ZA_STATUTORY);

    @Test
    @DisplayName("ships the specification's reading, and names it the way the fixture publishes it")
    void theDirectionIsTheSpecifications() {
        // 🔴 The contested fact. SASBO's answer of 2026-09-18 says forward; the specification they
        // validated says backward, and so does the client's debitDate.js. Question 33 is with them.
        // If they answer forward, this test and FIRST_MOVE change together -- and W6 below moves.
        assertThat(DebitWindow.FIRST_MOVE).isEqualTo(DebitWindow.Direction.BACKWARD);
        assertThat(DebitWindow.ruleName()).isEqualTo("backward-in-month-forward-below-20th");
    }

    @Nested
    @DisplayName("the specification's own worked examples, verbatim")
    class TheSpecificationsExamples {

        @Test
        @DisplayName("the 31st on a Sunday is processed on Friday the 29th")
        void aSunday31stMovesBackToFriday() {
            // May 2026: Sunday the 31st.
            assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 5), 31)).isEqualTo(LocalDate.of(2026, 5, 29));
        }

        @Test
        @DisplayName("the 30th on a Saturday is processed on Friday the 29th")
        void aSaturday30thMovesBackToFriday() {
            assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 5), 30)).isEqualTo(LocalDate.of(2026, 5, 29));
        }

        @Test
        @DisplayName("the 20th on a Sunday is processed on Monday the 21st -- never below the 20th")
        void aSunday20thMovesForward() {
            // September 2026: Sunday the 20th. Backward would be Friday the 18th, below the window.
            assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 9), 20)).isEqualTo(LocalDate.of(2026, 9, 21));
        }

        @Test
        @DisplayName("a public holiday on a Sunday makes the Monday one, and the debit steps past both")
        void theActsSundayRule() {
            // Human Rights Day 2027 is Sunday 21 March, so Monday the 22nd is a holiday too. A
            // member on the 22nd: backward walks past Sunday and Saturday to Friday the 19th, below
            // the window -- so forward instead, past the Monday holiday, to Tuesday the 23rd.
            assertThat(STATUTORY.actionDate(YearMonth.of(2027, 3), 22)).isEqualTo(LocalDate.of(2027, 3, 23));
        }
    }

    @ParameterizedTest(name = "{0}: {1}-{2} in {3} -> {4}")
    @DisplayName("the fourteen witness dates")
    @CsvSource({
            "W1,  2026-05, 31, either,       2026-05-29",
            "W2,  2026-05, 30, either,       2026-05-29",
            "W3,  2026-09, 20, either,       2026-09-21",
            "W4,  2026-06, 20, either,       2026-06-22",
            "W5,  2026-06, 21, either,       2026-06-22",
            "W6,  2026-07, 25, either,       2026-07-24",
            "W7,  2026-10, 31, either,       2026-10-30",
            "W8,  2026-02, 31, either,       2026-02-27",
            "W9,  2027-02, 31, either,       2027-02-26",
            "W10, 2026-12, 26, za-statutory, 2026-12-24",
            "W11, 2027-03, 22, za-statutory, 2027-03-23",
            "W12, 2027-03, 29, za-statutory, 2027-03-25",
            "W13, 2026-09, 24, za-statutory, 2026-09-23",
            "W14, 2027-12, 27, za-statutory, 2027-12-24",
    })
    void witness(String name, String month, int day, String set, String expected) {
        YearMonth ym = YearMonth.parse(month);
        LocalDate want = LocalDate.parse(expected);
        if (set.equals("either")) {
            assertThat(WEEKENDS.actionDate(ym, day)).as("%s on weekends-only", name).isEqualTo(want);
            assertThat(STATUTORY.actionDate(ym, day)).as("%s on za-statutory", name).isEqualTo(want);
        } else {
            assertThat(STATUTORY.actionDate(ym, day)).as("%s on za-statutory", name).isEqualTo(want);
        }
    }

    @Test
    @DisplayName("W10 and W14 differ by set -- which is why a date computed without a calendar is caveated")
    void theSetsDisagreeWhereTheyShould() {
        // On weekends-only, a member on the 26th of December 2026 is debited on Friday the 25th:
        // Christmas Day. Nothing about that date is wrong by the weekends-only rule, which is the
        // whole reason the run records its calendar and the screen says which one it used.
        assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 12), 26)).isEqualTo(LocalDate.of(2026, 12, 25));
        assertThat(WEEKENDS.actionDate(YearMonth.of(2027, 12), 27)).isEqualTo(LocalDate.of(2027, 12, 27));
    }

    @Test
    @DisplayName("never before the 20th, never after month end, never another month, never a non-business day")
    void theInvariantsHoldOverEveryCellOfTwoYears() {
        SoftAssertions softly = new SoftAssertions();
        int cells = 0;
        for (DebitWindow window : List.of(WEEKENDS, STATUTORY)) {
            for (YearMonth month : AcbCalendars.months()) {
                for (int day = DebitWindow.FIRST_DAY; day <= DebitWindow.LAST_DAY; day++) {
                    LocalDate result = window.actionDate(month, day);
                    String cell = window.holidays().name() + " " + month + " day " + day;
                    softly.assertThat(YearMonth.from(result)).as("%s stays in its month", cell).isEqualTo(month);
                    softly.assertThat(result.getDayOfMonth()).as("%s is not below the 20th", cell)
                            .isGreaterThanOrEqualTo(DebitWindow.FIRST_DAY);
                    softly.assertThat(window.holidays().isBusinessDay(result))
                            .as("%s is a business day", cell).isTrue();
                    LocalDate asked = month.atDay(Math.min(day, month.lengthOfMonth()));
                    if (window.holidays().isBusinessDay(asked)) {
                        softly.assertThat(result).as("%s: a business day is left alone", cell).isEqualTo(asked);
                    }
                    cells++;
                }
            }
        }
        softly.assertAll();
        // Non-vacuity: two sets x 24 months x 12 days. Derived, not typed: a loop that ran over
        // nothing would pass every assertion above.
        assertThat(cells).isEqualTo(2 * AcbCalendars.months().size() * 12).isEqualTo(576);
    }

    @Test
    @DisplayName("a day outside the window is refused, not bent into it")
    void aDayOutsideTheWindowIsRefused() {
        assertThatThrownBy(() -> WEEKENDS.actionDate(YearMonth.of(2026, 10), 19))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WEEKENDS.actionDate(YearMonth.of(2026, 10), 32))
                .isInstanceOf(IllegalArgumentException.class);
        // The must-pass twin: both edges of the window are accepted.
        assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 10), 20)).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(WEEKENDS.actionDate(YearMonth.of(2026, 10), 31)).isEqualTo(LocalDate.of(2026, 10, 30));
    }

    @Test
    @DisplayName("a calendar with no business day in reach is an error, not a hang")
    void aBrokenCalendarFailsLoudly() {
        // Every day of a fortnight declared a holiday: a data mistake, and it must say so.
        List<LocalDate> fortnight = LocalDate.of(2026, 10, 14).datesUntil(LocalDate.of(2026, 11, 3)).toList();
        DebitWindow broken = new DebitWindow(HolidayCalendar.of("broken", LocalDate.of(2026, 12, 31), fortnight));
        assertThatThrownBy(() -> broken.actionDate(YearMonth.of(2026, 10), 25))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("the weekends-only calendar says it knows no holidays, and a listed one says until when")
    void calendarsAreHonestAboutWhatTheyKnow() {
        assertThat(HolidayCalendar.WEEKENDS_ONLY.holidaysKnown()).isFalse();
        assertThat(HolidayCalendar.WEEKENDS_ONLY.knownUntil()).isEmpty();
        assertThat(AcbCalendars.ZA_STATUTORY.holidaysKnown()).isTrue();
        assertThat(AcbCalendars.ZA_STATUTORY.knownUntil()).contains(LocalDate.of(2027, 12, 31));
        assertThatThrownBy(() -> HolidayCalendar.of("dishonest", null, List.of(LocalDate.of(2026, 1, 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the statutory list is the Act's: every Sunday holiday brings its Monday")
    void theStatutoryListObeysTheSundayRule() {
        Map<LocalDate, Boolean> listed = new java.util.HashMap<>();
        AcbCalendars.ZA_STATUTORY_2026_2027.forEach(d -> listed.put(d, true));
        for (LocalDate holiday : AcbCalendars.ZA_STATUTORY_2026_2027) {
            if (holiday.getDayOfWeek() == DayOfWeek.SUNDAY) {
                assertThat(listed).as("%s is a Sunday holiday, so the Monday is one too", holiday)
                        .containsKey(holiday.plusDays(1));
            }
        }
        assertThat(AcbCalendars.ZA_STATUTORY_2026_2027).hasSize(27).doesNotHaveDuplicates();
    }
}

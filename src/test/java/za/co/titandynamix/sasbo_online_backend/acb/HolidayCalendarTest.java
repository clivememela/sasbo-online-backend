package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a run depends on of the calendar: whether it reaches the month at all ({@code covers}, D30),
 * and what it says of the month ({@code fingerprint}, SD3-F).
 */
@DisplayName("HolidayCalendar -- how far it reaches, and what it says of a month")
class HolidayCalendarTest {

    private static final HolidayCalendar STATUTORY = AcbCalendars.ZA_STATUTORY;

    @Test
    @DisplayName("V47's calendar covers every month to December 2027 and none after it")
    void coversToItsKnownUntil() {
        assertThat(STATUTORY.covers(YearMonth.of(2026, 1))).isTrue();
        assertThat(STATUTORY.covers(YearMonth.of(2027, 12))).as("the last month it knows, whole").isTrue();
        assertThat(STATUTORY.covers(YearMonth.of(2028, 1))).isFalse();
    }

    @Test
    @DisplayName("a calendar known to mid-month does not cover that month -- the month's end is what counts")
    void aMonthIsCoveredWhole() {
        HolidayCalendar toTheFifteenth = HolidayCalendar.of("za-statutory", LocalDate.of(2026, 10, 15), List.of());
        assertThat(toTheFifteenth.covers(YearMonth.of(2026, 9))).isTrue();
        assertThat(toTheFifteenth.covers(YearMonth.of(2026, 10))).isFalse();
    }

    @Test
    @DisplayName("weekends-only covers nothing: a month is never prepared on weekends alone")
    void weekendsOnlyCoversNothing() {
        assertThat(HolidayCalendar.WEEKENDS_ONLY.covers(YearMonth.of(2026, 10))).isFalse();
    }

    @Test
    @DisplayName("the fingerprint is the calendar's name and the month's own holidays, in date order")
    void theFingerprint() {
        assertThat(STATUTORY.fingerprint(YearMonth.of(2026, 12)))
                .isEqualTo("za-statutory|2026-12|2026-12-16,2026-12-25,2026-12-26");
        assertThat(STATUTORY.fingerprint(YearMonth.of(2026, 10))).as("a month with none")
                .isEqualTo("za-statutory|2026-10|");
    }

    @Test
    @DisplayName("a holiday declared inside the month changes it; one outside the month does not")
    void onlyTheMonthsOwnHolidaysCount() {
        String october = STATUTORY.fingerprint(YearMonth.of(2026, 10));
        assertThat(withDeclared(LocalDate.of(2026, 10, 28)).fingerprint(YearMonth.of(2026, 10))).isNotEqualTo(october);
        assertThat(withDeclared(LocalDate.of(2026, 11, 4)).fingerprint(YearMonth.of(2026, 10)))
                .as("the must-pass twin").isEqualTo(october);
    }

    private static HolidayCalendar withDeclared(LocalDate declared) {
        List<LocalDate> dates = new ArrayList<>(STATUTORY.dates());
        dates.add(declared);
        return HolidayCalendar.of(STATUTORY.name(), STATUTORY.knownUntil().orElseThrow(), dates);
    }
}

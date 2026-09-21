package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 SD3-G: from sixty days before the calendar's known-until, the screen says when it ends -- so
 * {@code HOLIDAYS_NOT_KNOWN} is never first met inside December's three-day window. 🧪 T2-G: today
 * is an argument, so every edge is asserted with no clock.
 */
@DisplayName("The calendar's sixty-day notice")
class AcbCalendarNoticeTest {

    private static final LocalDate KNOWN_UNTIL = LocalDate.of(2027, 12, 31);

    @Test
    @DisplayName("sixty days before known-until -- 1 November 2027 -- the notice is due; the day before, it is not")
    void theEdge() {
        assertThat(AcbCalendarServiceImpl.endingSoon(KNOWN_UNTIL, LocalDate.of(2027, 11, 1))).isTrue();
        assertThat(AcbCalendarServiceImpl.endingSoon(KNOWN_UNTIL, LocalDate.of(2027, 10, 31))).isFalse();
    }

    @Test
    @DisplayName("December, when January is prepared, is inside it; so is every day after known-until")
    void decemberAndAfter() {
        assertThat(AcbCalendarServiceImpl.endingSoon(KNOWN_UNTIL, LocalDate.of(2027, 12, 29))).isTrue();
        assertThat(AcbCalendarServiceImpl.endingSoon(KNOWN_UNTIL, LocalDate.of(2028, 1, 3))).isTrue();
    }

    @Test
    @DisplayName("no calendar on record is always worth saying")
    void noCalendar() {
        assertThat(AcbCalendarServiceImpl.endingSoon(null, LocalDate.of(2026, 9, 21))).isTrue();
    }

    @Test
    @DisplayName("the must-pass twin: today, fifteen months out, says nothing")
    void farAway() {
        assertThat(AcbCalendarServiceImpl.endingSoon(KNOWN_UNTIL, LocalDate.of(2026, 9, 21))).isFalse();
    }
}

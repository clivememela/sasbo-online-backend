package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Months and dates are Johannesburg's, whatever zone the server runs in.
 *
 * <p>Render runs in UTC. The case that separates a right answer from a plausible one is the first
 * hour of a month: at 00:30 in Johannesburg on the 1st of October it is still 22:30 on the 30th of
 * September in UTC. A run computed in the server's zone would be prepared for the wrong month.
 */
@DisplayName("AcbTime -- the zone, named once")
class AcbTimeTest {

    @Test
    @DisplayName("00:30 on the 1st in Johannesburg is the new month, although UTC is still in the old one")
    void theFirstHourOfAMonthIsThatMonth() {
        Clock utcClockJustBeforeUtcMidnight = Clock.fixed(Instant.parse("2026-09-30T22:30:00Z"), ZoneOffset.UTC);

        assertThat(AcbTime.currentMonth(utcClockJustBeforeUtcMidnight)).isEqualTo(YearMonth.of(2026, 10));
        assertThat(AcbTime.today(utcClockJustBeforeUtcMidnight)).isEqualTo(LocalDate.of(2026, 10, 1));
        // The contrast that makes the test mean something: the clock's own zone disagrees.
        assertThat(LocalDate.ofInstant(utcClockJustBeforeUtcMidnight.instant(), ZoneOffset.UTC))
                .isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("23:30 on the last day of the month in Johannesburg is still that month")
    void theLastHalfHourOfAMonthIsThatMonth() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-30T21:30:00Z"), ZoneOffset.UTC);
        assertThat(AcbTime.currentMonth(clock)).isEqualTo(YearMonth.of(2026, 9));
        assertThat(AcbTime.today(clock)).isEqualTo(LocalDate.of(2026, 9, 30));
    }

    @Test
    @DisplayName("an instant is written in ISO-8601 with Johannesburg's offset")
    void instantsCarryTheOffset() {
        assertThat(AcbTime.iso(Instant.parse("2026-09-18T21:30:00Z"))).isEqualTo("2026-09-18T23:30+02:00");
        assertThat(AcbTime.iso(LocalDate.of(2026, 10, 20))).isEqualTo("2026-10-20");
        assertThat(AcbTime.iso(YearMonth.of(2026, 10))).isEqualTo("2026-10");
        assertThat(AcbTime.iso((Instant) null)).isNull();
    }
}

package za.co.titandynamix.sasbo_online_backend.acb;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * The one place ACB says what time it is, and in which zone.
 *
 * <h2>Why the zone is named here and nowhere else</h2>
 * Render's containers run in UTC. A run prepared at 00:30 on the 1st of October in Johannesburg is
 * still the 30th of September in UTC, so a month boundary computed in the server's zone prepares
 * the wrong month -- and a Saturday rule evaluated in the wrong zone moves the wrong day. Every
 * month and every date ACB derives goes through {@link #ZONE}, and the zone is written once.
 *
 * <h2>Why every method takes a {@link Clock}</h2>
 * Time is injected, never read. A rule that reads the wall clock changes its answer between two
 * runs of the same test, and the day it is wrong is a month end nobody is watching. So nothing in
 * ACB asks the system for the time: it asks the {@code Clock} it was given, which production wires
 * to the system clock in this zone and every test freezes. A source-scan guard holds ACB to it.
 */
public final class AcbTime {

    /** South African Standard Time. No daylight saving, so a local date-time is never ambiguous. */
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    private AcbTime() {
    }

    /** Today's date in Johannesburg, whatever zone the server runs in. */
    public static LocalDate today(Clock clock) {
        return clock.instant().atZone(ZONE).toLocalDate();
    }

    /** This month in Johannesburg -- the month an administrator means by "this month". */
    public static YearMonth currentMonth(Clock clock) {
        return YearMonth.from(today(clock));
    }

    /** The instant the clock reads. The one way ACB asks for the present moment. */
    public static Instant instant(Clock clock) {
        return clock.instant();
    }

    /** An instant as ISO-8601 with Johannesburg's offset, {@code 2026-09-18T23:30:00+02:00}. */
    public static String iso(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime().toString();
    }

    /** A date as ISO-8601, {@code 2026-09-18}. */
    public static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** A month as ISO-8601, {@code 2026-09}. */
    public static String iso(YearMonth month) {
        return month == null ? null : month.toString();
    }
}

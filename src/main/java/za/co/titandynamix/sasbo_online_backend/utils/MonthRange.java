package za.co.titandynamix.sasbo_online_backend.utils;

import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A month as the figures read it (rule M): a {@code YearMonth} in {@code Africa/Johannesburg}, from the one injected
 * clock -- and the ONE place a month becomes a range. Every month predicate of the arc goes through here, so both
 * sides of midnight on the first and the last day are decided once (MV-2).
 *
 * <p>A range is half-open: from the first instant of the month, up to and NOT including the first instant of the
 * next. The dates are the same: from the first, up to and not including the first of the next month.
 */
public record MonthRange(YearMonth month, Instant start, Instant next, LocalDate firstDay, LocalDate firstDayOfNext,
                         LocalDate firstDayOfYear) {

    /** SASBO's zone: the month is South Africa's, whatever the host's clock says (PA-9). */
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");

    /** What a month a request names looks like: four digits, a hyphen, two. Anything else is not read. */
    private static final Pattern WRITTEN = Pattern.compile("\\d{4}-\\d{2}");

    /** The month {@code month}, as a range in SASBO's zone. */
    public static MonthRange of(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate next = month.plusMonths(1).atDay(1);
        return new MonthRange(month, first.atStartOfDay(ZONE).toInstant(), next.atStartOfDay(ZONE).toInstant(), first,
                next, LocalDate.of(month.getYear(), 1, 1));
    }

    /** The month the clock is in, in SASBO's zone -- never the browser's, never the host's (MD55). */
    public static YearMonth current(Clock clock) {
        return YearMonth.now(clock.withZone(ZONE));
    }

    /**
     * The month a request names, or the clock's where it names none. A month that cannot be read is
     * {@code SUMMARY_MONTH_INVALID}, naming the field and giving nothing of it back (ME-14); one after the clock's is
     * {@code SUMMARY_MONTH_IN_FUTURE}.
     */
    public static YearMonth asked(String month, Clock clock) {
        YearMonth now = current(clock);
        if (month == null || month.isBlank()) {
            return now;
        }
        YearMonth asked;
        try {
            if (!WRITTEN.matcher(month.trim()).matches()) {
                throw new DateTimeParseException("not a month", month, 0);
            }
            asked = YearMonth.parse(month.trim());
        } catch (DateTimeParseException unreadable) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_MONTH_INVALID,
                    "That month could not be read. Name it as year and month -- 2026-09.",
                    Map.of("field", "month"));
        }
        if (asked.isAfter(now)) {
            throw new MonthEndRefusal(MonthEndRefusalCode.SUMMARY_MONTH_IN_FUTURE,
                    "That month has not happened yet.", MonthEndRefusal.details("month", asked.toString(),
                            "current", now.toString()));
        }
        return asked;
    }

    /** The first instant of the month's year, in SASBO's zone: where the year to date begins (rule Y). */
    public Instant yearStart() {
        return firstDayOfYear.atStartOfDay(ZONE).toInstant();
    }

    /** Whether {@code instant} falls in this month -- half-open, in SASBO's zone. */
    public boolean contains(Instant instant) {
        return !instant.isBefore(start) && instant.isBefore(next);
    }

    /** Whether {@code date} falls in this month. */
    public boolean contains(LocalDate date) {
        return !date.isBefore(firstDay) && date.isBefore(firstDayOfNext);
    }
}

package za.co.titandynamix.sasbo_online_backend.payroll;

import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** What a payroll request names, read and held to its limits -- a month, a page, a filter word, a day. */
public final class PayrollRequests {

    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern DAY = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private PayrollRequests() {
    }

    /** A month as {@code 2026-09}, not after the clock's own in SASBO's zone. */
    public static YearMonth period(String written, Clock clock) {
        YearMonth month;
        try {
            if (written == null || !MONTH.matcher(written.trim()).matches()) {
                throw new DateTimeParseException("not a month", String.valueOf(written), 0);
            }
            month = YearMonth.parse(written.trim());
        } catch (DateTimeParseException unreadable) {
            throw new PayrollRefusal(PayrollRefusalCode.PERIOD_INVALID, "That month could not be read. Name it as "
                    + "year and month, for example 2026-09.", Map.of("field", "period"));
        }
        YearMonth now = MonthRange.current(clock);
        if (month.isAfter(now)) {
            throw new PayrollRefusal(PayrollRefusalCode.PERIOD_IN_FUTURE, "That month has not begun yet.",
                    PayrollRefusal.details("period", month.toString(), "current", now.toString()));
        }
        return month;
    }

    /** A month a list is filtered by: {@code null} for none; one that cannot be read is {@code PERIOD_INVALID}. */
    public static LocalDate periodFilter(String written, Clock clock) {
        return written == null || written.isBlank() ? null : period(written, clock).atDay(1);
    }

    /** A day as {@code 2026-09-30}, not after today in SASBO's zone. */
    public static LocalDate day(String written, Clock clock, String field) {
        LocalDate day;
        try {
            if (written == null || !DAY.matcher(written.trim()).matches()) {
                throw new DateTimeParseException("not a day", String.valueOf(written), 0);
            }
            day = LocalDate.parse(written.trim());
        } catch (DateTimeParseException unreadable) {
            throw new PayrollRefusal(PayrollRefusalCode.PAYMENT_INVALID, "That day could not be read. Write it as "
                    + "year, month and day, for example 2026-09-30.", Map.of("field", field));
        }
        if (day.isAfter(LocalDate.now(clock.withZone(MonthRange.ZONE)))) {
            throw new PayrollRefusal(PayrollRefusalCode.PAYMENT_INVALID, "That day has not come yet.",
                    Map.of("field", field));
        }
        return day;
    }

    /** A page and its size: page from 0, size from 1 to {@value PayrollLimits#PAGE_MAX_SIZE}, 20 by default. */
    public static int[] page(Integer page, Integer size) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_PAGE_SIZE : size;
        if (p < 0 || p > 1_000_000 || s < 1 || s > PayrollLimits.PAGE_MAX_SIZE) {
            throw new PayrollRefusal(PayrollRefusalCode.PAGE_INVALID, "That page does not exist. Pages start at 0 "
                    + "and hold 1 to " + PayrollLimits.PAGE_MAX_SIZE + " rows.",
                    PayrollRefusal.details("page", String.valueOf(p), "size", String.valueOf(s),
                            "maxSize", String.valueOf(PayrollLimits.PAGE_MAX_SIZE)));
        }
        return new int[] {p, s};
    }

    /** A word of an enum a filter takes; {@code null} for none; anything else {@code FILTER_UNKNOWN}. */
    public static <E extends Enum<E>> E word(String written, Class<E> type, String field) {
        if (written == null || written.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, written.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new PayrollRefusal(PayrollRefusalCode.FILTER_UNKNOWN, "That is not one of the words this list is "
                    + "filtered by.", Map.of("field", field, "accepted", String.join(", ",
                    Arrays.stream(type.getEnumConstants()).map(Enum::name).toList())));
        }
    }
}

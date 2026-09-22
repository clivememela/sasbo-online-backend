package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * The holiday calendar and one year of it (D30).
 *
 * <p>The list is bounded by the calendar itself -- a year has a dozen or so public holidays -- and by
 * the one year asked for.
 *
 * @param name        the calendar every run stores, {@code za-statutory}; {@code none} when the
 *                    database holds no calendar, which prepares no month
 * @param knownUntil  the last date the calendar is complete for; a month ending after it is refused
 *                    {@code HOLIDAYS_NOT_KNOWN}. Null when no calendar is on record
 * @param endingSoon  true from sixty days before {@code knownUntil}, so the screen says when the
 *                    calendar ends before a month is refused inside December's window (SD3-G)
 * @param year        the year {@code holidays} covers
 */
public record AcbCalendarDto(
        String name,
        String knownUntil,
        boolean endingSoon,
        int year,
        List<AcbHolidayDto> holidays) {
}

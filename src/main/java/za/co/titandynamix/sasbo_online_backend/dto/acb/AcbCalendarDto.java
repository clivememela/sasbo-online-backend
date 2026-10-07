package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * The non-working days calendar and one year of it (D30; Stephne, DOD-17 and DOD-18).
 *
 * <p>The list is bounded by the calendar itself -- a year has a dozen or so public holidays and the few days a bank
 * tells SASBO it is closed -- and by the one year asked for.
 *
 * @param name              the calendar every run stores, {@code za-statutory}; {@code none} when the
 *                          database holds no calendar, which prepares no month
 * @param knownUntil        the last date the calendar is complete for; a month ending after it is refused
 *                          {@code HOLIDAYS_NOT_KNOWN}. Null when no calendar is on record
 * @param endingSoon        true from sixty days before {@code knownUntil}, so the screen says when the
 *                          calendar ends before a month is refused inside December's window (SD3-G)
 * @param year              the year {@code holidays} covers
 * @param bankClosuresCount whether a day the bank is closed moves the debits on it, as a public holiday does --
 *                          DOD-17's open choice (Friday item 14), on by default
 */
public record AcbCalendarDto(
        String name,
        String knownUntil,
        boolean endingSoon,
        int year,
        boolean bankClosuresCount,
        List<AcbHolidayDto> holidays) {

    /** A calendar that counts bank closures, as it does by default. */
    public AcbCalendarDto(String name, String knownUntil, boolean endingSoon, int year, List<AcbHolidayDto> holidays) {
        this(name, knownUntil, endingSoon, year, true, holidays);
    }
}

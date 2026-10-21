package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbCalendarDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHolidayDto;

/**
 * The holiday calendar as data (D30): read by anyone, and changed -- a day added, a day removed, its
 * known-until moved -- only by an administrator, once the API knows who is calling (B1). Every change
 * leaves its audit row in its own transaction (SD4-A, R1-24).
 */
public interface AcbCalendarService {

    /** The calendar and one year's holidays -- this year's when {@code year} is null. */
    AcbCalendarDto calendar(Integer year);

    /** Adds a declared public holiday. {@code HOLIDAY_ALREADY_LISTED} when the day is listed already. */
    default AcbHolidayDto add(String date, String name, String actor) {
        return add(date, name, za.co.titandynamix.sasbo_online_backend.enums.AcbHolidayKind.PUBLIC_HOLIDAY, actor);
    }

    /**
     * Adds a non-working day: a declared public holiday, or a day the bank is closed (DOD-17, DOD-18).
     * {@code HOLIDAY_ALREADY_LISTED} when the day is listed already.
     */
    AcbHolidayDto add(String date, String name, za.co.titandynamix.sasbo_online_backend.enums.AcbHolidayKind kind,
                      String actor);

    /** Whether a bank closure moves the debits on it -- DOD-17's open choice (Friday item 14). Audited. */
    AcbCalendarDto setting(boolean bankClosuresCount, String actor);

    /** Removes a day an administrator added. {@code HOLIDAY_NOT_REMOVABLE} for any other day. */
    AcbCalendarDto remove(String date, String actor);

    /** Moves the calendar's known-until later -- never earlier. */
    AcbCalendarDto extend(String knownUntil, String actor);
}

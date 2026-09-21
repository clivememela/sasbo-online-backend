package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Who put a day in the holiday calendar -- the twin of V47's {@code acb_holidays_source_known} CHECK.
 */
public enum AcbHolidaySource {
    /** From the Public Holidays Act, by migration. Never removed through the application. */
    STATUTORY,
    /** Declared ad hoc -- an election, a day of mourning -- and added by an administrator. */
    ADDED
}

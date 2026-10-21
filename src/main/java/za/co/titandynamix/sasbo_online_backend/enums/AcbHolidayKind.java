package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What kind of non-working day a day in the calendar is -- the twin of V70's {@code acb_holidays_kind_known} CHECK.
 *
 * <p>Stephne, DOD-17: "Public holidays and any other day the bank is closed"; DOD-18: the developers keep the list.
 * Whether a bank closure moves the debits on it is the calendar's one setting, because she also ticked "South African
 * public holidays only" (Friday item 14).
 */
public enum AcbHolidayKind {
    /** A South African public holiday, statutory or declared. */
    PUBLIC_HOLIDAY,
    /** A day the bank is closed that is not a public holiday, added when SASBO tells us of one. */
    BANK_CLOSED
}

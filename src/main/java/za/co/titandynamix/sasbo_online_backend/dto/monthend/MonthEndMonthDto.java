package za.co.titandynamix.sasbo_online_backend.dto.monthend;

/**
 * One month's record (month end 04.2): taken -- when, and by whom -- or open, the clock's month, answered live. Dates
 * are ISO text (MD16).
 *
 * @param month   the month, {@code 2026-08}
 * @param state   {@value #TAKEN} or {@value #OPEN}
 * @param takenAt the instant it was taken, ISO-8601; {@code null} while open
 * @param takenBy who took it -- {@code seed} for the demonstration's months; {@code null} while open
 */
public record MonthEndMonthDto(String month, String state, String takenAt, String takenBy) {

    /** The month is frozen: its statistics are read from what was taken. */
    public static final String TAKEN = "TAKEN";

    /** The clock's month, not taken: its statistics are worked out as they are asked for. */
    public static final String OPEN = "OPEN";
}

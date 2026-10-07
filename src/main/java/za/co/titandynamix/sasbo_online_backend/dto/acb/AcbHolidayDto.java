package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * One day in the non-working days calendar.
 *
 * @param date   ISO-8601, {@code 2026-12-16}
 * @param source {@code STATUTORY} (the Act's, by migration) or {@code ADDED} (by an administrator) -- only an added
 *               day can be removed
 * @param kind   {@code PUBLIC_HOLIDAY} or {@code BANK_CLOSED} (DOD-17)
 */
public record AcbHolidayDto(String date, String name, String source, String kind) {

    /** A public holiday. */
    public AcbHolidayDto(String date, String name, String source) {
        this(date, name, source, "PUBLIC_HOLIDAY");
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * One day in the holiday calendar.
 *
 * @param date   ISO-8601, {@code 2026-12-16}
 * @param source {@code STATUTORY} (the Act's, by migration) or {@code ADDED} (declared, by an
 *               administrator) -- only an added day can be removed
 */
public record AcbHolidayDto(String date, String name, String source) {
}

package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.AcbHolidayKind;

/**
 * A day added to the non-working days list (DOD-17, DOD-18): a declared public holiday, or a day the bank is closed.
 *
 * @param date YYYY-MM-DD
 * @param kind {@code PUBLIC_HOLIDAY} (the default) or {@code BANK_CLOSED}
 */
public record HolidayRequest(@NotBlank String date, @NotBlank @Size(max = 100) String name, AcbHolidayKind kind) {
}

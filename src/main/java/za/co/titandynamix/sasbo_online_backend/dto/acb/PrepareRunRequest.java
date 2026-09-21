package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;

/**
 * Prepare the run for one month.
 *
 * @param actionMonth {@code YYYY-MM}. A string, parsed by the service, so a malformed month answers
 *                    {@code ACTION_MONTH_INVALID} rather than a deserialisation error. Field-level
 *                    constraints only: the validation handler cannot take a class-level one.
 */
public record PrepareRunRequest(@NotBlank String actionMonth) {
}

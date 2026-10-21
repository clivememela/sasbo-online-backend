package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Next year's flat rate, entered ahead of its month (Q43: captured in December for January).
 *
 * @param effectiveFrom the first day of a month still to come, YYYY-MM-DD
 * @param amount        rand and cents, positive
 * @param source        where it came from, at most 255 characters -- the Management Committee's decision, the letter
 */
public record RateRequest(@NotBlank String effectiveFrom, @NotNull BigDecimal amount,
                          @NotBlank @Size(max = 255) String source) {
}

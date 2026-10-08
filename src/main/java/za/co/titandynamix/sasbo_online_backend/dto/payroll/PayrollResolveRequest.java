package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Resolve a discrepancy: the outcome (one that fits its kind), a note saying what was done, and the discrepancy's
 * version as the screen read it -- two administrators cannot both resolve it.
 */
public record PayrollResolveRequest(
        @NotBlank @Size(max = 30) String outcome,
        @NotBlank @Size(max = 1000) String note,
        @NotNull Integer version) {
}

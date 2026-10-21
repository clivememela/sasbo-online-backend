package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * An employer's settings an administrator may change: the fee it keeps when it pays over (PAY-27; whether Absa's 3%
 * applies to every deduction is still to be confirmed, so it is data), and which heading is which field on its file.
 */
public record PayrollSettingsRequest(
        @NotBlank @Size(max = 6) String adminFeePercent,
        @Size(max = 8) Map<String, String> columnMapping,
        @NotNull Integer version) {
}

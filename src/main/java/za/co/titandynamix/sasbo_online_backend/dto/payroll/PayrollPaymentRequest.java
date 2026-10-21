package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Money an employer paid over for a month: the amount as a decimal string, the day it arrived, a reference. */
public record PayrollPaymentRequest(
        @NotNull Long employerId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String period,
        @NotBlank @Size(max = 16) String amount,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String receivedOn,
        @Size(max = 60) String reference) {
}

package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A note on a discrepancy: what was done or learnt (PAY-20). 🔒 Never logged. */
public record PayrollNoteRequest(@NotBlank @Size(max = 1000) String text) {
}

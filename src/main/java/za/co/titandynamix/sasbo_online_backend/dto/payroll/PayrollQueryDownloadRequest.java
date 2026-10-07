package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Download an employer's query list, protected by a password the administrator chooses -- the list leaves SASBO for
 * the employer (POPIA). 🔒 The password is used once, to protect the file, and is never stored, logged or echoed.
 */
public record PayrollQueryDownloadRequest(
        @NotBlank @Size(max = 10) String format,
        @NotNull @Size(min = 8, max = 64) String password) {

    @Override
    public String toString() {
        return "PayrollQueryDownloadRequest[format=" + format + ", password withheld]";
    }
}

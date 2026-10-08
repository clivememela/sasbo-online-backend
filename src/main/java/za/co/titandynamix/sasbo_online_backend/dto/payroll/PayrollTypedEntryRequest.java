package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Rows typed in by hand from a file the system cannot read -- GBS's PDF, SA Reserve Bank's picture (PAY-03). Each row
 * needs an amount and at least one of a staff number, an ID number and a surname (checked again by the service, which
 * names the row).
 */
public record PayrollTypedEntryRequest(
        @NotNull Long employerId,
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}") String period,
        @Size(max = 16) String statedTotal,
        @NotNull @Size(min = 1, max = 5000) @Valid List<Row> rows) {

    /** One typed row. 🔒 Personal data: never logged; no generated text of it leaves this record. */
    public record Row(
            @Size(max = 40) String staffNumber,
            @Size(max = 20) String idNumber,
            @Size(max = 20) String initials,
            @Size(max = 100) String surname,
            @NotBlank @Size(max = 16) String amount) {

        @Override
        public String toString() {
            return "Row[personal data withheld]";
        }
    }
}

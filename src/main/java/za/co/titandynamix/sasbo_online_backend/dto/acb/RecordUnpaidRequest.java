package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

/**
 * An unpaid debit, as an administrator records it from the bank's returns report.
 *
 * @param code     the bank's numeric code as the report prints it: {@code 2}, {@code 4}, {@code 30}
 * @param notedOn  the day the return arrived, {@code YYYY-MM-DD} -- a string, like every ACB date
 * @param decision present it again, or suspend -- SASBO's call per unpaid; may be left undecided
 *                 and made later, and is then counted as undecided on the run
 */
public record RecordUnpaidRequest(
        @NotBlank @Size(max = 10) String code,
        @NotBlank String notedOn,
        UnpaidDecision decision) {
}

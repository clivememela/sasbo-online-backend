package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.util.List;

/**
 * One debit day's returns report, typed in (Stephne, DOD-05, DOD-06, DOD-12): every unpaid on it, each naming its
 * line by id or by the file's sequence number -- the number after the slash of the report's trace number.
 *
 * @param actionDate the debit day the report is for, YYYY-MM-DD
 * @param reportDate the date on the bank's report, YYYY-MM-DD, if given
 * @param entries    one to a thousand
 */
public record ReturnsBatchRequest(@NotBlank String actionDate, String reportDate,
                                  @NotNull @Size(min = 1, max = 1000) @Valid List<Entry> entries) {

    /**
     * One unpaid on the report.
     *
     * @param lineId       the line, or null when the sequence number names it
     * @param fileSequence the file's sequence number, or null when the line id names it
     * @param code         the bank's code as the report prints it
     * @param decision     present it again or suspend; null to decide later
     */
    public record Entry(Long lineId, Integer fileSequence, @NotBlank @Size(max = 10) String code,
                        UnpaidDecision decision) {
    }
}

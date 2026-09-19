package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

/**
 * How many members one reason kept out of a run.
 *
 * @param listed  whether those members are listed by name ({@code GET …/exclusions}) or only counted
 * @param detail  for a counted reason, the raw value that excluded them -- {@code Payroll Deduction},
 *                {@code Suspended}; {@code null} for a listed reason, whose details are per member
 */
public record AcbExclusionSummaryDto(
        AcbExclusionReason reason,
        boolean listed,
        String detail,
        int memberCount) {
}

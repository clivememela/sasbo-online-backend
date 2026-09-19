package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

/**
 * One member a run could not debit, with enough to find them and fix what excluded them.
 *
 * <p>The member's register fields only -- membership number and name. 🔒 Never a banking field: an
 * exclusion for an account number's shape says the problem ({@code 12 digits}), not the number.
 */
public record AcbExclusionDto(
        Integer memberId,
        String membershipNumber,
        String surname,
        String initials,
        AcbExclusionReason reason,
        String detail) {
}

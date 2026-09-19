package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.math.BigDecimal;

/**
 * One line of a run's sheet.
 *
 * <p>🔒 The account shows its LAST FOUR DIGITS only. The sheet needs to tell two accounts apart,
 * not to reproduce them, and this API still answers anyone who can reach it (register B1). The full
 * number exists in exactly two places: the line in the database and the bureau file.
 *
 * @param rePresentsLineId the earlier unpaid this line presents again, or {@code null}
 * @param accountType      1 cheque, 2 savings, 3 transmission; {@code null} until the member record
 *                         carries one -- and the bureau file refuses to render while any line lacks it
 */
public record AcbRunLineDto(
        Long id,
        Integer memberId,
        String membershipNumber,
        String surname,
        String initials,
        String bankName,
        String branchCode,
        String accountLastFour,
        Integer accountType,
        Integer debitDay,
        String actionDate,
        BigDecimal amount,
        AcbLineOutcome outcome,
        Long rePresentsLineId,
        Integer fileSequence,
        String unpaidCode,
        String unpaidReason,
        UnpaidDecision decision) {
}

package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebitWithdrawal;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;

import java.math.BigDecimal;

/**
 * One line of a run's sheet.
 *
 * <p>🔒 The account shows its LAST FOUR DIGITS only. The sheet needs to tell two accounts apart,
 * not to reproduce them, and this API still answers anyone who can reach it (register B1). The full
 * number exists in exactly two places: the line in the database and the bureau file.
 *
 * @param rePresentsLineId the earlier unpaid this line presents again, or {@code null}
 * @param accountType      1 cheque, 2 savings, 3 transmission, from the member's account type (00.5);
 *                         {@code null} only on a line prepared before V49 -- and the bureau file refuses
 *                         to render while any line lacks it
 * @param suggestedDecision for an undecided unpaid, what its code suggests -- present again where the
 *                         code allows a re-debit, suspend where it does not, nothing where unknown
 *                         (Q40, Q44); the administrator decides
 * @param permissionNote   the member's permission for a further re-debit in one run (D32), if given
 * @param letterStatus     where the unpaid's letter is -- {@code null} for a line with no letter
 * @param reDebit          for an unpaid, whether its bank code may be re-debited -- YES, NO or UNKNOWN (Stephne,
 *                         DOD-03, DOD-04); null for a line with no unpaid
 * @param reDebitSource    who said so, in words
 * @param suspensionCode   SASBO's suspension code the bank's code records (her sheet, DOD-01); null when unknown
 * @param codeProvisional  the bank code's row is provisional -- something about it SASBO has not settled
 * @param memberUnpaidCount on the unpaids list, the member's unpaid count now (three is the limit, DOD-03)
 * @param reDebitWithdrawn why a re-debit decided on will never be presented -- the member came back or rejoined
 *                         (DOD-26, DOD-27); null otherwise
 * @param returnsBatchId   the debit day's returns report that recorded the unpaid; null when recorded by line
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
        UnpaidDecision decision,
        UnpaidDecision suggestedDecision,
        String permissionNote,
        LetterStatus letterStatus,
        ReDebit reDebit,
        String reDebitSource,
        Integer suspensionCode,
        boolean codeProvisional,
        Integer memberUnpaidCount,
        ReDebitWithdrawal reDebitWithdrawn,
        Long returnsBatchId) {

    /** A line as it was before Stephne's answers: no code rule, no count, nothing withdrawn. */
    public AcbRunLineDto(Long id, Integer memberId, String membershipNumber, String surname, String initials,
                         String bankName, String branchCode, String accountLastFour, Integer accountType,
                         Integer debitDay, String actionDate, BigDecimal amount, AcbLineOutcome outcome,
                         Long rePresentsLineId, Integer fileSequence, String unpaidCode, String unpaidReason,
                         UnpaidDecision decision, UnpaidDecision suggestedDecision, String permissionNote,
                         LetterStatus letterStatus) {
        this(id, memberId, membershipNumber, surname, initials, bankName, branchCode, accountLastFour, accountType,
                debitDay, actionDate, amount, outcome, rePresentsLineId, fileSequence, unpaidCode, unpaidReason, decision,
                suggestedDecision, permissionNote, letterStatus, null, null, null, false, null, null, null);
    }
}

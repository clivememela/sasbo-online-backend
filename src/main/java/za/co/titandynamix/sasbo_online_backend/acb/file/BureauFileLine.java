package za.co.titandynamix.sasbo_online_backend.acb.file;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * One debit as the bureau file needs it: what the run line snapshotted, in the file's terms.
 *
 * <p>🔒 Carries a member's account number, so {@link #toString()} names the line's id only.
 *
 * @param lineId           the run line this is, so the sequence the file gives it can be stored on
 *                         the line -- the bank's returns report names an unpaid by that sequence
 * @param homingBranch     the member's bank's branch code, six digits
 * @param homingAccount    the member's account, one to eleven digits
 * @param accountType      1 cheque/current, 2 savings, 3 transmission; {@code null} refuses the file
 * @param rePresentsMonth  for a debit presented again, the month of the unpaid it presents -- the
 *                         file prints its name where the month's own debits print {@code SUBSCRIP}
 */
public record BureauFileLine(
        Long lineId,
        String homingBranch,
        String homingAccount,
        Integer accountType,
        BigDecimal amount,
        LocalDate actionDate,
        String membershipNumber,
        String surname,
        String initials,
        YearMonth rePresentsMonth) {

    @Override
    public String toString() {
        return "BureauFileLine[lineId=" + lineId + ", actionDate=" + actionDate + "]";
    }
}

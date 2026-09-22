package za.co.titandynamix.sasbo_online_backend.acb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * An earlier unpaid the union decided to present again, not yet presented in a live run.
 *
 * @param lineId  the unpaid line
 * @param memberId whose debit it was
 * @param amount  what that line charged -- a re-presentation charges the same amount until SASBO
 *                says whether a debit re-presented after a rate change takes the new rate
 * @param month   the month it was for, which the bureau file prints in the re-presented line's
 *                reference (July's file: {@code JUNE})
 * @param permitted the decision carries the member's permission for a further re-debit (D32)
 */
public record RePresentationCandidate(Long lineId, Integer memberId, BigDecimal amount, YearMonth month,
                                      boolean permitted) {

    /** As a query selects it: the run's month is stored as its first day. */
    public RePresentationCandidate(Long lineId, Integer memberId, BigDecimal amount, LocalDate monthStart,
                                   boolean permitted) {
        this(lineId, memberId, amount, YearMonth.from(monthStart), permitted);
    }
}

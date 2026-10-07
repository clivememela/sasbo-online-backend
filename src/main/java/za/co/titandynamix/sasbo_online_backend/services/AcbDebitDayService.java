package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbBanksDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbDebitDayMoveDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.DebitDayMoveRequest;

/**
 * Moving a bank's debit-order members to a new debit day in one step (Stephne, DOD-19: "When a bank changes its debit
 * day, move all of its members in one step? -- Yes"). Previewed first; then one transaction, audited, and safe to ask
 * twice.
 */
public interface AcbDebitDayService {

    /** The banks, each with its debit-order members counted by day. */
    AcbBanksDto banks();

    /** What a move would do -- nothing changes. {@code BANK_NOT_FOUND}, {@code DEBIT_DAY_INVALID}. */
    AcbDebitDayMoveDto preview(int bankId, Integer newDay);

    /**
     * Moves every debit-order member of the bank to {@code newDay}, and the bank's own day with them. Refused
     * {@code MOVE_COUNT_CHANGED} when the members to move are no longer the number the preview showed; moving again to
     * the same day moves nobody and answers so.
     */
    AcbDebitDayMoveDto move(int bankId, DebitDayMoveRequest request, String actor);

    /** The move dated {@code at} -- for the demonstration seed's past. The route moves now. */
    AcbDebitDayMoveDto move(int bankId, DebitDayMoveRequest request, String actor, java.time.Instant at);
}

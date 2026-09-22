package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What the union decided about an unpaid debit, recorded with it.
 *
 * <p>SASBO, 18 September 2026: <i>"we will either mark it as a re-debit, or mark it as a suspension
 * depending on the suspension codes."</i> The decision is SASBO's per unpaid, for any reason; the
 * re-present rule reads the decision and never the reason. An unpaid with no decision yet is simply
 * undecided -- {@code null} on the line -- and is counted as such rather than guessed at.
 */
public enum UnpaidDecision {
    /** Present the debit again in the next run, on the member's own day, at the same amount. */
    RE_PRESENT,
    /** Suspend the member; the debit is not presented again. */
    SUSPEND
}

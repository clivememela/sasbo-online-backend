package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why a re-debit the union had decided on is never presented -- the twin of V72's
 * {@code acb_run_lines_withdrawn_known} CHECK.
 */
public enum ReDebitWithdrawal {
    /** The member was suspended and is active again: "Only the current month" is collected (Stephne, DOD-26). */
    REACTIVATED,
    /** The member's membership ended and they joined again, with a new join date (Stephne, DOD-27). */
    REJOINED
}

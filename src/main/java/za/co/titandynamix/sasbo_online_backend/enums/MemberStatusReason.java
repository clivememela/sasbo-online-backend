package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why {@code MemberService.changeStatus} moved a member -- the reason SASBO counts by (Q25: "exactly how
 * many members we cancelled on debit orders"). Recorded in {@code member_status_changes} (V50).
 */
public enum MemberStatusReason {
    /** Three unpaid presentations -- every unpaid counts, a re-debit included (Q27). Terminates. */
    SUBS_IN_ARREARS,
    /**
     * An unpaid decided SUSPEND: its code allows no re-debit (Q40, Q44). Suspends -- "still pending and not
     * terminated as yet".
     */
    UNPAID_SUSPENDED
}

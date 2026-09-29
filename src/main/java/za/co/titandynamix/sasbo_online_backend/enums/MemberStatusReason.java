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
    UNPAID_SUSPENDED,
    /** Month end 03.2 (V55): a change into a leaving status, carrying SASBO's deletion code (rule L). */
    DELETION_CODE,
    /** Month end 03.2 (V55): a change into Suspended, carrying SASBO's suspension code (rule U). */
    SUSPENSION_CODE,
    /** Month end 03.2 (V55): a change out of a leaving or a suspended status -- a member who came back (MM63). */
    REINSTATED,
    /** Month end 03.2 (V55): a status given when the member was captured, or set by hand with no code of SASBO's. */
    CAPTURED
}

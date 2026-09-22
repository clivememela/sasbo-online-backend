package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * The member statuses the ACB module moves a member to (03.5), spelled as the member record stores them and the
 * browser's {@code STATUS_OPTIONS} offers them. A member's status is text, not an enum: these two are the ones the
 * returns loop writes.
 */
public final class MemberStatuses {

    /** "Still pending and not terminated as yet" -- an unpaid whose code allows no re-debit (Q40, Q44). */
    public static final String SUSPENDED = "Suspended";

    /** Three unpaid presentations (Q27): the membership is cancelled, for the reason SUBS_IN_ARREARS (Q25). */
    public static final String TERMINATED = "Terminated";

    private MemberStatuses() {
    }
}

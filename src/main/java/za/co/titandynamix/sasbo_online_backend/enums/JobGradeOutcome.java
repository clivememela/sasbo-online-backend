package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What the server did with the job grade the client submitted (ux.md §6c clause ②).
 *
 * <h2>The client never decides what is "custom" -- the server does, and says so</h2>
 * The browser sends the string it always sent. There is no new request field, so a
 * stale client has nothing to lie about. The server resolves the value against that
 * bank's grades and reports the outcome back on the saved member.
 *
 * <h2>🔴 {@link #CLEARED} is why this enum exists</h2>
 * {@code resolveJobGrade} used to end {@code .orElse(null)}: a grade the server did
 * not recognise was discarded without a word, the save returned success, and the
 * field came back empty. That is the A31 class of defect -- and it sat in the one
 * field the stakeholder had just asked us to open up to free text, so typing a grade
 * would have appeared to work and then vanished.
 */
public enum JobGradeOutcome {

    /**
     * Resolved to a master-list grade for this bank -- {@code ba2} to {@code BA2}.
     * The canonical value is stored; the user's spelling is quietly corrected.
     */
    MATCHED,

    /**
     * Genuinely new. Stored against this bank and flagged unverified, so it is
     * captured without becoming everyone else's drop-down option.
     */
    CAPTURED,

    /** Blank or unusable. Said out loud, rather than being a silent null. */
    CLEARED
}

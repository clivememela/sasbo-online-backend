package za.co.titandynamix.sasbo_online_backend.legacy.reconcile;

/**
 * What a month-end figure counts, in SASBO's own terms (31 August 2026: on record 65,714 · suspended 145 · members
 * 65,569 · joined 591 · left 400). Every total SASBO reads EXCLUDES the suspended; they are counted beside it.
 */
public enum Measure {
    /** On record, the suspended included -- {@code TOTALS ALL AREAS INCLUDING SUSPENSIONS}. */
    ON_RECORD,
    /** Suspended, with a code. */
    SUSPENDED,
    /** On record and not suspended -- the figure SASBO calls its members. */
    MEMBERS,
    /** A date joined in the month (rule N). */
    JOINED,
    /** Deleted in the month, with a code (rule L). */
    LEFT
}

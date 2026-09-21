package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why the bank declined a file, from a short list (SD3-H) -- the twin of V41's
 * {@code acb_runs_declined_reason_known} CHECK. The bank's own words go beside it, never instead of it.
 */
public enum AcbDeclineReason {
    /** A debit on a day the bank will not take -- "an incorrect debit date, like a holiday" (Q29). */
    WRONG_DEBIT_DATE,
    /** The file's layout. */
    LAYOUT,
    /** Anything else; the bank's words say what. */
    OTHER
}

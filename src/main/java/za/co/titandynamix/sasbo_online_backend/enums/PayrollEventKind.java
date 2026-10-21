package za.co.titandynamix.sasbo_online_backend.enums;

/** One kind of entry in the payroll module's record of what was done ({@code payroll_events}). */
public enum PayrollEventKind {
    FILE_LOADED,
    FILE_RECONCILED,
    FILE_SUPERSEDED,
    FILE_PURGED,
    /** The file as it arrived, downloaded (PAY-32): who and when, never the content. */
    FILE_DOWNLOADED,
    QUERY_RAISED,
    /** An employer's query list produced as a protected file -- never its password. */
    QUERY_FILE_PRODUCED,
    /** A produced query list downloaded. */
    QUERY_FILE_DOWNLOADED,
    NOTE_ADDED,
    DISCREPANCY_RESOLVED,
    PAYMENT_RECORDED,
    SETTINGS_CHANGED,
    SEEDED
}

package za.co.titandynamix.sasbo_online_backend.enums;

/** Where a payroll file stands. */
public enum PayrollFileStatus {
    /** Loaded and compared: the employer's file for its month. */
    RECONCILED,
    /** Replaced by a later file for the same employer and month; kept on record. */
    SUPERSEDED,
    /** Its contents and lines deleted a year after it was loaded (Stephne, PAY-39); what was found stays. */
    PURGED
}

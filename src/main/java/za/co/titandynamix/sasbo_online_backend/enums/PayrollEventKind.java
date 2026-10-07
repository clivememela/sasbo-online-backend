package za.co.titandynamix.sasbo_online_backend.enums;

/** One kind of entry in the payroll module's record of what was done ({@code payroll_events}). */
public enum PayrollEventKind {
    FILE_LOADED,
    FILE_RECONCILED,
    FILE_SUPERSEDED,
    FILE_PURGED,
    QUERY_RAISED,
    QUERY_DOWNLOADED,
    NOTE_ADDED,
    DISCREPANCY_RESOLVED,
    PAYMENT_RECORDED,
    SETTINGS_CHANGED,
    SEEDED
}

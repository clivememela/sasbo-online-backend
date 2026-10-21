package za.co.titandynamix.sasbo_online_backend.enums;

/** How a payroll file came in. */
public enum PayrollEntry {
    /** A spreadsheet loaded as the employer sent it. */
    UPLOAD,
    /**
     * Rows typed in by hand: GBS sends a PDF and SA Reserve Bank a picture, neither of which the system can read
     * (Stephne, PAY-03; PAY.md, clarification 2).
     */
    TYPED
}

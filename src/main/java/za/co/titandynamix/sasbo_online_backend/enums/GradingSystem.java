package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Which grading scheme a job grade belongs to.
 *
 * <h2>🔑 Why one bank is not one vocabulary</h2>
 * SBV uses TWO systems at once: TASK grades 1–11 (bargaining unit) and Paterson
 * D Lower … F Upper (non-bargaining). The bargaining-unit answer there follows the
 * SYSTEM rather than the grade, so any model assuming one grade vocabulary per
 * employer is wrong for that employer.
 *
 * <h2>And why the band is stored rather than parsed</h2>
 * {@code grade} has at least three notations across the banks on file —
 * {@code "C"} (Capitec), {@code "C3"} (Nedbank), {@code "D Upper"} (SBV) — so
 * anything wanting the Paterson band must read the stored {@code band} column
 * instead of slicing the first character of the string.
 */
public enum GradingSystem {

    /** Paterson bands A–F, with or without a sub-level. */
    PATERSON,

    /** The TASK system — SBV's grades 1–11. */
    TASK,

    /** The Reserve Bank's own level codes: M1–M4, SP1–SP6, FC1–FC4. */
    SARB_LEVELS,

    /**
     * The employer's own scheme, mapping to no published system: Absa's
     * {@code BA1}/{@code AVP}, Standard Bank's {@code SBG04}, OM Bank's
     * {@code Grade c}.
     */
    BANK_SPECIFIC
}

package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Whether an unpaid under one of the bank's codes may be presented again -- stored as {@code acb_unpaid_codes
 * .re_debit_allowed}, true, false or NULL.
 *
 * <p>Stephne, DOD-03: bank code 2 "allows to redebit"; code 4 "cannot redebit"; DOD-04: 30, 31, 32 and 34 "we cannot
 * try to redebit". The codes her sheet lists and she has not ruled on are {@link #UNKNOWN} (Friday item 9), and an
 * unknown code is never re-debited until an administrator holding DOD_SETTINGS sets it: nothing is guessed.
 */
public enum ReDebit {
    YES,
    NO,
    UNKNOWN;

    /** The column's value as the rule reads it. */
    public static ReDebit of(Boolean allowed) {
        return allowed == null ? UNKNOWN : allowed ? YES : NO;
    }

    /** The column's value for this rule. */
    public Boolean stored() {
        return this == UNKNOWN ? null : this == YES;
    }
}

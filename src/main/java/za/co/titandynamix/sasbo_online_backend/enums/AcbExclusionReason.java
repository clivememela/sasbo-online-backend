package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why a member is not debited in a run -- ONE reason per member, the first rule they fail, in the
 * order the constants are declared.
 *
 * <h2>The order is part of the contract</h2>
 * A member with no bank on record AND a debit day of the 5th is reported once, for the bank.
 * Reporting every failed rule would count one person several times in a report whose totals an
 * administrator reconciles against the membership. The declaration order below is the order the
 * rules are applied, and a test pins it.
 *
 * <h2>Listed or counted</h2>
 * The first two reasons describe members the run was never for -- payroll-deduction and cash
 * members, members who are not Active -- and are COUNTED by the value that excluded them, since
 * tens of thousands of payroll rows are not a report. The rest are things an administrator can fix
 * for a named member, and are LISTED one member per row.
 */
public enum AcbExclusionReason {
    /** Not a debit-order member. Counted by payment type, not listed. */
    NOT_DEBIT_ORDER(false),
    /** Not in a status the run debits -- Active, until SASBO says otherwise. Counted by status. */
    STATUS_NOT_ACTIVE(false),
    /** The bank name on the member record matches no bank on record. */
    NO_BANK_ON_RECORD(true),
    /** The member's bank has no ACTIVE subscription row: SASBO does not collect there. */
    NO_SUBSCRIPTION_ROW(true),
    /** The member's debit day is missing, or outside the 20th to the 31st. */
    DAY_OUT_OF_WINDOW(true),
    /** The account number is not one to eleven digits -- the width the bureau file gives it. */
    ACCOUNT_SHAPE_INVALID(true),
    /**
     * Something the file or the letters print is missing: the membership number, the surname, or
     * -- since 00.5 -- the account type, which every member captured before V49 lacks.
     */
    SNAPSHOT_INCOMPLETE(true);

    private final boolean listed;

    AcbExclusionReason(boolean listed) {
        this.listed = listed;
    }

    /** Whether excluded members are listed by name (true) or only counted by value (false). */
    public boolean listed() {
        return listed;
    }
}

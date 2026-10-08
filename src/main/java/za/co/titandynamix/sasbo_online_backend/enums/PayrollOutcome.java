package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * What was done about a payroll discrepancy, as an administrator records it when resolving one (Stephne, PAY-20: "Admin
 * to follow up"). Recording an outcome changes no member's record: a status or a payment type is changed on the record
 * itself, by whoever may edit members. Which outcomes fit which kind of discrepancy is {@link PayrollDiscrepancyType}'s.
 *
 * <p>The answers a bank can give to a query are still to be confirmed with Stephne (PAY.md, clarification 11), so
 * {@link #OTHER}, with the note every resolution carries, takes whatever the list does not.
 */
public enum PayrollOutcome {

    JOINED_AND_CAPTURED("Joined, and captured as a member"),
    DEDUCTION_STOPPED("The employer stopped the deduction"),
    MEMBER_REINSTATED("The member was reinstated"),
    DEDUCTION_RESTARTED("The employer restarted the deduction"),
    MEMBER_SUSPENDED("The member was suspended"),
    MEMBER_LEFT_EMPLOYER("The member has left the employer"),
    ON_LEAVE("Maternity or sick leave"),
    MEMBER_DELETED("The member was deleted"),
    CORRECTED_BY_EMPLOYER("The employer corrected it"),
    ARREARS_RECORDED("The difference was recorded as arrears"),
    CREDIT_RECORDED("The difference was recorded as a credit"),
    REFUNDED("Refunded to the member"),
    CATCH_UP_CONFIRMED("Confirmed as a catch-up of missed months"),
    RECORD_UPDATED("The member's record was updated"),
    EMPLOYER_ASKED_TO_CORRECT("The employer was asked to correct its records"),
    SAME_PERSON_CONFIRMED("Confirmed as the same person"),
    NOT_THE_SAME_PERSON("Not the same person"),
    SWITCHED_TO_PAYROLL("Switched to payroll; the debit order was stopped"),
    STAYS_ON_DEBIT_ORDER("Stays on debit order; the employer was asked to stop"),
    IGNORED("Nothing to do"),
    CORRECTED_FILE_RECEIVED("A corrected file was received"),
    EXPLAINED("Explained"),
    BALANCE_RECEIVED("The balance was received"),
    OTHER("Something else (see the note)"),
    /** Set by the system only: comparing the file again no longer finds it. */
    NO_LONGER_APPLIES("No longer found when compared again", true),
    /** Set by the system only: the file was replaced by a later one for the same employer and month. */
    SUPERSEDED("The file was replaced", true);

    private final String label;
    private final boolean systemOnly;

    PayrollOutcome(String label) {
        this(label, false);
    }

    PayrollOutcome(String label, boolean systemOnly) {
        this.label = label;
        this.systemOnly = systemOnly;
    }

    /** The words a screen, a report and the record show for it. */
    public String label() {
        return label;
    }

    /** Whether only the system sets it -- never offered to a person. */
    public boolean systemOnly() {
        return systemOnly;
    }
}

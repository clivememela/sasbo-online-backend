package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * How a line of a payroll file was matched to a member, in the order the rules are tried (Stephne, PAY-10: staff
 * number, ID number, and "Initials and surname").
 */
public enum PayrollMatchRule {
    /** The staff number, within the employer: staff numbers are the employer's, so they mean nothing outside it. */
    STAFF_NUMBER,
    /** The ID number, anywhere on record. */
    ID_NUMBER,
    /** Initials and surname, within the employer, and only where exactly one member fits. */
    NAME,
    /** No member fits. */
    NONE
}

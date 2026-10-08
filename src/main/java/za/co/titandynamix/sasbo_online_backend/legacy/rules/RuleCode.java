package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import static za.co.titandynamix.sasbo_online_backend.legacy.rules.Severity.ERROR;
import static za.co.titandynamix.sasbo_online_backend.legacy.rules.Severity.WARNING;

/**
 * Every check a migrated row is put through, as a stable code with its severity and the sentence an administrator
 * reads. A row's problems are stored as these codes and the field they are about -- never the value -- so a report and
 * a log line say what is wrong and where, and nothing of whom.
 *
 * <p><b>Severity is a default.</b> A mapping may make any warning an error (stricter is always allowed), and may make an
 * error a warning only where {@link #downgradable()} says so: the identity-number and workplace checks, which are
 * SASBO's to decide (MIG-21: "move it and list it for staff to fix", MIG-22: "a holding workplace"). A downgraded
 * identity number is not written to the member -- capture would refuse it -- and the row is listed for staff.
 *
 * <p>The codes are published in {@code api-vocabulary.json} with their severities; each is produced in one place and
 * named by a test.
 */
public enum RuleCode {

    // ------------------------------------------------------------------------------------- reading the file

    ROW_MALFORMED(ERROR, false, "The row could not be read cleanly: a quote is not closed, or it has more cells than "
            + "the header."),
    ROW_SHORT(WARNING, false, "The row is shorter than the layout, or has fewer cells than the header; the missing "
            + "fields are read as blank."),
    FIELD_TOO_LONG(ERROR, false, "A value is longer than its field can hold."),
    ENCODING_SUSPECT(WARNING, false, "Some characters could not be read in the mapping's encoding: the file may be in "
            + "another one (exports from older systems are often Windows-1252)."),

    // ------------------------------------------------------------------------------------- the mapping

    DATE_UNREADABLE(ERROR, false, "A date does not match any of the mapping's date formats."),
    STATUS_UNKNOWN(ERROR, false, "The status is not one the mapping's list of statuses knows."),
    CODE_UNKNOWN(WARNING, false, "The value is not in the mapping's list for its field; the field is not moved."),

    // ------------------------------------------------------------------------------------- who the member is

    REQUIRED_MISSING(ERROR, false, "A field every member must have is blank."),
    IDENTITY_MISSING(ERROR, true, "There is neither an identity number nor a passport number."),
    ID_NUMBER_FORMAT(ERROR, true, "The identity number is not 13 digits."),
    ID_NUMBER_DATE(ERROR, true, "The identity number's first six digits are not a real date of birth."),
    ID_NUMBER_CHECK_DIGIT(ERROR, true, "The identity number's check digit is wrong: it was mistyped, or it is not a "
            + "South African identity number."),
    DATE_OF_BIRTH_MISMATCH(WARNING, false, "The date of birth differs from the one in the identity number; the "
            + "identity number's is used."),
    DATE_OF_BIRTH_UNREADABLE(WARNING, false, "The date of birth cannot be read; the identity number's is used."),
    DATE_OF_BIRTH_REQUIRED(ERROR, false, "A member with a passport number needs a date of birth."),
    GENDER_MISMATCH(WARNING, false, "The gender differs from the one in the identity number; the identity number's is "
            + "used."),
    ID_NUMBER_DUPLICATE_IN_BATCH(ERROR, false, "Another row of this file has the same identity number."),
    ID_NUMBER_ALREADY_MEMBER(ERROR, false, "A member on record already has this identity number."),

    // ------------------------------------------------------------------------------------- the membership

    MEMBERSHIP_NUMBER_FORMAT(ERROR, false, "The membership number is not in the form the mapping expects."),
    MEMBERSHIP_NUMBER_DUPLICATE_IN_BATCH(ERROR, false, "Another row of this file has the same membership number."),
    MEMBERSHIP_NUMBER_ALREADY_USED(ERROR, false, "A member on record already has this membership number."),
    JOIN_DATE_IN_FUTURE(ERROR, false, "The date joined is after today."),

    // ------------------------------------------------------------------------------------- status and SASBO's codes

    DELETED_WITHOUT_CODE(ERROR, false, "A deleted member has no deletion code."),
    DELETED_WITHOUT_DATE(ERROR, false, "A deleted member has no date deleted."),
    DELETION_CODE_UNKNOWN(ERROR, false, "The deletion code is not one of SASBO's on record here."),
    SUSPENDED_WITHOUT_CODE(ERROR, false, "A suspended member has no suspension code."),
    SUSPENSION_CODE_UNKNOWN(ERROR, false, "The suspension code is not one of SASBO's on record here."),
    SUSPENSION_DATE_MISSING(WARNING, false, "A suspended member has no date suspended; the date the export represents "
            + "is used."),
    STATUS_DATE_IN_FUTURE(ERROR, false, "The date deleted or suspended is after today."),
    STATUS_DATE_BEFORE_JOINED(ERROR, false, "The date deleted or suspended is before the date joined."),
    CODE_ON_ACTIVE_MEMBER(WARNING, false, "An active member carries a deletion or suspension code; it is ignored."),

    // ------------------------------------------------------------------------------------- where the member works

    WORKPLACE_UNKNOWN(ERROR, true, "The R/A number is not one of SASBO's workplaces on record here."),
    WORKPLACE_MISSING(WARNING, false, "There is no R/A number: the member is moved with no workplace, and is counted "
            + "under \"no workplace on record\"."),
    TABLE_CODE_UNKNOWN(WARNING, false, "The table code is not one of SASBO's on record here."),
    TABLE_CODE_DISAGREES(WARNING, false, "The table code differs from the workplace's; the workplace's is used."),
    AREA_UNKNOWN(WARNING, false, "The area letter is not one of SASBO's areas."),
    AREA_DISAGREES(WARNING, false, "The area differs from the workplace's; the workplace's is used."),

    // ------------------------------------------------------------------------------------- contact and payment

    PHONE_INVALID(WARNING, false, "A telephone number is not a South African number; it is not moved."),
    EMAIL_INVALID(WARNING, false, "An e-mail address is not in a valid form; it is not moved."),
    DEBIT_ORDER_WITHOUT_ACCOUNT(ERROR, false, "A member who pays by debit order has no bank account number."),
    ACCOUNT_NUMBER_INVALID(WARNING, false, "The bank account number is not 6 to 16 digits; it is not moved."),
    DEBIT_DAY_INVALID(WARNING, false, "The debit day is not a day of the month; it is not moved."),
    AMOUNT_INVALID(WARNING, false, "The subscription is not an amount in rand; it is not moved.");

    private final Severity severity;
    private final boolean downgradable;
    private final String sentence;

    RuleCode(Severity severity, boolean downgradable, String sentence) {
        this.severity = severity;
        this.downgradable = downgradable;
        this.sentence = sentence;
    }

    /** The severity unless a mapping says otherwise. */
    public Severity severity() {
        return severity;
    }

    /** Whether a mapping may make this error a warning -- SASBO's choice, where SASBO has one to make. */
    public boolean downgradable() {
        return downgradable;
    }

    /** What the problem is, for the person fixing the file. Never names a value. */
    public String sentence() {
        return sentence;
    }
}

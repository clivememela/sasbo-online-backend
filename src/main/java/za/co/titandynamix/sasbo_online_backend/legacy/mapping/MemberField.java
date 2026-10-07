package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a column of a legacy file can become: the member fields a move writes, and the facts it needs to write them --
 * SASBO's status and codes with their dates, the workplace's R/A number, the table code and area the reconciliation
 * counts by. Named on the wire as the member record names them ({@code surname}, {@code joinDate}).
 *
 * <p>The ceiling is the column's: a value longer is {@code FIELD_TOO_LONG}, never cut to fit a member's record.
 */
public enum MemberField {
    MEMBERSHIP_NUMBER("membershipNumber", 30, false),
    TITLE("title", 255, false),
    INITIALS("initials", 255, false),
    FIRST_NAMES("firstNames", 255, false),
    SURNAME("surname", 255, false),
    IDENTITY_NUMBER("identityNumber", 255, false),
    PASSPORT_NUMBER("passportNumber", 255, false),
    DATE_OF_BIRTH("dateOfBirth", 10, true),
    GENDER("gender", 255, false),
    RACE("race", 255, false),
    EMPLOYEE_NUMBER("employeeNumber", 255, false),
    JOIN_DATE("joinDate", 10, true),
    /** The old system's status, through the mapping's list: ACTIVE, SUSPENDED or DELETED. */
    STATUS("status", 20, false),
    /** SASBO's deletion code -- its number, never the table's id (RC7). */
    DELETION_CODE("deletionCode", 6, false),
    DELETED_ON("deletedOn", 10, true),
    /** SASBO's suspension code. */
    SUSPENSION_CODE("suspensionCode", 6, false),
    SUSPENDED_ON("suspendedOn", 10, true),
    /** The workplace's R/A number -- the branch list's ACCNO. */
    WORKPLACE_RA_NUMBER("workplaceRaNumber", 12, false),
    TABLE_CODE("tableCode", 6, false),
    AREA_LETTER("areaLetter", 4, false),
    /** "Bank Where Employed" as the member record has it: text, copied. */
    BANK_NAME("bankName", 255, false),
    PAYMENT_TYPE("paymentType", 255, false),
    BANK_ACCOUNT_NUMBER("bankAccountNumber", 255, false),
    ACCOUNT_TYPE("accountType", 255, false),
    DEBIT_DAY("debitDay", 2, false),
    MONTHLY_SUBSCRIPTION("monthlySubscription", 14, false),
    CELL_TEL("cellTel", 255, false),
    WORK_TEL("workTel", 255, false),
    HOME_TEL("homeTel", 255, false),
    PERSONAL_EMAIL("personalEmail", 255, false),
    WORK_EMAIL("workEmail", 255, false),
    ADDRESS("address", 255, false),
    REMARKS("remarks", 1000, false);

    private final String wire;
    private final int maxLength;
    private final boolean date;

    MemberField(String wire, int maxLength, boolean date) {
        this.wire = wire;
        this.maxLength = maxLength;
        this.date = date;
    }

    /** The field's name in a mapping, a report and the API. */
    public String wire() {
        return wire;
    }

    public int maxLength() {
        return maxLength;
    }

    /** A date: read through the mapping's date formats, stored as ISO. */
    public boolean isDate() {
        return date;
    }

    /** Whether a person's identity number, passport number or bank account: shown as its last four only. */
    public boolean masked() {
        return this == IDENTITY_NUMBER || this == PASSPORT_NUMBER || this == BANK_ACCOUNT_NUMBER;
    }

    public static Optional<MemberField> fromWire(String wire) {
        return Arrays.stream(values()).filter(field -> field.wire.equals(wire)).findFirst();
    }
}

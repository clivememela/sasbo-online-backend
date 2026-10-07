package za.co.titandynamix.sasbo_online_backend.payroll;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a column of an employer's file can hold, as far as the comparison is concerned. Every other column -- a wage
 * type, a cost centre, a payroll area -- is read into the line's raw text and nothing else.
 */
public enum PayrollField {
    STAFF_NUMBER("staffNumber"),
    ID_NUMBER("idNumber"),
    INITIALS("initials"),
    FIRST_NAMES("firstNames"),
    SURNAME("surname"),
    /** A name in one column, written in the layout's {@link NameStyle}. */
    FULL_NAME("fullName"),
    AMOUNT("amount"),
    /** Added to the amount: FNB's "SASBO Membership Fees Adjustment", where a correction or catch-up shows. */
    ADJUSTMENT("adjustment"),
    /** Standard Bank's debit and credit: summed for the amount where the total is empty. */
    DEBIT("debit"),
    CREDIT("credit"),
    /** The month the line deducts for (Absa's and Nedbank's For-period, YYYYMM). */
    FOR_PERIOD("forPeriod");

    private final String wire;

    PayrollField(String wire) {
        this.wire = wire;
    }

    /** The name a column mapping and an answer use for it. */
    public String wire() {
        return wire;
    }

    public static Optional<PayrollField> fromWire(String name) {
        return Arrays.stream(values()).filter(f -> f.wire.equals(name)).findFirst();
    }
}

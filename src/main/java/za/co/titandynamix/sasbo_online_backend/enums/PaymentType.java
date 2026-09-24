package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * Payment type enumeration for member subscriptions.
 * Determines which banking information is required.
 */
@Getter
public enum PaymentType {
    DEBIT_ORDER("Debit Order"),
    /**
     * SD2-3. The display name is <b>"Payroll Deduction"</b>, not "Payroll".
     * <p>
     * Four sources agree on the longer name: the specification uses it eight
     * times, {@code V10}'s own comment says the system supports "Debit Order"
     * and "Payroll Deduction", and <b>48 of the 160 seeded members are stored
     * with it</b>. The enum and the client were the outliers.
     * <p>
     * 🔑 The cheap fix was to change the CLIENT's comparison to "Payroll" --
     * one line, and backwards: it would have orphaned those 48 live members,
     * whose stored value no longer matched anything the UI offered.
     * {@link #fromString} already accepts both spellings, so nothing that reads
     * existing data breaks.
     */
    PAYROLL("Payroll Deduction");

    private final String displayName;

    PaymentType(final String displayName) {
        this.displayName = displayName;
    }

    /**
     * Check if this payment type requires bank account information.
     * Only Debit Order requires a bank account number.
     */
    public boolean requiresBankAccount() {
        return this == DEBIT_ORDER;
    }

    /**
     * Parse payment type from string (case-insensitive).
     * Accepts both enum name and display name.
     *
     * <p>🔴 FIX: Unknown payment types now throw with clear message. The validator
     * will catch and log these, rather than silently ignoring them.
     */
    public static PaymentType fromString(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_");
        if ("PAYROLL_DEDUCTION".equals(normalized)) {
            normalized = "PAYROLL";
        }
        for (final PaymentType type : values())
            if (type.name().equals(normalized)
                        || type.displayName.equalsIgnoreCase(value.trim())) {
                return type;
            }
        throw new IllegalArgumentException("Invalid payment type: " + value + ". Supported types: Debit Order, Payroll Deduction");
    }
}

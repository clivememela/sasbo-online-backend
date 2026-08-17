package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Payment type enumeration for member subscriptions.
 * Determines which banking information is required.
 */
public enum PaymentType {
    DEBIT_ORDER("Debit Order"),
    PAYROLL_DEDUCTION("Payroll Deduction");

    private final String displayName;

    PaymentType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Check if this payment type requires bank account information.
     * Only Debit Order requires bank account number.
     */
    public boolean requiresBankAccount() {
        return this == DEBIT_ORDER;
    }

    /**
     * Parse payment type from string (case-insensitive).
     * Accepts both enum name and display name.
     */
    public static PaymentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toUpperCase().replace(" ", "_");
        for (PaymentType type : PaymentType.values()) {
            if (type.name().equals(normalized) || type.displayName.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid payment type: " + value);
    }
}

package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * Payment type enumeration for member subscriptions.
 * Determines which banking information is required.
 */
@Getter
public enum PaymentType {
    DEBIT_ORDER("Debit Order"),
    PAYROLL("Payroll");

    private final String displayName;

    PaymentType(String displayName) {
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
     */
    public static PaymentType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace(" ", "_");
        if ("PAYROLL_DEDUCTION".equals(normalized)) {
            normalized = "PAYROLL";
        }
        for (PaymentType type : PaymentType.values()) {
            if (type.name().equals(normalized)
                    || type.displayName.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid payment type: " + value);
    }
}

package za.co.titandynamix.sasbo_online_backend.enums;

import lombok.Getter;

/**
 * The type of the account a debit order draws on (ACB {@code 00.5}; Q16, "yes definitely").
 *
 * <p>The bureau file prints {@link #getCode() the digit} on every debit line -- position 47, and
 * SASBO's July 2026 file carried 2,746 · 449 · 19 of types 1 · 2 · 3. The member record stores
 * {@link #getDisplayName() the display name}, the way {@code paymentType} stores "Debit Order": what
 * the capture screen offered is what the record says, and the digit is read from here, never from
 * the text.
 */
@Getter
public enum AccountType {

    CHEQUE(1, "Cheque"),
    SAVINGS(2, "Savings"),
    TRANSMISSION(3, "Transmission");

    /** The digit the bureau file prints: 1 cheque/current, 2 savings, 3 transmission. */
    private final int code;
    private final String displayName;

    AccountType(int code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * The type a stored or submitted value names -- its display name or the constant's own name, case
     * and surrounding spaces ignored -- or {@code null} for a blank one.
     *
     * @throws IllegalArgumentException for anything else, naming the three it accepts
     */
    public static AccountType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (AccountType type : values()) {
            if (type.name().equalsIgnoreCase(trimmed) || type.displayName.equalsIgnoreCase(trimmed)) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Account type must be Cheque, Savings or Transmission, not '" + trimmed + "'");
    }
}

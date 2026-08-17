package za.co.titandynamix.sasbo_online_backend.enums;

import java.util.ArrayList;
import java.util.List;

/**
 * Preferred email type for member/representative communications.
 * Determines which email address(es) to use when sending
 * updates, notifications, and other correspondence.
 *
 * <p>Tri-state selection rules:
 * <ul>
 *   <li>{@code BOTH} → both work and personal emails are selected</li>
 *   <li>{@code WORK} or {@code PERSONAL} alone → only that email is selected; {@code BOTH} is NOT selected</li>
 *   <li>If both work AND personal are individually selected → {@code BOTH} is automatically implied</li>
 * </ul>
 */
public enum PreferredEmailType {
    PERSONAL("Personal"),
    WORK("Work"),
    BOTH("Both");

    private final String displayName;

    PreferredEmailType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Whether this preference includes the personal email address.
     */
    public boolean includesPersonal() {
        return this == PERSONAL || this == BOTH;
    }

    /**
     * Whether this preference includes the work email address.
     */
    public boolean includesWork() {
        return this == WORK || this == BOTH;
    }

    /**
     * Resolve which email addresses to use for communication.
     *
     * @param personalEmail the personal email address (may be null)
     * @param workEmail     the work email address (may be null)
     * @return list of email addresses to send to (never null, may be empty)
     */
    public List<String> resolveEmails(String personalEmail, String workEmail) {
        List<String> emails = new ArrayList<>();
        if (this.includesPersonal() && personalEmail != null && !personalEmail.isBlank()) {
            emails.add(personalEmail);
        }
        if (this.includesWork() && workEmail != null && !workEmail.isBlank()) {
            emails.add(workEmail);
        }
        return emails;
    }

    /**
     * Derive the preferred email type from boolean selections of work and personal.
     * If both are selected → BOTH. If only one → that one. If neither → null.
     *
     * @param personalSelected whether personal email is selected
     * @param workSelected     whether work email is selected
     * @return the resolved PreferredEmailType, or null if neither is selected
     */
    public static PreferredEmailType fromSelection(boolean personalSelected, boolean workSelected) {
        if (personalSelected && workSelected) return BOTH;
        if (personalSelected) return PERSONAL;
        if (workSelected) return WORK;
        return null;
    }

    /**
     * Parse preferred email type from string (case-insensitive).
     * Accepts both enum name and display name.
     */
    public static PreferredEmailType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.toUpperCase().replace(" ", "_");
        for (PreferredEmailType type : PreferredEmailType.values()) {
            if (type.name().equals(normalized) || type.displayName.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid preferred email type: " + value
                + ". Allowed values: Personal, Work, Both");
    }
}

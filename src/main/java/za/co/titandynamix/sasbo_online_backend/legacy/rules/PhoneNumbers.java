package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import java.util.Optional;

/**
 * A South African telephone number, written one way: {@code +27} and the nine digits after the trunk nought. Spaces,
 * hyphens, brackets and dots go; {@code 082 123 4567}, {@code 27821234567}, {@code 0027 82 123 4567} and
 * {@code +27 (0)82 123 4567} are one number. Nine digits with no nought -- a spreadsheet's work, which drops leading
 * noughts -- are read as the number they were. Anything else is not a South African number and is not moved.
 */
public final class PhoneNumbers {

    private PhoneNumbers() {
    }

    public static Optional<String> normalise(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String digits = raw.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+")) {
            if (!digits.startsWith("+27")) {
                return Optional.empty();
            }
            digits = digits.substring(3);
        } else if (digits.startsWith("0027")) {
            digits = digits.substring(4);
        } else if (digits.length() == 11 && digits.startsWith("27")) {
            digits = digits.substring(2);
        }
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.matches("[1-8][0-9]{8}") ? Optional.of("+27" + digits) : Optional.empty();
    }
}

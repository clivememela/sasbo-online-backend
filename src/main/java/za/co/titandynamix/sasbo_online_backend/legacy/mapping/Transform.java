package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * What a mapping may do to a value before it is checked, applied in the order the mapping lists them. Each is a plain
 * function of the text: no lookup, no guess.
 */
public enum Transform {
    TRIM("trim"),
    UPPER("upper"),
    LOWER("lower"),
    /** Each word capitalised, after spaces, hyphens and apostrophes: {@code o'BRIEN-SMITH} reads O'Brien-Smith. */
    TITLE("title"),
    /** Runs of spaces as one. */
    COLLAPSE_SPACES("collapseSpaces"),
    /** Every space gone -- the old system's dates print a space where a leading zero would be, {@code  9/ 9/2026}. */
    REMOVE_SPACES("removeSpaces"),
    DIGITS_ONLY("digitsOnly"),
    /** {@code 000123} as {@code 123}; nought stays {@code 0}. */
    STRIP_LEADING_ZEROS("stripLeadingZeros");

    private final String wire;

    Transform(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static Optional<Transform> fromWire(String wire) {
        return Arrays.stream(values()).filter(transform -> transform.wire.equals(wire)).findFirst();
    }

    public String apply(String value) {
        if (value == null) {
            return null;
        }
        return switch (this) {
            case TRIM -> value.strip();
            case UPPER -> value.toUpperCase(Locale.ROOT);
            case LOWER -> value.toLowerCase(Locale.ROOT);
            case TITLE -> title(value);
            case COLLAPSE_SPACES -> value.replaceAll("\\s+", " ");
            case REMOVE_SPACES -> value.replaceAll("\\s+", "");
            case DIGITS_ONLY -> value.replaceAll("[^0-9]", "");
            case STRIP_LEADING_ZEROS -> value.matches("0+[0-9]+") ? value.replaceFirst("^0+(?=.)", "") : value;
        };
    }

    private static String title(String value) {
        StringBuilder titled = new StringBuilder(value.length());
        boolean start = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            titled.append(start ? Character.toTitleCase(c) : Character.toLowerCase(c));
            start = Character.isWhitespace(c) || c == '-' || c == '\'';
        }
        return titled.toString();
    }
}

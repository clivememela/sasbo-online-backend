package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A payroll refusal: a stable {@link PayrollRefusalCode}, the sentence an administrator reads, and the facts the screen
 * needs -- every detail a string, in the order given.
 *
 * <p>🔒 The message and details are written by the payroll code from values it chose to expose. A value that was
 * RECEIVED is given back only through {@link #echo}, when it is short and of the alphabet a code or a number is written
 * in: a refusal that quotes its input is a way to make the server print anything, and a payroll line is personal data.
 */
public class PayrollRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What a value given back may look like: a month, a code, a word of the vocabulary, a number. */
    private static final Pattern ECHOABLE = Pattern.compile("[A-Za-z0-9_\\-]{1,40}");

    private final transient PayrollRefusalCode code;
    private final transient Map<String, String> details;

    public PayrollRefusal(PayrollRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    public PayrollRefusal(PayrollRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    public PayrollRefusalCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }

    /** A received value, given back only when it is short and plain; {@code null} otherwise (and then left out). */
    public static String echo(String received) {
        return received != null && ECHOABLE.matcher(received).matches() ? received : null;
    }

    /** Details in the order given, leaving out any whose value is {@code null}. */
    public static Map<String, String> details(String... keysAndValues) {
        Map<String, String> found = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            if (keysAndValues[i + 1] != null) {
                found.put(keysAndValues[i], keysAndValues[i + 1]);
            }
        }
        return found;
    }
}

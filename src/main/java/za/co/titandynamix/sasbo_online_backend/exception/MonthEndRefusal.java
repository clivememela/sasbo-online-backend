package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * A month-end refusal: a stable {@link MonthEndRefusalCode}, the sentence a person reads, and the facts the screen
 * needs to say something useful without a second request (ME-1).
 *
 * <p>🔒 The message and the details are written by this arc's code from values it chose to expose. EVERY DETAIL IS A
 * STRING (ME-14), and a value that was RECEIVED is given back only when it is short and of the alphabet expected --
 * {@link #echo} -- otherwise the field is named and the value is not: the envelope answers anyone who can reach the
 * API, and a refusal that quotes its input is a way to make the server print anything.
 */
public class MonthEndRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What a value given back may look like: a month, a code, a word of the vocabulary, a number. */
    private static final Pattern ECHOABLE = Pattern.compile("[A-Za-z0-9_\\-]{1,40}");

    private final transient MonthEndRefusalCode code;
    private final transient Map<String, String> details;

    public MonthEndRefusal(MonthEndRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    public MonthEndRefusal(MonthEndRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    public MonthEndRefusalCode code() {
        return code;
    }

    /** The facts behind the refusal, in the order they were given. Empty when there are none. */
    public Map<String, String> details() {
        return details;
    }

    /**
     * A received value, given back only when it is short and of the alphabet a month, a code or a number is written
     * in; {@code null} otherwise -- and a detail that is {@code null} is left out, so the field is named by the
     * refusal's code and its other details, and nothing of what was sent is printed (ME-14).
     */
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

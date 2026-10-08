package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SequencedMap;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A data-migration refusal: a stable {@link MigrationRefusalCode}, the sentence a person reads, and the facts the screen
 * needs -- the shape of {@code MonthEndRefusal}.
 *
 * <p>🔒 The message and the details are written by this code from values it chose to expose: a status, a count, a
 * limit, the name of a field. A value that was RECEIVED is given back only when it is short and of a plain alphabet --
 * {@link #echo} -- and a value read from a file never: the envelope answers whoever sent the request.
 */
public class MigrationRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private static final Pattern ECHOABLE = Pattern.compile("[A-Za-z0-9_\\-]{1,40}");

    private final transient MigrationRefusalCode code;
    private final transient Map<String, String> details;

    public MigrationRefusal(MigrationRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    public MigrationRefusal(MigrationRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        Map<String, String> given = details == null ? Map.of() : details;
        this.details = Collections.unmodifiableMap(given instanceof SequencedMap<?, ?>
                ? new LinkedHashMap<>(given)
                : new LinkedHashMap<>(new TreeMap<>(given)));
    }

    public MigrationRefusalCode code() {
        return code;
    }

    public Map<String, String> details() {
        return details;
    }

    /** A received value, given back only when short and plain; {@code null} -- and so left out -- otherwise. */
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

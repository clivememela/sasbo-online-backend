package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A sign-in refusal: a stable {@link SignInRefusalCode}, the sentence a SASBO administrator reads, and the facts the
 * screen needs. 🔒 Every detail is a value this code chose to expose -- never a password, a token, a hash, or anything
 * a caller typed that has not been held to a short, known alphabet.
 */
public class SignInRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient SignInRefusalCode code;
    private final transient Map<String, String> details;

    public SignInRefusal(SignInRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    public SignInRefusal(SignInRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    public SignInRefusalCode code() {
        return code;
    }

    /** The facts behind the refusal, in the order given; empty when none. */
    public Map<String, String> details() {
        return details;
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

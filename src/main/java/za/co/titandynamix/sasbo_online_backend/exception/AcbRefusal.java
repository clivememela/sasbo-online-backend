package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An ACB refusal: a stable {@link AcbRefusalCode}, a message written for the person at the desk,
 * and the facts the screen needs to say something useful without a second request.
 *
 * <p>🔒 The message and the details are written by ACB code from values it chose to expose -- a
 * run's id, status and version, a month, a bank's name. Never an exception's message and never a
 * member's account or identity number: the envelope answers anyone who can reach the API.
 */
public class AcbRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient AcbRefusalCode code;
    private final transient Map<String, String> details;

    public AcbRefusal(AcbRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    public AcbRefusal(AcbRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        this.details = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(details == null ? Map.of() : details));
    }

    public AcbRefusalCode code() {
        return code;
    }

    /** The facts behind the refusal, in the order they were given. Empty when there are none. */
    public Map<String, String> details() {
        return details;
    }
}

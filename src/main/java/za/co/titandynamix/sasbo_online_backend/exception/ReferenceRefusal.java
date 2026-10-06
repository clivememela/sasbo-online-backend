package za.co.titandynamix.sasbo_online_backend.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A refused change to a reference list: a stable {@link ReferenceRefusalCode}, the sentence a SASBO administrator
 * reads, and the facts behind it -- the envelope ACB's and month end's refusals answer with.
 *
 * <p>🔒 The message and the details are written by the code that refuses, from reference data it chose to expose (a
 * bank's name, a branch code, an id) -- never a member's value, and never the driver's message, which quotes SQL.
 */
public class ReferenceRefusal extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient ReferenceRefusalCode code;
    private final transient Map<String, String> details;

    public ReferenceRefusal(ReferenceRefusalCode code, String message) {
        this(code, message, Map.of());
    }

    /** @param details written by name, so the same refusal reads the same on every start whatever map it came in */
    public ReferenceRefusal(ReferenceRefusalCode code, String message, Map<String, String> details) {
        super(message);
        this.code = code;
        Map<String, String> byName = new TreeMap<>(details == null ? Map.of() : details);
        this.details = Collections.unmodifiableMap(new LinkedHashMap<>(byName));
    }

    public ReferenceRefusalCode code() {
        return code;
    }

    /** The facts behind the refusal, by name. Empty when none. */
    public Map<String, String> details() {
        return details;
    }
}

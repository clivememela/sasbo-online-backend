package za.co.titandynamix.sasbo_online_backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * The one error envelope every endpoint answers with.
 *
 * <p>{@code code} and {@code details} arrived with the ACB module's typed refusals. {@code code} is
 * a stable name the browser may branch on -- a message is copy and gets reworded -- and
 * {@code details} carries the facts a refusal is about, such as the state and version of the run
 * that caused a conflict. Both are {@code null}, and therefore absent from the JSON, on every error
 * that has neither, which is every error outside ACB today.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors,
        String code,
        Map<String, String> details
) {

    /** An error with no refusal code -- the shape every handler answered with before ACB. */
    public ApiErrorResponse(LocalDateTime timestamp, int status, String error, String message,
                            String path, Map<String, String> validationErrors) {
        this(timestamp, status, error, message, path, validationErrors, null, null);
    }
}

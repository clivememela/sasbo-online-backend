package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.titandynamix.sasbo_online_backend.controller.ACBController;

/**
 * How the ACB routes say no -- and ONLY the ACB routes: this advice is scoped to
 * {@link ACBController}, so every other route answers exactly as it did (EH2-A). In effect it is the
 * controller's own handler, kept out of the controller. The envelope itself is the global handler's,
 * built in one place for every refusal.
 *
 * <h2>A request that cannot be read is a coded 400, never a 500 (EH2-A, R1-21)</h2>
 * Malformed JSON, a string where {@code version} wants a number, a missing parameter or part: until
 * this existed each reached the catch-all and answered 500 (register A63). Each is now
 * {@code REQUEST_MALFORMED} with the envelope -- and 🔒 nothing of what was sent, because a parser's
 * message quotes the input, and an ACB body can carry an account number.
 *
 * <h2>Every refusal is said once, without its details (EH2-C)</h2>
 * One {@code INFO} line -- the method, the path and the code -- and one count, {@code acb.refusals}
 * tagged by code. The details stay in the answer and out of the log: they name runs, months and banks
 * for the person at the desk, and a log is read by other people.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = ACBController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AcbExceptionHandler {

    private final GlobalExceptionHandler envelopes;
    private final ObjectProvider<MeterRegistry> meters;

    public AcbExceptionHandler(GlobalExceptionHandler envelopes, ObjectProvider<MeterRegistry> meters) {
        this.envelopes = envelopes;
        this.meters = meters;
    }

    @ExceptionHandler(AcbRefusal.class)
    public ResponseEntity<ApiErrorResponse> refused(AcbRefusal refusal, HttpServletRequest request) {
        log.info("ACB refused {} {}: {}", request.getMethod(), request.getRequestURI(), refusal.code());
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("acb.refusals", "code", refusal.code().name()).increment();
        }
        return envelopes.handleAcbRefusal(refusal, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> unreadable(Exception unreadable, HttpServletRequest request) {
        // 🔒 The exception is not logged either: its message is the parser's, and quotes the input.
        String field = fieldOf(unreadable);
        return refused(new AcbRefusal(AcbRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send JSON with each field of its expected type.",
                field == null ? java.util.Map.of() : java.util.Map.of("field", field)), request);
    }

    /** The NAME of the field or parameter that could not be read, when the exception knows it -- never its value. */
    static String fieldOf(Exception unreadable) {
        if (unreadable instanceof MethodArgumentTypeMismatchException mismatch) {
            return mismatch.getName();
        }
        if (unreadable instanceof MissingServletRequestParameterException missing) {
            return missing.getParameterName();
        }
        if (unreadable instanceof MissingServletRequestPartException missing) {
            return missing.getRequestPartName();
        }
        if (unreadable.getCause() instanceof com.fasterxml.jackson.databind.exc.MismatchedInputException input
                && !input.getPath().isEmpty()) {
            return input.getPath().stream()
                    .map(reference -> reference.getFieldName() != null ? reference.getFieldName()
                            : String.valueOf(reference.getIndex()))
                    .collect(java.util.stream.Collectors.joining("."));
        }
        return null;
    }
}

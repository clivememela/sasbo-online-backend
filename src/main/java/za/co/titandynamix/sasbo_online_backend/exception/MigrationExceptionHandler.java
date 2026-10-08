package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.MigrationApi;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * How the data migration's routes say no -- and only theirs: scoped to the controllers that carry {@link MigrationApi},
 * as month end's handler is to its own, so every other route answers exactly as it did.
 *
 * <ul>
 *   <li>A refusal is the one envelope -- status, sentence, {@code code}, {@code details} -- said once in the log by its
 *       method, path and code, and counted ({@code migration.refusals}, by code). Its details stay out of the log.</li>
 *   <li>A request that cannot be read is {@code REQUEST_MALFORMED}, naming the field and nothing of what was sent; a
 *       field that breaks its rule is {@code REQUEST_INVALID}, naming the fields.</li>
 *   <li>A permission refused by method security is the filter chain's to answer -- 401 or 403 -- never this 500.</li>
 *   <li>🔒 Anything else is the 500 with a reference, logged SCRUBBED: the class and the stack, never the message. A
 *       migration's statements carry members' identity numbers, and a failed statement quotes what it was given.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(annotations = MigrationApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MigrationExceptionHandler {

    private final ObjectProvider<MeterRegistry> meters;

    public MigrationExceptionHandler(ObjectProvider<MeterRegistry> meters) {
        this.meters = meters;
    }

    @ExceptionHandler(MigrationRefusal.class)
    public ResponseEntity<ApiErrorResponse> refused(MigrationRefusal refusal, HttpServletRequest request) {
        log.info("Migration refused {} {}: {}", request.getMethod(), request.getRequestURI(), refusal.code());
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("migration.refusals", "code", refusal.code().name()).increment();
        }
        HttpStatus status = refusal.code().status();
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                status.value(),
                status.getReasonPhrase(),
                refusal.getMessage(),
                request.getRequestURI(),
                null,
                refusal.code().name(),
                refusal.details().isEmpty() ? null : refusal.details()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> unreadable(Exception unreadable, HttpServletRequest request) {
        // 🔒 Not logged: a parser's message quotes the input.
        String field = AcbExceptionHandler.fieldOf(unreadable);
        return refused(new MigrationRefusal(MigrationRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                field == null ? Map.of() : Map.of("field", field)), request);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class})
    public ResponseEntity<ApiErrorResponse> invalid(Exception invalid, HttpServletRequest request) {
        TreeSet<String> fields = new TreeSet<>();
        if (invalid instanceof MethodArgumentNotValidException notValid) {
            notValid.getBindingResult().getFieldErrors().stream().map(FieldError::getField).forEach(fields::add);
        } else if (invalid instanceof HandlerMethodValidationException notValid) {
            notValid.getParameterValidationResults().forEach(result -> {
                String name = result.getMethodParameter().getParameterName();
                if (name != null) {
                    fields.add(name);
                }
            });
        } else if (invalid instanceof ConstraintViolationException violations) {
            violations.getConstraintViolations().forEach(violation -> fields.add(violation.getPropertyPath().toString()));
        }
        return refused(new MigrationRefusal(MigrationRefusalCode.REQUEST_INVALID,
                "A field of this request is missing, too long or out of range. Correct it and send it again.",
                fields.isEmpty() ? Map.of() : Map.of("fields", String.join(",", fields))), request);
    }

    /** A permission refused by method security answers 401/403 from the filter chain, never this handler's 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void securityRefusal(RuntimeException refusal) {
        throw refusal;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> failed(Exception failure, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Migration failed on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference,
                AcbLogging.scrubbed(failure));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong on our side. Quote reference " + reference + " if you report this.",
                request.getRequestURI(),
                null));
    }
}

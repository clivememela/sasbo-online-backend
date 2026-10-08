package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.SignInApi;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * How sign-in's routes say no -- and ONLY sign-in's: scoped to the controllers that carry {@link SignInApi}, so every
 * other route answers exactly as it did (the pattern of {@code MonthEndExceptionHandler}).
 *
 * <ul>
 *   <li>a {@link SignInRefusal}: its code, status and sentence, in the one envelope;</li>
 *   <li>a request that cannot be read: {@code REQUEST_MALFORMED}, naming the field and never its value (a parser's
 *       message quotes the input -- here, possibly a password);</li>
 *   <li>a field that breaks its rule: {@code VALIDATION_FAILED}, with {@code validationErrors} field by field, which the
 *       forms put beside their fields;</li>
 *   <li>a record changed since it was read: {@code STALE_VERSION}; a broken constraint, by its NAME:
 *       {@code USERNAME_TAKEN}, a position's or role's code taken, a scope that is not one;</li>
 *   <li>a permission refused by method security: thrown on to the filter chain, which answers 401 or 403 (oct/base);</li>
 *   <li>anything else: a 500 with a reference, logged SCRUBBED -- the class and the stack, never the message.</li>
 * </ul>
 * Each refusal is said once at INFO -- method, path, code; never the details -- and counted, {@code sign_in.refusals}.
 */
@Slf4j
@RestControllerAdvice(annotations = SignInApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SignInExceptionHandler {

    /** A constraint's name, as Postgres and H2 both print it, to the refusal it means. */
    static final Map<String, SignInRefusalCode> CONSTRAINTS = Map.of(
            "staff_users_username_unique", SignInRefusalCode.USERNAME_TAKEN,
            "staff_positions_code_unique", SignInRefusalCode.POSITION_CODE_TAKEN,
            "staff_roles_code_unique", SignInRefusalCode.ROLE_CODE_TAKEN,
            "staff_user_scope_consistent", SignInRefusalCode.SCOPE_INVALID,
            "staff_user_scope_codes_once", SignInRefusalCode.SCOPE_INVALID,
            "staff_role_permissions_once", SignInRefusalCode.PERMISSION_UNKNOWN);

    private static final Map<SignInRefusalCode, String> CONSTRAINT_SENTENCES = Map.of(
            SignInRefusalCode.USERNAME_TAKEN, "Another user already signs in with that username. Choose another.",
            SignInRefusalCode.POSITION_CODE_TAKEN, "Another position already has that code. Choose another.",
            SignInRefusalCode.ROLE_CODE_TAKEN, "Another role already has that code. Choose another.",
            SignInRefusalCode.SCOPE_INVALID, "That is not a scope a user can have. Choose everything, or at least one "
                    + "region or area, each named once.",
            SignInRefusalCode.PERMISSION_UNKNOWN, "A role holds each permission once.");

    private final ObjectProvider<MeterRegistry> meters;

    public SignInExceptionHandler(ObjectProvider<MeterRegistry> meters) {
        this.meters = meters;
    }

    @ExceptionHandler(SignInRefusal.class)
    public ResponseEntity<ApiErrorResponse> refused(SignInRefusal refusal, HttpServletRequest request) {
        ResponseEntity<ApiErrorResponse> answer = answer(refusal.code(), refusal.getMessage(), refusal.details(), null,
                request);
        String retryAfter = refusal.details().get("retryAfterSeconds");
        if (retryAfter == null) {
            return answer;
        }
        // The throttle's answer says when to come back, in the header a client reads (RFC 9110 10.2.3) as well.
        return ResponseEntity.status(answer.getStatusCode()).header(HttpHeaders.RETRY_AFTER, retryAfter)
                .body(answer.getBody());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalid(MethodArgumentNotValidException invalid,
                                                    HttpServletRequest request) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : invalid.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return answer(SignInRefusalCode.VALIDATION_FAILED, "Some of what was entered needs changing. Each field "
                + "says what.", Map.of(), fields, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> unreadable(Exception unreadable, HttpServletRequest request) {
        // 🔒 Not logged: the parser's message quotes the input, and the input here can be a password.
        String field = AcbExceptionHandler.fieldOf(unreadable);
        return answer(SignInRefusalCode.REQUEST_MALFORMED, "This request could not be read. Send each field in the "
                + "form it is expected in.", field == null ? Map.of() : Map.of("field", field), null, request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> stale(ObjectOptimisticLockingFailureException stale,
                                                  HttpServletRequest request) {
        return answer(SignInRefusalCode.STALE_VERSION, "Somebody else changed this while you had it open. Nothing "
                + "was saved: open it again to see their change, then make yours.", Map.of(), null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> conflict(DataIntegrityViolationException conflict,
                                                     HttpServletRequest request) {
        SignInRefusalCode code = constraintOf(conflict);
        if (code == null) {
            String reference = UUID.randomUUID().toString().substring(0, 8);
            log.warn("Sign-in conflict on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference,
                    AcbLogging.scrubbed(conflict));
            return answer(SignInRefusalCode.RECORD_CONFLICT, "This conflicts with a record already there. Nothing was "
                    + "saved. Quote reference " + reference + " if you need help.", Map.of(), null, request);
        }
        return answer(code, CONSTRAINT_SENTENCES.get(code), Map.of(), null, request);
    }

    /** A permission refused by method security answers 401/403 from the filter chain, never this handler's 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void securityRefusal(RuntimeException refusal) {
        throw refusal;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> failed(Exception failure, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Sign-in failed on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference,
                AcbLogging.scrubbed(failure));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")), HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Something went wrong on our side. Quote reference " + reference + " if you report this.",
                request.getRequestURI(), null));
    }

    /** The refusal a broken constraint means, read off its NAME anywhere in the cause chain; null for any other. */
    static SignInRefusalCode constraintOf(Throwable conflict) {
        for (Throwable cause = conflict; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, SignInRefusalCode> constraint : CONSTRAINTS.entrySet()) {
                if (lower.contains(constraint.getKey())) {
                    return constraint.getValue();
                }
            }
        }
        return null;
    }

    private ResponseEntity<ApiErrorResponse> answer(SignInRefusalCode code, String message,
                                                    Map<String, String> details, Map<String, String> fields,
                                                    HttpServletRequest request) {
        log.info("Sign-in refused {} {}: {}", request.getMethod(), request.getRequestURI(), code);
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("sign_in.refusals", "code", code.name()).increment();
        }
        HttpStatus status = code.status();
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                LocalDateTime.now(ZoneId.of("UTC")), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), fields == null || fields.isEmpty() ? null : new LinkedHashMap<>(fields),
                code.name(), details == null || details.isEmpty() ? null : new LinkedHashMap<>(details)));
    }
}

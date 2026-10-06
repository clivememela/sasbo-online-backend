package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.MonthEndApi;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * How the month-end routes say no -- and ONLY the month-end routes: this advice is scoped to the controllers that
 * carry {@link MonthEndApi}, so every other route of the application answers exactly as it did (MD49, ME-11). The
 * envelope itself is the global handler's, built in one place for every refusal.
 *
 * <h2>A request that cannot be read is a coded 400, never a 500 (ME-11)</h2>
 * Malformed JSON, a word where a page number is wanted, a missing parameter or part, a content type the route does
 * not take: each is {@code REQUEST_MALFORMED}, and the details carry the NAME of the field -- 🔒 never its value,
 * because a parser's message quotes the input. Elsewhere in the API the same request still answers 500 (register
 * A63), and that is proved unmoved (MV-35).
 *
 * <h2>Every refusal is said once, without its details (ME-20)</h2>
 * One {@code INFO} line -- the method, the path and the code -- and one count, {@code month_end.refusals} tagged by
 * code. The details stay in the answer and out of the log.
 *
 * <h2>🔒 What this arc's own statements throw never reaches the global handler (MR-SEC-20)</h2>
 * The global catch-all logs an exception whole, and an exception's message is a value: a failed statement quotes what
 * it was given. So anything else a month-end route throws is answered here with the same 500 and its reference, and
 * logged scrubbed -- the exception's class and stack, never its message.
 */
@Slf4j
@RestControllerAdvice(annotations = MonthEndApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonthEndExceptionHandler {

    private final GlobalExceptionHandler envelopes;
    private final ObjectProvider<MeterRegistry> meters;

    public MonthEndExceptionHandler(GlobalExceptionHandler envelopes, ObjectProvider<MeterRegistry> meters) {
        this.envelopes = envelopes;
        this.meters = meters;
    }

    @ExceptionHandler(MonthEndRefusal.class)
    public ResponseEntity<ApiErrorResponse> refused(MonthEndRefusal refusal, HttpServletRequest request) {
        log.info("Month end refused {} {}: {}", request.getMethod(), request.getRequestURI(), refusal.code());
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("month_end.refusals", "code", refusal.code().name()).increment();
        }
        ResponseEntity<ApiErrorResponse> answer = envelopes.handleMonthEndRefusal(refusal, request);
        if (refusal.code() == MonthEndRefusalCode.SUMMARY_UNAVAILABLE) {
            // 02.2 (MD47, MD63): the figures are asked for again in a moment, never queued.
            return ResponseEntity.status(answer.getStatusCode()).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                    .body(answer.getBody());
        }
        return answer;
    }

    /** How long a screen waits before asking for the figures again, when they were unavailable (02.2). */
    public static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class})
    public ResponseEntity<ApiErrorResponse> unreadable(Exception unreadable, HttpServletRequest request) {
        // 🔒 The exception is not logged either: its message is the parser's, and quotes the input.
        String field = AcbExceptionHandler.fieldOf(unreadable);
        return refused(new MonthEndRefusal(MonthEndRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                field == null ? Map.of() : Map.of("field", field)), request);
    }

    /** A permission refused by method security answers 401/403 from the filter chain, never this arc's 500. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void securityRefusal(RuntimeException refusal) {
        throw refusal;
    }

    /**
     * Anything else a month-end route throws: the 500 the global catch-all answers, with its reference -- but logged
     * SCRUBBED, the class and the stack without the message (MR-SEC-20).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> failed(Exception failure, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Month end failed on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference,
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

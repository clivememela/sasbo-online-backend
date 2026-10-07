package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.titandynamix.sasbo_online_backend.acb.AcbLogging;
import za.co.titandynamix.sasbo_online_backend.config.PayrollApi;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollNoteRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDownloadRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollResolveRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollSettingsRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * How the payroll routes say no -- and ONLY the payroll routes: scoped to the controllers that carry {@link PayrollApi},
 * so every other route answers exactly as it did. The envelope is the global handler's, built in one place.
 *
 * <ul>
 *   <li>A request that cannot be read is a coded 400, never a 500, naming the field and 🔒 nothing of what was sent --
 *       a parser's message quotes its input, and a payroll line is personal data.</li>
 *   <li>A body that fails its constraints is the refusal of the thing it was for -- a note {@code NOTE_INVALID}, a
 *       payment {@code PAYMENT_INVALID} -- so the screen says the same whichever check caught it.</li>
 *   <li>A missing permission is not answered here: it is handed back to the security filter chain, which answers it
 *       as it answers every route (the sign-in stream's {@code PERMISSION_REQUIRED}). Caught here it would be a 500.</li>
 *   <li>Every refusal is said once in the log -- the method, the path and the code -- and counted; the details stay
 *       out of the log. Anything else a payroll route throws is a 500 with a reference, logged SCRUBBED: the class and
 *       the stack, never the message, which may quote a line of a file.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice(annotations = PayrollApi.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PayrollExceptionHandler {

    private final GlobalExceptionHandler envelopes;
    private final ObjectProvider<MeterRegistry> meters;

    public PayrollExceptionHandler(GlobalExceptionHandler envelopes, ObjectProvider<MeterRegistry> meters) {
        this.envelopes = envelopes;
        this.meters = meters;
    }

    @ExceptionHandler(PayrollRefusal.class)
    public ResponseEntity<ApiErrorResponse> refused(PayrollRefusal refusal, HttpServletRequest request) {
        log.info("Payroll refused {} {}: {}", request.getMethod(), request.getRequestURI(), refusal.code());
        MeterRegistry registry = meters.getIfAvailable();
        if (registry != null) {
            registry.counter("payroll.refusals", "code", refusal.code().name()).increment();
        }
        return envelopes.handlePayrollRefusal(refusal, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class,
            HttpMediaTypeNotSupportedException.class, MultipartException.class})
    public ResponseEntity<ApiErrorResponse> unreadable(Exception unreadable, HttpServletRequest request) {
        // 🔒 Not logged either: its message is the parser's, and quotes the input.
        if (unreadable instanceof MissingServletRequestPartException missing && "file".equals(missing.getRequestPartName())) {
            return refused(new PayrollRefusal(PayrollRefusalCode.FILE_MISSING,
                    "Choose the employer's file to load."), request);
        }
        String field = AcbExceptionHandler.fieldOf(unreadable);
        return refused(new PayrollRefusal(PayrollRefusalCode.REQUEST_MALFORMED,
                "This request could not be read. Send each field in the form it is expected in.",
                field == null ? Map.of() : Map.of("field", field)), request);
    }

    /** A body that fails its constraints: the refusal of what the body was for, naming the field and not its value. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> invalid(MethodArgumentNotValidException invalid, HttpServletRequest request) {
        FieldError first = invalid.getBindingResult().getFieldError();
        String field = first == null ? null : first.getField();
        Object target = invalid.getBindingResult().getTarget();
        PayrollRefusal refusal;
        if (target instanceof PayrollNoteRequest || target instanceof PayrollResolveRequest && "note".equals(field)) {
            refusal = new PayrollRefusal(PayrollRefusalCode.NOTE_INVALID,
                    "Write a note of up to 1,000 characters saying what was done.", detail(field));
        } else if (target instanceof PayrollPaymentRequest) {
            refusal = new PayrollRefusal(PayrollRefusalCode.PAYMENT_INVALID,
                    "The payment could not be recorded. Check the employer, the month, the amount and the date.",
                    detail(field));
        } else if (target instanceof PayrollTypedEntryRequest) {
            refusal = new PayrollRefusal(PayrollRefusalCode.TYPED_ROWS_INVALID,
                    "Some typed rows could not be taken. Each row needs an amount and a staff number, an ID number "
                            + "or a surname.", typedRowDetail(field));
        } else if (target instanceof PayrollQueryDownloadRequest && "password".equals(field)) {
            refusal = new PayrollRefusal(PayrollRefusalCode.PASSWORD_INVALID,
                    "Choose a password of 8 to 64 characters for the employer's list.");
        } else if (target instanceof PayrollSettingsRequest) {
            refusal = new PayrollRefusal(PayrollRefusalCode.SETTINGS_INVALID,
                    "The settings could not be saved. The fee is a percentage from 0 to 99.99.", detail(field));
        } else {
            refusal = new PayrollRefusal(PayrollRefusalCode.REQUEST_MALFORMED,
                    "This request could not be read. Send each field in the form it is expected in.", detail(field));
        }
        return refused(refusal, request);
    }

    private static Map<String, String> detail(String field) {
        return field == null ? Map.of() : Map.of("field", field);
    }

    /** A typed row's field, as the screen numbers rows -- {@code rows[3].amount} is row 4's amount. */
    static Map<String, String> typedRowDetail(String field) {
        java.util.regex.Matcher row = field == null ? null
                : java.util.regex.Pattern.compile("rows\\[(\\d{1,5})]\\.(\\w{1,20})").matcher(field);
        if (row != null && row.matches()) {
            return PayrollRefusal.details("row", String.valueOf(Integer.parseInt(row.group(1)) + 1),
                    "field", row.group(2));
        }
        return detail(field);
    }

    /**
     * A permission refused by method security answers 401/403 from the filter chain, never this module's 500 -- thrown
     * on unchanged, as {@code GlobalExceptionHandler} and {@code MonthEndExceptionHandler} do (oct/base).
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void securityRefusal(RuntimeException refusal) {
        throw refusal;
    }

    /** Anything else: the 500 the global catch-all answers, with its reference -- logged scrubbed. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> failed(Exception failure, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Payroll failed on {} {} [ref {}]", request.getMethod(), request.getRequestURI(), reference,
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

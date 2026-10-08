package za.co.titandynamix.sasbo_online_backend.exception;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollNoteRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentRequest;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollTypedEntryRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How the payroll routes say no: a refusal is the envelope with its code and details, counted; a request that cannot
 * be read is a coded 400 naming the field and nothing of what was sent; a body that fails its constraints is the
 * refusal of what it was for; a missing permission is handed back to the security chain; anything else is a 500
 * with a reference, and its message goes nowhere.
 */
@DisplayName("PayrollExceptionHandler")
class PayrollExceptionHandlerTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final PayrollExceptionHandler handler = new PayrollExceptionHandler(new GlobalExceptionHandler(),
            provider(meters));

    private static HttpServletRequest request() {
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getMethod()).thenReturn("POST");
        Mockito.when(request.getRequestURI()).thenReturn("/api/payroll/files");
        return request;
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MeterRegistry> provider(MeterRegistry registry) {
        ObjectProvider<MeterRegistry> provider = Mockito.mock(ObjectProvider.class);
        Mockito.when(provider.getIfAvailable()).thenReturn(registry);
        return provider;
    }

    @Test
    @DisplayName("a refusal answers its status, code, message and details -- and is counted by code")
    void aRefusal() {
        ResponseEntity<ApiErrorResponse> answer = handler.refused(new PayrollRefusal(PayrollRefusalCode.TOO_MANY_ROWS,
                "Too many.", Map.of("limit", "20000")), request());
        assertThat(answer.getStatusCode().value()).isEqualTo(422);
        assertThat(answer.getBody().code()).isEqualTo("TOO_MANY_ROWS");
        assertThat(answer.getBody().details()).containsEntry("limit", "20000");
        assertThat(meters.counter("payroll.refusals", "code", "TOO_MANY_ROWS").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("a word where a number is wanted is REQUEST_MALFORMED, naming the parameter and nothing of its value")
    void anUnreadableParameter() {
        MethodArgumentTypeMismatchException mismatch = new MethodArgumentTypeMismatchException("PLANTED-VALUE",
                Long.class, "employerId", null, new NumberFormatException("For input string: \"PLANTED-VALUE\""));
        ResponseEntity<ApiErrorResponse> answer = handler.unreadable(mismatch, request());
        assertThat(answer.getBody().code()).isEqualTo("REQUEST_MALFORMED");
        assertThat(answer.getBody().details()).containsEntry("field", "employerId");
        assertThat(answer.getBody().toString()).doesNotContain("PLANTED-VALUE");
    }

    @Test
    @DisplayName("an upload with no file part is FILE_MISSING")
    void noFilePart() {
        assertThat(handler.unreadable(new MissingServletRequestPartException("file"), request()).getBody().code())
                .isEqualTo("FILE_MISSING");
    }

    @Test
    @DisplayName("a body failing its constraints is the refusal of what it was for: a note, a payment, typed rows")
    void constraintFailures() throws Exception {
        assertThat(handler.invalid(invalid(new PayrollNoteRequest(""), "text"), request()).getBody().code())
                .isEqualTo("NOTE_INVALID");
        assertThat(handler.invalid(invalid(new PayrollPaymentRequest(1L, "2026-09", "", "2026-09-30", null), "amount"),
                request()).getBody().code()).isEqualTo("PAYMENT_INVALID");
        ResponseEntity<ApiErrorResponse> typed = handler.invalid(invalid(new PayrollTypedEntryRequest(1L, "2026-09",
                null, List.of()), "rows[3].amount"), request());
        assertThat(typed.getBody().code()).isEqualTo("TYPED_ROWS_INVALID");
        assertThat(typed.getBody().details()).containsEntry("row", "4").containsEntry("field", "amount");
    }

    @Test
    @DisplayName("a missing permission is thrown on to the security chain -- never answered here as a 500")
    void aMissingPermission() {
        AccessDeniedException denied = new AccessDeniedException("Access Denied");
        assertThatThrownBy(() -> handler.securityRefusal(denied)).isSameAs(denied);
    }

    @Test
    @DisplayName("anything else is a 500 with a reference, and the exception's message -- which may quote a line -- goes nowhere")
    void anythingElse() {
        ResponseEntity<ApiErrorResponse> answer = handler.failed(new IllegalStateException("Testerson 8106145689086"),
                request());
        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody().message()).startsWith("Something went wrong on our side. Quote reference ");
        assertThat(answer.getBody().toString()).doesNotContain("8106145689086");
    }

    private static MethodArgumentNotValidException invalid(Object target, String field) throws Exception {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(target, "request");
        result.addError(new FieldError("request", field, "is not valid"));
        MethodParameter parameter = new MethodParameter(PayrollExceptionHandlerTest.class.getDeclaredMethod("request"), -1);
        return new MethodArgumentNotValidException(parameter, result);
    }
}

package za.co.titandynamix.sasbo_online_backend.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The handler that turns an exception into the envelope every controller test
 * asserts on.
 *
 * <h2>Why it needed its own tests</h2>
 * It was untested, and it is the machinery underneath every error assertion in
 * the suite. Untested, all of those rest on it by accident: if the envelope
 * changed shape, a dozen controller tests would fail with a dozen confusing
 * messages and none of them would point here.
 *
 * <h2>What is actually assertable</h2>
 * The {@code exception} package holds exactly two files -- this handler and the
 * {@code ApiErrorResponse} record. There are no custom exception types and no
 * {@code code} field. So the three stable things, and the only three worth
 * asserting, are <b>the HTTP status</b>, the <b>{@code validationErrors} field
 * names</b>, and <b>the envelope's own shape</b>. {@code message} is
 * {@code ex.getMessage()} -- free text that changes whenever anyone rewords a
 * {@code throw} -- and asserting it would be asserting copy.
 *
 * <p>A typed {@code code} per domain failure is the right design and it is a
 * production change: new exception types plus an envelope field. It is written
 * up as a finding rather than built here.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        this.handler = new GlobalExceptionHandler();
        this.request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(this.request.getRequestURI()).thenReturn("/api/members/99");
    }

    @Test
    @DisplayName("a missing entity becomes 404 with the full envelope")
    void entityNotFoundBecomes404() {
        ResponseEntity<ApiErrorResponse> response = this.handler.handleEntityNotFound(
                new EntityNotFoundException("Member 99 not found"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).isEqualTo("Not Found");
        assertThat(body.path()).isEqualTo("/api/members/99");
        assertThat(body.timestamp()).isNotNull();
        // No assertion on message. It is ex.getMessage() -- copy, not contract.
        assertThat(body.validationErrors())
                .as("a 404 carries no field-level errors, and a non-null empty map here would "
                        + "read to the client as 'validation failed with no reasons'")
                .isNull();
    }

    @Test
    @DisplayName("a validation failure becomes 400 carrying the field names, not the messages")
    void validationFailureBecomes400WithFieldNames() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "memberDto");
        binding.addError(new org.springframework.validation.FieldError(
                "memberDto", "surname", "must not be blank"));
        binding.addError(new org.springframework.validation.FieldError(
                "memberDto", "identityNumber", "must be 13 digits"));

        ResponseEntity<ApiErrorResponse> response = this.handler.handleValidationFailures(
                new MethodArgumentNotValidException(someMethodParameter(), binding), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(400);
        assertThat(body.error()).isEqualTo("Bad Request");
        // The field NAMES are the contract; the messages beside them are copy.
        assertThat(body.validationErrors()).containsOnlyKeys("surname", "identityNumber");
    }

    @Test
    @DisplayName("an illegal argument becomes 400, not 500")
    void illegalArgumentBecomes400() {
        // MemberValidator throws IllegalArgumentException for a bad debit day.
        // Without this handler that is a 500, and the browser cannot tell a
        // rejected payload from a broken server.
        ResponseEntity<ApiErrorResponse> response = this.handler.handleIllegalArgument(
                new IllegalArgumentException("Debit day must be one of 20, 25 or 31"),
                this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    @Test
    @DisplayName("an unmapped exception becomes 500 with the envelope intact")
    void unmappedExceptionBecomes500() {
        ResponseEntity<ApiErrorResponse> response = this.handler.handleGeneralException(
                new IllegalStateException("something broke"), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(500);
        assertThat(response.getBody().error()).isEqualTo("Internal Server Error");
        assertThat(response.getBody().path()).isEqualTo("/api/members/99");
    }

    @Test
    @DisplayName("a 500 body carries no SQL, no table or column name and no bound value")
    void theCatchAllDoesNotLeakInternals() {
        // A real DataIntegrityViolationException message, in the shape the
        // driver produces: the statement, the constraint, the table and the
        // bound parameters -- which on this application means an identity
        // number and a bank account number.
        String driverMessage = "could not execute statement [ERROR: duplicate key value violates "
                + "unique constraint \"members_identity_number_key\"  Detail: Key "
                + "(identity_number)=(9404275800086) already exists.] "
                + "[insert into members (bank_account_number,identity_number,surname) "
                + "values (?,?,?)]";

        ResponseEntity<ApiErrorResponse> response = this.handler.handleGeneralException(
                new DataIntegrityViolationException(driverMessage), this.request);

        String body = String.valueOf(response.getBody().message());

        assertThat(body)
                .as("the 500 body carries the SQL statement to the caller. With /api/** on "
                        + "permitAll(), anyone who can trigger a 500 reads it anonymously")
                .doesNotContain("insert into")
                .doesNotContain("members")
                .doesNotContain("identity_number")
                .doesNotContain("9404275800086");
    }

    @Test
    @DisplayName("the 404 and 400 paths keep their messages, deliberately")
    void theCallerFacingHandlersKeepTheirMessages() {
        // The companion to the disabled test above, and the reason that finding
        // is a one-line fix rather than a rewrite. These messages are written
        // for the caller and are meant to travel; only the catch-all copies an
        // internal one.
        ResponseEntity<ApiErrorResponse> notFound = this.handler.handleEntityNotFound(
                new EntityNotFoundException("Member 99 not found"), this.request);
        ResponseEntity<ApiErrorResponse> badRequest = this.handler.handleIllegalArgument(
                new IllegalArgumentException("Debit day must be one of 20, 25 or 31"),
                this.request);

        assertThat(notFound.getBody().message()).isNotBlank();
        assertThat(badRequest.getBody().message()).isNotBlank();
    }

    @Test
    @Disabled(KnownDefects.VALIDATION_HANDLER_CANNOT_TAKE_A_GLOBAL_ERROR)
    @DisplayName("a class-level constraint failure is a 400, not a ClassCastException")
    void aGlobalValidationErrorIsHandled() throws Exception {
        // Found by writing the test above. getAllErrors() returns ObjectError,
        // and only SOME of those are FieldError -- a class-level constraint
        // such as @AssertTrue on the DTO, or any cross-field rule, produces the
        // plain kind. The handler casts unconditionally.
        //
        // Latent today because no DTO here carries a class-level constraint. It
        // goes live the first time somebody writes one, and the symptom is a
        // 500 with a ClassCastException on a payload the browser had already
        // rejected -- which points nowhere near this line.
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "memberDto");
        binding.reject("PaymentDetailsIncomplete", "banking details are required for a debit order");

        ResponseEntity<ApiErrorResponse> response = this.handler.handleValidationFailures(
                new MethodArgumentNotValidException(someMethodParameter(), binding), this.request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
    }

    /** Any real method parameter -- {@code MethodArgumentNotValidException} needs one. */
    private static MethodParameter someMethodParameter() throws NoSuchMethodException {
        Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod(
                "aMethodWithAParameter", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void aMethodWithAParameter(String payload) {
        // Exists only to give MethodParameter something real to point at.
    }
}

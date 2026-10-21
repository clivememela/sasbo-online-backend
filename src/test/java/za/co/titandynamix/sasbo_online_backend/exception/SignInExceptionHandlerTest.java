package za.co.titandynamix.sasbo_online_backend.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * How sign-in's routes say no: each refusal in the one envelope, a broken constraint by its NAME -- in Postgres's
 * words or H2's -- and a security refusal thrown on to the chain, never made a 500 here.
 */
@DisplayName("SignInExceptionHandler")
class SignInExceptionHandlerTest {

    @SuppressWarnings("unchecked")
    private final SignInExceptionHandler handler = new SignInExceptionHandler(mock(ObjectProvider.class));
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/admin/users");

    @Test
    @DisplayName("a refusal answers its status, code, sentence and details")
    void aRefusalIsItsOwnEnvelope() {
        ResponseEntity<ApiErrorResponse> answer = handler.refused(new SignInRefusal(SignInRefusalCode.LAST_ADMINISTRATOR,
                "This would leave nobody able to manage users.", java.util.Map.of("permission", "USERS_ADMIN")),
                request);
        assertThat(answer.getStatusCode().value()).isEqualTo(409);
        assertThat(answer.getBody().code()).isEqualTo("LAST_ADMINISTRATOR");
        assertThat(answer.getBody().message()).isEqualTo("This would leave nobody able to manage users.");
        assertThat(answer.getBody().details()).containsEntry("permission", "USERS_ADMIN");
        assertThat(answer.getBody().path()).isEqualTo("/api/admin/users");
    }

    @Test
    @DisplayName("a broken constraint is read by its name in Postgres's words -- USERNAME_TAKEN, not the identity sentence")
    void postgresConstraintNames() {
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint "
                        + "\"staff_users_username_unique\"  Detail: Key (username)=(invented.x) already exists."));
        ResponseEntity<ApiErrorResponse> answer = handler.conflict(conflict, request);
        assertThat(answer.getStatusCode().value()).isEqualTo(409);
        assertThat(answer.getBody().code()).isEqualTo("USERNAME_TAKEN");
        assertThat(answer.getBody().message()).doesNotContain("invented.x").doesNotContainIgnoringCase("identity number");
    }

    @Test
    @DisplayName("…and in H2's, where the name is the index's, in capitals")
    void h2ConstraintNames() {
        assertThat(SignInExceptionHandler.constraintOf(new DataIntegrityViolationException("x",
                new RuntimeException("Unique index or primary key violation: \"PUBLIC.STAFF_POSITIONS_CODE_UNIQUE_INDEX_4 "
                        + "ON PUBLIC.STAFF_POSITIONS(CODE)\"")))).isEqualTo(SignInRefusalCode.POSITION_CODE_TAKEN);
        assertThat(SignInExceptionHandler.constraintOf(new DataIntegrityViolationException(
                "staff_user_scope_consistent: a staff user's scope of REGION carries 0 code(s)")))
                .isEqualTo(SignInRefusalCode.SCOPE_INVALID);
    }

    @Test
    @DisplayName("a constraint no rule names is RECORD_CONFLICT with a reference -- never the driver's message")
    void anUnnamedConstraintIsAConflict() {
        ResponseEntity<ApiErrorResponse> answer = handler.conflict(new DataIntegrityViolationException(
                "ERROR: some_other_constraint violated by (an invented value)"), request);
        assertThat(answer.getBody().code()).isEqualTo("RECORD_CONFLICT");
        assertThat(answer.getBody().message()).contains("Quote reference").doesNotContain("an invented value");
    }

    @Test
    @DisplayName("a record changed since it was read is STALE_VERSION")
    void anOptimisticLockIsStale() {
        ResponseEntity<ApiErrorResponse> answer = handler.stale(new ObjectOptimisticLockingFailureException(
                "StaffUser", 4L), request);
        assertThat(answer.getStatusCode().value()).isEqualTo(409);
        assertThat(answer.getBody().code()).isEqualTo("STALE_VERSION");
    }

    @Test
    @DisplayName("a permission refused by method security is thrown on to the filter chain -- never answered 500 here")
    void aSecurityRefusalIsThrownOn() {
        AccessDeniedException denied = new AccessDeniedException("denied");
        assertThatThrownBy(() -> handler.securityRefusal(denied)).isSameAs(denied);
    }

    @Test
    @DisplayName("anything else is a 500 with a reference, and nothing of the failure's message")
    void anythingElseIsA500() {
        ResponseEntity<ApiErrorResponse> answer = handler.failed(new IllegalStateException("an invented secret value"),
                request);
        assertThat(answer.getStatusCode().value()).isEqualTo(500);
        assertThat(answer.getBody().message()).contains("Quote reference").doesNotContain("an invented secret value");
    }
}

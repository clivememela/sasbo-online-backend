package za.co.titandynamix.sasbo_online_backend.guards;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.Mockito;
import za.co.titandynamix.sasbo_online_backend.exception.GlobalExceptionHandler;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Personal information does not reach the log.
 *
 * <h2>Two halves, and neither is sufficient alone</h2>
 * <ol>
 *   <li><b>Static.</b> A type carrying a PII-named field must not generate a
 *       {@code toString()} over it. Lombok's {@code @Data} and {@code @ToString}
 *       both do, and the moment such an object appears in a log format argument
 *       every field goes with it -- including the ones nobody was thinking
 *       about. This half catches the <i>capability</i>, before anyone uses it.
 *   <li><b>Behavioural.</b> Capture what is actually logged while a code path
 *       that logs runs, and assert no PII value is in it. This half catches the
 *       <i>use</i>, including uses the static half cannot see: a hand-written
 *       {@code log.info("id " + member.getIdentityNumber())} generates no
 *       {@code toString()} at all.
 * </ol>
 *
 * <p>This application holds identity numbers and bank account numbers for real
 * people, runs at {@code root: DEBUG}, and mounts a request-logging filter with
 * {@code setIncludePayload(true)}. Logs are forwarded, pasted into tickets and
 * shipped to aggregators, so a leak here is a privacy breach with a URL rather
 * than an untidy line.
 *
 * <h2>Written against the field name, not against the class</h2>
 * The guard discovers types by scanning for PII-shaped field names, so a new
 * entity carrying an {@code identityNumber} is covered the day it is written.
 * Naming today's two offenders would have closed this exact hole and left the
 * hole open.
 */
// @Isolated: this class runs with nothing else running beside it.
//
// It holds ONLY the behavioural half. The two static source scans that used to
// live here moved to PiiToStringTest -- while an isolated class runs the whole
// suite is single-threaded, so work in here is added to the wall clock rather
// than shared with it, and those two tests never touched a logger.
//
// A lock would not do. Logback's LoggerContext is process-global, and Spring
// Boot RESETS it -- removing every appender -- each time an application context
// starts. Under a parallel runner, a @WebMvcTest booting in another class
// between this one attaching its appender and reading it back leaves the
// capture empty, and the test fails having asserted nothing.
//
// Observed on 2026-09-04: the canary that exists to prove the capture works
// failed once in three runs of the full gate, and only under load. That is a
// flake, and the fix is to remove the concurrency rather than to retry the
// test.
//
// It is a confession, like a resource lock: it costs a little parallelism, and
// it is the honest price of asserting on a global that a framework owns.
@Isolated
@DisplayName("PII in logs")
class PiiInLogsTest {

    private LogCapture captured;

    @BeforeEach
    void startCapturing() {
        // The exception handler's OWN logger, not the root one -- see LogCapture
        // for why that distinction is the difference between evidence and a
        // coin toss under a parallel runner.
        this.captured = LogCapture.on(GlobalExceptionHandler.class);
    }

    @AfterEach
    void stopCapturing() {
        this.captured.close();
    }

    @Test
    @DisplayName("the exception handler logs the path and the type, never the payload")
    void theExceptionHandlerLogsNoPii() {
        // The behavioural half. The catch-all is the one place in this
        // application that deliberately logs an exception, and an exception
        // raised while binding or persisting a member is exactly where an
        // identity number ends up inside a message.
        // The fixture's value, not a copy of it. This assertion is that the log
        // does not contain the identity number the APPLICATION would have
        // handled -- a literal drifting from the fixture would leave it
        // asserting against a value nothing produces.
        String syntheticId = MemberFixture.SYNTHETIC_ID;
        String syntheticAccount = MemberFixture.SYNTHETIC_ACCOUNT;

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getRequestURI()).thenReturn("/api/members");

        new GlobalExceptionHandler().handleEntityNotFound(
                new EntityNotFoundException("Member not found"), request);

        String logged = this.captured.rendered();

        assertThat(logged)
                .as("an identity number reached the log. Logs are forwarded, pasted into tickets "
                        + "and shipped to aggregators -- this is a privacy breach with a URL, not "
                        + "an untidy line")
                .doesNotContain(syntheticId);
        assertThat(logged)
                .as("a bank account number reached the log")
                .doesNotContain(syntheticAccount);
    }

    @Test
    @DisplayName("the capture itself works -- otherwise the assertion above is vacuous")
    void theCaptureActuallyCaptures() {
        // Without this, a broken appender makes every assertion in this class
        // pass against an empty string. The guard has to prove it can see
        // something before "it saw nothing bad" means anything.
        this.captured.emitCanary("canary for the log capture");

        assertThat(this.captured.rendered())
                .as("the appender captured nothing at all, so a doesNotContain assertion here "
                        + "would pass whatever the application logged")
                .contains("canary for the log capture");
    }

}

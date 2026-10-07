package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A failure is logged with its class and its stack, and never with its message -- because the
 * message is where a JDBC driver prints the row it was inserting.
 */
@DisplayName("AcbLogging -- a failure, scrubbed")
class AcbLoggingTest {

    private static final String SYNTHETIC_ACCOUNT = "6200999911";

    @Test
    @DisplayName("keeps every class and frame, cause included, and drops every message")
    void scrubbedKeepsTheShapeAndDropsTheData() {
        IllegalStateException cause = new IllegalStateException("bound value " + SYNTHETIC_ACCOUNT);
        RuntimeException failure = new RuntimeException("Batch entry 0 INSERT ... " + SYNTHETIC_ACCOUNT, cause);

        Throwable scrubbed = AcbLogging.scrubbed(failure);
        String printed = print(scrubbed);

        assertThat(printed).doesNotContain(SYNTHETIC_ACCOUNT);
        assertThat(printed).contains(RuntimeException.class.getName())
                .contains(IllegalStateException.class.getName());
        assertThat(scrubbed.getStackTrace()).isEqualTo(failure.getStackTrace());
        assertThat(scrubbed.getCause()).isNotNull();
        assertThat(scrubbed.getCause().getStackTrace()).isEqualTo(cause.getStackTrace());
    }

    @Test
    @DisplayName("the must-pass twin: the unscrubbed failure DOES carry the number -- the test can see it")
    void theUnscrubbedFailureCarriesIt() {
        RuntimeException failure = new RuntimeException("bound value " + SYNTHETIC_ACCOUNT);
        assertThat(print(failure)).contains(SYNTHETIC_ACCOUNT);
    }

    @Test
    @DisplayName("the SQL state of the database's refusal, read through its wrappers -- the kind of refusal, which the scrubbed copy loses")
    void theSqlStateIsNamed() {
        java.sql.SQLException timedOut = new java.sql.SQLException("canceling statement ... " + SYNTHETIC_ACCOUNT, "57014");
        RuntimeException failure = new RuntimeException("wrapped", new IllegalStateException("translated", timedOut));

        assertThat(AcbLogging.sqlState(failure)).isEqualTo("57014");
        assertThat(AcbLogging.sqlState(timedOut)).isEqualTo("57014");
    }

    @Test
    @DisplayName("🔒 only the standard's five characters are given back: no refusal, no state, or anything else is 'none'")
    void anythingElseIsNone() {
        assertThat(AcbLogging.sqlState(new IllegalStateException(SYNTHETIC_ACCOUNT))).isEqualTo("none");
        assertThat(AcbLogging.sqlState(new java.sql.SQLException("no state"))).isEqualTo("none");
        assertThat(AcbLogging.sqlState(new java.sql.SQLException("a driver's own", SYNTHETIC_ACCOUNT))).isEqualTo("none");
        assertThat(AcbLogging.sqlState(new java.sql.SQLException("lower case", "57p01"))).isEqualTo("none");
    }

    private static String print(Throwable t) {
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}

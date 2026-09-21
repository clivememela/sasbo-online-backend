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

    private static String print(Throwable t) {
        StringWriter out = new StringWriter();
        t.printStackTrace(new PrintWriter(out));
        return out.toString();
    }
}

package za.co.titandynamix.sasbo_online_backend.acb;

/**
 * What ACB may put in a log line when something fails.
 *
 * <h2>The rule</h2>
 * Run ids, line ids and membership numbers may be logged. Account numbers, identity numbers, the
 * bureau file's bytes and a letter's body never. ACB never passes those to a log call -- and an
 * EXCEPTION'S MESSAGE is exactly where one would sneak in: a failed JDBC batch prints the row it was
 * inserting, bound values and all, and a run's rows are account numbers.
 *
 * <h2>So a failure is logged scrubbed</h2>
 * {@link #scrubbed(Throwable)} keeps what a person debugging needs -- every exception's class and
 * every stack frame, the causes included -- and drops every message. The stack says where it
 * failed; the class says what kind of failure; neither says whose account it was.
 */
public final class AcbLogging {

    private AcbLogging() {
    }

    /** A copy of {@code failure} with every message removed and every frame and cause kept. */
    public static Throwable scrubbed(Throwable failure) {
        return scrub(failure, 0);
    }

    private static Throwable scrub(Throwable failure, int depth) {
        Throwable cause = failure.getCause() != null && failure.getCause() != failure && depth < 10
                ? scrub(failure.getCause(), depth + 1)
                : null;
        Scrubbed copy = new Scrubbed(failure.getClass().getName(), cause);
        copy.setStackTrace(failure.getStackTrace());
        return copy;
    }

    /** What a SQL state is: two characters of class, three of subclass, letters and digits -- a code, never data. */
    private static final java.util.regex.Pattern SQL_STATE = java.util.regex.Pattern.compile("[0-9A-Z]{5}");

    /**
     * The SQL state of the first database refusal among {@code failure} and its causes, or {@code "none"}: "57014", a
     * statement cancelled at its time limit; "23505", a unique index. What KIND of refusal it was, which the scrubbed
     * copy cannot say once its message is gone -- and nothing of whose row: only a string of the standard's five
     * characters is given back, anything else as {@code "none"}.
     */
    public static String sqlState(Throwable failure) {
        Throwable at = failure;
        for (int depth = 0; at != null && depth < 10; depth++) {
            if (at instanceof java.sql.SQLException refused) {
                String state = refused.getSQLState();
                return state != null && SQL_STATE.matcher(state).matches() ? state : "none";
            }
            at = at.getCause() == at ? null : at.getCause();
        }
        return "none";
    }

    /** Stands in for a failure whose message may not be logged. Its message is the class name. */
    private static final class Scrubbed extends RuntimeException {

        private static final long serialVersionUID = 1L;

        Scrubbed(String originalClass, Throwable cause) {
            super(originalClass + " (message withheld: it may carry member data)", cause);
        }
    }
}

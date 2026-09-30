package za.co.titandynamix.sasbo_online_backend.enums;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.TransactionTimedOutException;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * How a month-end seeder failed a list, in one of four words (01.1, ME-18, MR-SEC-21). Recorded beside the list and
 * answered by the status read -- a public address -- so it is a word from this closed set and never an exception's
 * class, which names a library and a table, and never its message, which may quote a row. The exception itself goes
 * to the log, scrubbed.
 */
public enum SeedFailure {
    /** The resource could not be read, or a row of it is not the shape a list's row is. */
    UNREADABLE,
    /** The database refused a row: a key, a reference or a check it does not meet. */
    REFUSED,
    /** The work was cut off: a timeout, a lock not given, a connection lost, the thread interrupted. */
    INTERRUPTED,
    /** Anything else. */
    UNKNOWN;

    /** The word for a failure, from its type and its causes' -- never from a message. */
    public static SeedFailure of(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof IOException || t instanceof UncheckedIOException
                    || t instanceof com.fasterxml.jackson.core.JacksonException
                    || t instanceof UnreadableRow) {
                return UNREADABLE;
            }
            if (t instanceof DataIntegrityViolationException) {
                return REFUSED;
            }
            if (t instanceof TransactionTimedOutException || t instanceof QueryTimeoutException
                    || t instanceof TransientDataAccessException || t instanceof InterruptedException) {
                return INTERRUPTED;
            }
        }
        return UNKNOWN;
    }

    /** A row of a seed resource that is not the shape its list's rows are. Its message names the list, never a value. */
    public static final class UnreadableRow extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public UnreadableRow(String what) {
            super(what);
        }
    }
}

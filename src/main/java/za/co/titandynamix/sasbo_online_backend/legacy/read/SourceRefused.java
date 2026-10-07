package za.co.titandynamix.sasbo_online_backend.legacy.read;

/**
 * A file that cannot be read at all -- as opposed to a row with a problem, which is staged and reported. Thrown by a
 * reader the moment it knows; nothing of the file is kept. The message is a sentence of this code's own and never
 * quotes the file.
 */
public class SourceRefused extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Why the whole file is refused. */
    public enum Reason {
        /** No data rows at all. */
        EMPTY,
        /** Larger than the byte ceiling. {@link #limit()} carries it. */
        TOO_LARGE,
        /** More data rows than the ceiling. {@link #limit()} carries it. */
        TOO_MANY_ROWS,
        /** Not the format the mapping says: binary where text was expected, not a workbook, no such sheet, too wide. */
        NOT_READABLE
    }

    private final Reason reason;
    private final long limit;

    public SourceRefused(Reason reason, String message) {
        this(reason, 0, message);
    }

    public SourceRefused(Reason reason, long limit, String message) {
        super(message);
        this.reason = reason;
        this.limit = limit;
    }

    public Reason reason() {
        return reason;
    }

    /** The ceiling that was passed, for {@link Reason#TOO_LARGE} and {@link Reason#TOO_MANY_ROWS}; 0 otherwise. */
    public long limit() {
        return limit;
    }
}

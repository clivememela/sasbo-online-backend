package za.co.titandynamix.sasbo_online_backend.legacy.read;

/**
 * The ceilings a file is read under, stated rather than inherited.
 *
 * @param maxBytes     the file's size: past it, the read stops and nothing is kept
 * @param maxRows      data rows: past it, likewise
 * @param maxColumns   cells a row may carry; a header with more is not a member export
 * @param maxCellChars characters a cell may hold; a longer one is cut there and its row says so
 */
public record ReadLimits(long maxBytes, int maxRows, int maxColumns, int maxCellChars) {

    /**
     * A physical line of a fixed-width file longer than this is not a record: a file with no line breaks, or binary
     * read as text. Its row is malformed, and no more of it is held.
     */
    public static final int MAX_LINE_CHARS = 100_000;

    public ReadLimits {
        if (maxBytes <= 0 || maxRows <= 0 || maxColumns <= 0 || maxCellChars <= 0) {
            throw new IllegalArgumentException("every read limit is positive");
        }
    }
}

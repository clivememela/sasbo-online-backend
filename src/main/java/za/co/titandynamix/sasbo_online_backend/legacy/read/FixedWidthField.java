package za.co.titandynamix.sasbo_online_backend.legacy.read;

/**
 * One field of a fixed-width layout: its name, where it starts (1-based, as a record's documentation numbers its
 * columns) and how many characters it takes.
 */
public record FixedWidthField(String name, int start, int length) {

    /** The index just past the field's last character, 0-based. */
    public int end() {
        return start - 1 + length;
    }
}

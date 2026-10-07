package za.co.titandynamix.sasbo_online_backend.legacy.read;

/**
 * Where a reader hands each row the moment it is read -- so a file is never held whole: the reader keeps one row and
 * its read buffer, the sink decides how many rows it keeps before writing them (one chunk).
 */
@FunctionalInterface
public interface RowSink {

    void accept(SourceRow row);
}

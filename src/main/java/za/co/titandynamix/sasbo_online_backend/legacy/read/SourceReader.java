package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads a file row by row and hands each row on as it is read: never the whole file in memory. A file that cannot be
 * read at all is refused with {@link SourceRefused}; a row that reads badly is handed on with its problems, so the
 * report can say which line and why.
 */
public interface SourceReader {

    ReadSummary read(InputStream in, ReaderSettings settings, ReadLimits limits, RowSink sink) throws IOException;

    /** The reader for a format. */
    static SourceReader of(SourceFormat format) {
        return switch (format) {
            case CSV -> new CsvSourceReader();
            case XLSX -> new XlsxSourceReader();
            case FIXED_WIDTH -> new FixedWidthSourceReader();
        };
    }
}

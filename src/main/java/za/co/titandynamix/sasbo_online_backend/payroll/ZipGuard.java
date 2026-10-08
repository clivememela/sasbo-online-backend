package za.co.titandynamix.sasbo_online_backend.payroll;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Measures what a workbook really expands to before POI opens it -- by inflating each part into a small buffer and
 * counting, never by believing the sizes the zip declares.
 *
 * <p>POI's own guard ({@code ZipSecureFile}) refuses a part that inflates more than a hundred times its compressed
 * size, and it stays as it is. But POI holds an opened package's parts in memory, and a hundred times ten megabytes
 * is a gigabyte -- on a host with half of one. So a workbook is refused here, {@code FILE_UNREADABLE}, when it holds
 * more parts than any workbook needs, or expands past what twenty thousand lines could ever take.
 */
public final class ZipGuard {

    /** The most parts a payroll workbook holds; one sheet's workbook has about a dozen. */
    static final int MAX_ENTRIES = 1_000;

    /** The most one part may expand to: far beyond a 20,000-line sheet's XML. */
    static final long MAX_ENTRY_BYTES = 120L * 1024 * 1024;

    /** The most the whole workbook may expand to. */
    static final long MAX_TOTAL_BYTES = 160L * 1024 * 1024;

    private ZipGuard() {
    }

    /** @throws za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal {@code FILE_UNREADABLE} when it is too much */
    public static void check(byte[] bytes) {
        byte[] buffer = new byte[64 * 1024];
        long total = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > MAX_ENTRIES) {
                    throw GridReader.unreadable();
                }
                if (entry.isDirectory()) {
                    continue;
                }
                long size = 0;
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    size += read;
                    total += read;
                    if (size > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw GridReader.unreadable();
                    }
                }
            }
        } catch (IOException damaged) {
            throw GridReader.unreadable();
        }
    }
}

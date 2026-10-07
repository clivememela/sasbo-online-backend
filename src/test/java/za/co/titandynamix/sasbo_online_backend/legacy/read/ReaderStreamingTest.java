package za.co.titandynamix.sasbo_online_backend.legacy.read;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.limits;

/**
 * The readers STREAM: 100,000 invented rows, generated a line at a time as the reader asks for bytes, and at every row
 * handed on the reader has asked for no more than its buffers past that row. A reader that held the file -- read it all,
 * then handed rows on -- would have asked for all ten megabytes before the first row arrived, and fails here.
 *
 * <p>The memory a read holds is therefore its buffers and one row, whatever the file's size: the property, proved by
 * what the reader asked for rather than by sampling a heap the collector runs when it likes.
 */
@DisplayName("Reading streams")
class ReaderStreamingTest {

    private static final int ROWS = 100_000;

    /** The furthest a reader may have read past the row it hands on: its byte and character buffers, with room. */
    private static final long READ_AHEAD = 256 * 1024;

    /** Invented rows, made only as they are read, counting the bytes handed out and where each line ends. */
    private static final class GeneratedFile extends InputStream {

        private final boolean fixedWidth;
        private final long[] lineEnds = new long[ROWS + 2];
        private byte[] current = new byte[0];
        private int position;
        private int nextLine;
        private long produced;

        GeneratedFile(boolean fixedWidth) {
            this.fixedWidth = fixedWidth;
        }

        private String line(int n) {
            if (n == 0) {
                return fixedWidth ? "" : "MEMBERSHIP NO,SURNAME,ID NUMBER,DATE JOINED,R/A NO,CELL NO\r\n";
            }
            String number = String.format("%09d", n);
            String surname = "Invented" + (n % 997);
            return fixedWidth
                    ? String.format("%-9s%-30s%-13s%-10s", number, surname, "0000000000000", " 1/ 3/2015") + "\r\n"
                    : number + "," + surname + ",0000000000000, 1/ 3/2015,990001,082 000 0000\r\n";
        }

        @Override
        public int read() {
            if (position >= current.length && !advance()) {
                return -1;
            }
            produced++;
            return current[position++] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (length == 0) {
                return 0;
            }
            if (position >= current.length && !advance()) {
                return -1;
            }
            int n = Math.min(length, current.length - position);
            System.arraycopy(current, position, buffer, offset, n);
            position += n;
            produced += n;
            return n;
        }

        private boolean advance() {
            int first = fixedWidth ? 1 : 0;
            if (nextLine == 0 && first == 1) {
                nextLine = 1;
            }
            if (nextLine > ROWS) {
                return false;
            }
            current = line(nextLine).getBytes(StandardCharsets.US_ASCII);
            position = 0;
            long before = nextLine == first ? 0 : lineEnds[nextLine - 1];
            lineEnds[nextLine] = before + current.length;
            nextLine++;
            return true;
        }
    }

    @Test
    @DisplayName("CSV: 100,000 rows, and never more than the buffers read past the row in hand")
    void csvStreams() throws IOException {
        GeneratedFile file = new GeneratedFile(false);
        long[] rows = {0};
        long[] furthest = {0};
        new CsvSourceReader().read(file, ReaderSettings.csv(StandardCharsets.US_ASCII, ',', true), limits(), row -> {
            rows[0]++;
            // The data row numbered n is line n of the file: the header is line 1.
            long ahead = file.produced - file.lineEnds[row.rowNumber() - 1];
            furthest[0] = Math.max(furthest[0], ahead);
        });

        assertThat(rows[0]).isEqualTo(ROWS);
        assertThat(file.produced).as("the whole file was read, by the end").isGreaterThan(5_000_000L);
        assertThat(furthest[0]).as("bytes read past the row being handed on").isLessThanOrEqualTo(READ_AHEAD);
    }

    @Test
    @DisplayName("fixed width: 100,000 rows, and never more than the buffers read past the row in hand")
    void fixedWidthStreams() throws IOException {
        GeneratedFile file = new GeneratedFile(true);
        long[] rows = {0};
        long[] furthest = {0};
        List<FixedWidthField> layout = List.of(new FixedWidthField("MEMNO", 1, 9), new FixedWidthField("SURNAME", 10, 30),
                new FixedWidthField("IDNO", 40, 13), new FixedWidthField("JOINED", 53, 10));
        new FixedWidthSourceReader().read(file, ReaderSettings.fixedWidth(StandardCharsets.US_ASCII, layout), limits(),
                row -> {
                    rows[0]++;
                    long ahead = file.produced - file.lineEnds[row.rowNumber()];
                    furthest[0] = Math.max(furthest[0], ahead);
                });

        assertThat(rows[0]).isEqualTo(ROWS);
        assertThat(furthest[0]).as("bytes read past the row being handed on").isLessThanOrEqualTo(READ_AHEAD);
    }
}

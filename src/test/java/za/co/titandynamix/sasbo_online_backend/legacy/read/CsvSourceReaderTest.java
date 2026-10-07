package za.co.titandynamix.sasbo_online_backend.legacy.read;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.limits;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.read;

/**
 * Delimited text, the shapes a legacy export takes: encodings (Windows-1252 above all), delimiters, quoting, line
 * breaks of either kind, and every way a row can be badly formed -- each reported on its row, never stopping the read.
 */
@DisplayName("CsvSourceReader")
class CsvSourceReaderTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private static List<SourceRow> csv(String text) throws IOException {
        return csv(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',', true, limits());
    }

    private static List<SourceRow> csv(byte[] bytes, Charset charset, char delimiter, boolean header, ReadLimits limits)
            throws IOException {
        return read(new CsvSourceReader(), bytes, ReaderSettings.csv(charset, delimiter, header), limits);
    }

    private static List<RuleCode> codes(SourceRow row) {
        return row.problems().stream().map(Problem::code).toList();
    }

    @Test
    @DisplayName("names each cell by its header, and numbers each row by the line it starts on")
    void readsByHeader() throws IOException {
        List<SourceRow> rows = csv("NAME,CODE\r\nAnn,1\r\nBen,2\r\n");

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).values()).containsExactly(java.util.Map.entry("NAME", "Ann"), java.util.Map.entry("CODE", "1"));
        assertThat(rows.get(0).rowNumber()).isEqualTo(2);
        assertThat(rows.get(1).rowNumber()).isEqualTo(3);
        assertThat(rows.get(0).problems()).isEmpty();
    }

    @Test
    @DisplayName("reads Windows-1252 as Windows-1252 -- e with an acute accent is one byte, 0xE9")
    void readsWindows1252() throws IOException {
        byte[] bytes = "NAME\r\nRenée\r\n".getBytes(WINDOWS_1252);
        assertThat(bytes).contains((byte) 0xE9);

        List<SourceRow> rows = csv(bytes, WINDOWS_1252, ',', true, limits());

        assertThat(rows.get(0).values().get("NAME")).isEqualTo("Renée");
        assertThat(rows.get(0).problems()).isEmpty();
    }

    @Test
    @DisplayName("ENCODING_SUSPECT: Windows-1252 read as UTF-8 says so on the row, and never throws")
    void encodingSuspect() throws IOException {
        byte[] bytes = "NAME\r\nRenée\r\nAnn\r\n".getBytes(WINDOWS_1252);

        List<SourceRow> rows = csv(bytes, StandardCharsets.UTF_8, ',', true, limits());

        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ENCODING_SUSPECT);
        assertThat(rows.get(1).problems()).as("a row that reads cleanly says nothing").isEmpty();
    }

    @Test
    @DisplayName("passes over a UTF-8 byte-order mark: the first column's name is not spoilt by it")
    void byteOrderMark() throws IOException {
        byte[] text = "NAME,CODE\nAnn,1\n".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[text.length + 3];
        bytes[0] = (byte) 0xEF;
        bytes[1] = (byte) 0xBB;
        bytes[2] = (byte) 0xBF;
        System.arraycopy(text, 0, bytes, 3, text.length);

        assertThat(csv(bytes, StandardCharsets.UTF_8, ',', true, limits()).get(0).values()).containsKey("NAME");
    }

    @ParameterizedTest(name = "delimiter {0}")
    @ValueSource(chars = {';', '|', '\t'})
    @DisplayName("takes the delimiter the mapping names")
    void delimiters(char delimiter) throws IOException {
        String text = "NAME" + delimiter + "CODE\nAnn" + delimiter + "1\n";

        List<SourceRow> rows = csv(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, delimiter, true, limits());

        assertThat(rows.get(0).values().get("CODE")).isEqualTo("1");
    }

    @Test
    @DisplayName("a quoted cell holds the delimiter, a doubled quote and a line break -- and the next row's number counts the line")
    void quoting() throws IOException {
        List<SourceRow> rows = csv("NAME,NOTE\n\"Example, Ann\",\"said \"\"hello\"\"\"\n\"Ben\",\"two\r\nlines\"\nCara,x\n");

        assertThat(rows.get(0).values().get("NAME")).isEqualTo("Example, Ann");
        assertThat(rows.get(0).values().get("NOTE")).isEqualTo("said \"hello\"");
        assertThat(rows.get(1).values().get("NOTE")).isEqualTo("two\nlines");
        assertThat(rows.get(1).rowNumber()).isEqualTo(3);
        assertThat(rows.get(2).rowNumber()).as("the line after a quoted line break").isEqualTo(5);
        assertThat(rows).allSatisfy(row -> assertThat(row.problems()).isEmpty());
    }

    @Test
    @DisplayName("line breaks of every kind end a row: CRLF, LF and a lone CR")
    void lineBreaks() throws IOException {
        assertThat(csv("A\r\n1\r\n2\r\n")).hasSize(2);
        assertThat(csv("A\n1\n2\n")).hasSize(2);
        assertThat(csv("A\r1\r2\r")).hasSize(2);
        assertThat(csv("A\n1\n2")).as("no break after the last row").hasSize(2);
    }

    @Test
    @DisplayName("spaces before an opening quote are not part of the cell")
    void spacesBeforeAQuote() throws IOException {
        assertThat(csv("A,B\n1,  \"x, y\"\n").get(0).values().get("B")).isEqualTo("x, y");
    }

    @Test
    @DisplayName("ROW_MALFORMED: text after a closing quote, or a quote inside a bare cell -- the row is read leniently and said")
    void malformedQuotes() throws IOException {
        List<SourceRow> rows = csv("A,B\n\"x\"y,1\nab\"c,2\nfine,3\n");

        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ROW_MALFORMED);
        assertThat(rows.get(0).values().get("A")).isEqualTo("xy");
        assertThat(codes(rows.get(1))).containsExactly(RuleCode.ROW_MALFORMED);
        assertThat(rows.get(2).problems()).isEmpty();
    }

    @Test
    @DisplayName("ROW_MALFORMED: a quote never closed takes the rest of the file into its cell, and the read ends on that row")
    void unterminatedQuote() throws IOException {
        List<SourceRow> rows = csv("A,B\n1,ok\n2,\"never closed\n3,lost\n");

        assertThat(rows).hasSize(2);
        assertThat(codes(rows.get(1))).containsExactly(RuleCode.ROW_MALFORMED);
        assertThat(rows.get(1).values().get("B")).isEqualTo("never closed\n3,lost\n");
    }

    @Test
    @DisplayName("ROW_MALFORMED: more cells than the header -- a value has moved a column along; ROW_SHORT: fewer, read as blank")
    void cellCounts() throws IOException {
        List<SourceRow> rows = csv("A,B,C\n1,2,3,4\n1,2\n");

        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ROW_MALFORMED);
        assertThat(rows.get(0).values()).containsEntry("#4", "4");
        assertThat(codes(rows.get(1))).containsExactly(RuleCode.ROW_SHORT);
        assertThat(rows.get(1).values()).containsEntry("C", "");
    }

    @Test
    @DisplayName("a blank header cell is named by its position, and a repeated name is kept as its second")
    void headerNames() throws IOException {
        List<SourceRow> rows = csv("Employee Number,,Class,class\n1,2,NEW,x\n");

        assertThat(rows.get(0).values().keySet()).containsExactly("Employee Number", "#2", "Class", "class (2)");
    }

    @Test
    @DisplayName("a file with no header row names every column by its position")
    void noHeader() throws IOException {
        List<SourceRow> rows = csv("1,NEW\n2,DEL\n".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',', false,
                limits());

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).values().keySet()).containsExactly("#1", "#2");
        assertThat(rows.get(0).rowNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("passes over the rows before the header -- the title row SASBO's employer files carry")
    void skipRows() throws IOException {
        byte[] bytes = "NEDBANK NEW AND DELETED MEMBERS - 2026/08/31\nClass,Name\nNEW,Ann\n".getBytes(StandardCharsets.UTF_8);

        List<SourceRow> rows = read(new CsvSourceReader(), bytes,
                ReaderSettings.csv(StandardCharsets.UTF_8, ',', true).skipping(1), limits());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).values()).containsEntry("Class", "NEW");
        assertThat(rows.get(0).rowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("blank lines, and lines of nothing but delimiters, are not rows")
    void blankLines() throws IOException {
        List<SourceRow> rows = csv("A,B\n\n1,2\n,,\n   \n3,4\n");

        assertThat(rows).extracting(row -> row.values().get("A")).containsExactly("1", "3");
    }

    @Test
    @DisplayName("FIELD_TOO_LONG: a cell past the ceiling is cut there, and its row names the column")
    void cellCeiling() throws IOException {
        String longCell = "x".repeat(50);
        List<SourceRow> rows = csv(("A,B\n1," + longCell + "\n").getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8,
                ',', true, new ReadLimits(1_000_000, 100, 10, 20));

        assertThat(rows.get(0).values().get("B")).hasSize(20);
        assertThat(rows.get(0).problems()).containsExactly(new Problem(RuleCode.FIELD_TOO_LONG, "B"));
    }

    @Test
    @DisplayName("a header wider than the column ceiling is no member export: the file is refused, NOT_READABLE")
    void headerTooWide() {
        assertThatThrownBy(() -> csv("A,B,C,D\n1,2,3,4\n".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',',
                true, new ReadLimits(1_000_000, 100, 3, 100)))
                .isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.NOT_READABLE);
    }

    @Test
    @DisplayName("a row wider than the ceiling, with no header to compare, is ROW_MALFORMED and its extra cells are dropped")
    void rowTooWide() throws IOException {
        List<SourceRow> rows = csv("1,2,3,4,5\n".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',', false,
                new ReadLimits(1_000_000, 100, 3, 100));

        assertThat(rows.get(0).values()).hasSize(3);
        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ROW_MALFORMED);
    }

    @Test
    @DisplayName("refuses past the row ceiling (TOO_MANY_ROWS), and past the byte ceiling while still reading (TOO_LARGE)")
    void ceilings() {
        assertThatThrownBy(() -> csv("A\n1\n2\n3\n".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8, ',', true,
                new ReadLimits(1_000_000, 2, 10, 100)))
                .isInstanceOf(SourceRefused.class)
                .satisfies(refused -> {
                    assertThat(((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.TOO_MANY_ROWS);
                    assertThat(((SourceRefused) refused).limit()).isEqualTo(2);
                });
        byte[] big = ("A\n" + "1\n".repeat(100_000)).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> new CsvSourceReader().read(new LimitedInputStream(new ByteArrayInputStream(big), 50_000),
                ReaderSettings.csv(StandardCharsets.UTF_8, ',', true), limits(), row -> { }))
                .isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.TOO_LARGE);
    }

    @Test
    @DisplayName("refuses a file with no rows: empty, or a header alone (EMPTY)")
    void empty() {
        assertThatThrownBy(() -> csv("")).isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.EMPTY);
        assertThatThrownBy(() -> csv("A,B\r\n")).isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.EMPTY);
    }

    @Test
    @DisplayName("never trusts a name: binary, a zip (an .xlsx) and a PDF are refused by their bytes, NOT_READABLE")
    void contentChecks() {
        byte[] binary = {'A', ',', 'B', '\n', 1, 0, 2, '\n'};
        byte[] zip = {'P', 'K', 3, 4, 20, 0, 0, 0};
        byte[] pdf = "%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII);
        for (byte[] bytes : List.of(binary, zip, pdf)) {
            assertThatThrownBy(() -> csv(bytes, StandardCharsets.UTF_8, ',', true, limits()))
                    .isInstanceOf(SourceRefused.class)
                    .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.NOT_READABLE);
        }
    }

    @Test
    @DisplayName("UTF-16 text, whose every other byte is nought, is text and not binary")
    void utf16() throws IOException {
        byte[] bytes = "NAME\nAnn\n".getBytes(StandardCharsets.UTF_16LE);

        assertThat(csv(bytes, StandardCharsets.UTF_16LE, ',', true, limits()).get(0).values().get("NAME")).isEqualTo("Ann");
    }
}

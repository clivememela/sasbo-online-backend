package za.co.titandynamix.sasbo_online_backend.legacy.read;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.limits;
import static za.co.titandynamix.sasbo_online_backend.legacy.LegacyFixtures.read;

/**
 * Columns by position: a layout applied to each line, its padding trimmed, the lines that are not records passed over,
 * and a short or an endless line said on its row.
 */
@DisplayName("FixedWidthSourceReader")
class FixedWidthSourceReaderTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    /** MEMNO 1-6 · NAME 7-16 · CODE 17-18. */
    private static final List<FixedWidthField> LAYOUT = List.of(
            new FixedWidthField("MEMNO", 1, 6), new FixedWidthField("NAME", 7, 10), new FixedWidthField("CODE", 17, 2));

    private static List<SourceRow> fixed(String text, Charset charset) throws IOException {
        return fixed(text.getBytes(charset), charset, ReaderSettings.fixedWidth(charset, LAYOUT), limits());
    }

    private static List<SourceRow> fixed(byte[] bytes, Charset charset, ReaderSettings settings, ReadLimits limits)
            throws IOException {
        return read(new FixedWidthSourceReader(), bytes, settings, limits);
    }

    private static List<RuleCode> codes(SourceRow row) {
        return row.problems().stream().map(Problem::code).toList();
    }

    @Test
    @DisplayName("slices each line by the layout, keeps a value less its padding, and numbers a row by its line")
    void slices() throws IOException {
        List<SourceRow> rows = fixed("000123Example   11\r\n000124Sample    2 \r\n", StandardCharsets.UTF_8);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).values()).containsEntry("MEMNO", "000123").containsEntry("NAME", "Example")
                .containsEntry("CODE", "11");
        assertThat(rows.get(1).values()).containsEntry("CODE", "2");
        assertThat(rows.get(1).rowNumber()).isEqualTo(2);
        assertThat(rows).allSatisfy(row -> assertThat(row.problems()).isEmpty());
    }

    @Test
    @DisplayName("keeps a value's leading spaces -- the old system's dates print one where a nought would be")
    void leadingSpacesKept() throws IOException {
        assertThat(fixed("     9 9/ 9/2026 1\n", StandardCharsets.UTF_8).get(0).values()).containsEntry("MEMNO", "     9")
                .containsEntry("NAME", " 9/ 9/2026");
    }

    @Test
    @DisplayName("passes over the rows before the data, blank lines, and the lines the layout names as not records")
    void skipsWhatIsNotARecord() throws IOException {
        ReaderSettings settings = new ReaderSettings(SourceFormat.FIXED_WIDTH, StandardCharsets.UTF_8, ',', '"', false, 1,
                null, LAYOUT, List.of("HDR", "TRL"));
        byte[] bytes = "A REPORT TITLE\nHDR 2026/08/31\n000123Example   11\n\n   \nTRL 000001\n"
                .getBytes(StandardCharsets.UTF_8);

        List<SourceRow> rows = fixed(bytes, StandardCharsets.UTF_8, settings, limits());

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).rowNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("ROW_SHORT: a line that ends before a field begins leaves that field blank, and says so")
    void shortLine() throws IOException {
        List<SourceRow> rows = fixed("000123Exam\n000124Sample    12\n", StandardCharsets.UTF_8);

        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ROW_SHORT);
        assertThat(rows.get(0).values()).containsEntry("NAME", "Exam").containsEntry("CODE", "");
        assertThat(rows.get(1).problems()).isEmpty();
    }

    @Test
    @DisplayName("a field the line reaches only partly -- its trailing padding trimmed away by the writer -- is not short")
    void trimmedPaddingIsNotShort() throws IOException {
        List<SourceRow> rows = fixed("000123Example   1\n", StandardCharsets.UTF_8);

        assertThat(rows.get(0).problems()).isEmpty();
        assertThat(rows.get(0).values()).containsEntry("CODE", "1");
    }

    @Test
    @DisplayName("reads Windows-1252 as Windows-1252, and says ENCODING_SUSPECT when it is read as UTF-8")
    void encodings() throws IOException {
        String line = "000123Müller    11\n";
        byte[] bytes = line.getBytes(WINDOWS_1252);

        assertThat(fixed(bytes, WINDOWS_1252, ReaderSettings.fixedWidth(WINDOWS_1252, LAYOUT), limits()).get(0).values())
                .containsEntry("NAME", "Müller");
        assertThat(codes(fixed(bytes, StandardCharsets.UTF_8, ReaderSettings.fixedWidth(StandardCharsets.UTF_8, LAYOUT),
                limits()).get(0))).containsExactly(RuleCode.ENCODING_SUSPECT);
    }

    @Test
    @DisplayName("ROW_MALFORMED: a line longer than any record -- no line breaks, or binary read as text -- and only the layout's part is held")
    void endlessLine() throws IOException {
        String endless = "000123Example   11" + "x".repeat(ReadLimits.MAX_LINE_CHARS + 10) + "\n000124Sample    12\n";

        List<SourceRow> rows = fixed(endless, StandardCharsets.UTF_8);

        assertThat(codes(rows.get(0))).containsExactly(RuleCode.ROW_MALFORMED);
        assertThat(rows.get(0).values()).containsEntry("CODE", "11");
        assertThat(rows.get(1).problems()).isEmpty();
    }

    @Test
    @DisplayName("passes over a byte-order mark on the first line, without cutting its last field short")
    void byteOrderMark() throws IOException {
        assertThat(fixed("﻿000123Example   11\n", StandardCharsets.UTF_8).get(0).values())
                .containsEntry("MEMNO", "000123").containsEntry("CODE", "11");
    }

    @Test
    @DisplayName("refuses past the row ceiling, a file with no records, and binary content")
    void refusals() {
        assertThatThrownBy(() -> fixed("000001A         11\n000002B         11\n".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, ReaderSettings.fixedWidth(StandardCharsets.UTF_8, LAYOUT),
                new ReadLimits(1_000_000, 1, 10, 100)))
                .isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.TOO_MANY_ROWS);
        assertThatThrownBy(() -> fixed("\n\n", StandardCharsets.UTF_8)).isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.EMPTY);
        assertThatThrownBy(() -> fixed(new byte[] {'0', '0', 0, 1, '\n'}, StandardCharsets.UTF_8,
                ReaderSettings.fixedWidth(StandardCharsets.UTF_8, LAYOUT), limits()))
                .isInstanceOf(SourceRefused.class)
                .extracting(refused -> ((SourceRefused) refused).reason()).isEqualTo(SourceRefused.Reason.NOT_READABLE);
    }
}

package za.co.titandynamix.sasbo_online_backend.legacy.read;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * How a file is read -- the format half of a mapping definition.
 *
 * @param charset               the text's encoding (CSV and fixed width; a workbook carries its own)
 * @param delimiter             CSV: the character between cells
 * @param quote                 CSV: the character that wraps a cell holding a delimiter, a quote or a line break
 * @param header                CSV and workbook: whether the first row read (after {@code skipRows}) names the columns
 * @param skipRows              rows before the data -- a title row, as SASBO's employer files carry one
 * @param sheet                 workbook: the sheet's name, or {@code null} for the first
 * @param layout                fixed width: the fields, in order
 * @param skipLinesStartingWith fixed width: lines that are not records -- a header or trailer record's marker
 */
public record ReaderSettings(SourceFormat format, Charset charset, char delimiter, char quote, boolean header,
                             int skipRows, String sheet, List<FixedWidthField> layout,
                             List<String> skipLinesStartingWith) {

    public ReaderSettings {
        charset = charset == null ? StandardCharsets.UTF_8 : charset;
        layout = layout == null ? List.of() : List.copyOf(layout);
        skipLinesStartingWith = skipLinesStartingWith == null ? List.of() : List.copyOf(skipLinesStartingWith);
    }

    /** Comma-separated UTF-8 text with a header row: the defaults, for tests and the simplest file. */
    public static ReaderSettings csv(Charset charset, char delimiter, boolean header) {
        return new ReaderSettings(SourceFormat.CSV, charset, delimiter, '"', header, 0, null, List.of(), List.of());
    }

    public static ReaderSettings fixedWidth(Charset charset, List<FixedWidthField> layout) {
        return new ReaderSettings(SourceFormat.FIXED_WIDTH, charset, ',', '"', false, 0, null, layout, List.of());
    }

    public static ReaderSettings xlsx(String sheet, boolean header) {
        return new ReaderSettings(SourceFormat.XLSX, StandardCharsets.UTF_8, ',', '"', header, 0, sheet, List.of(),
                List.of());
    }

    /** The same settings with {@code skipRows} rows passed over before the data. */
    public ReaderSettings skipping(int rows) {
        return new ReaderSettings(format, charset, delimiter, quote, header, rows, sheet, layout, skipLinesStartingWith);
    }
}

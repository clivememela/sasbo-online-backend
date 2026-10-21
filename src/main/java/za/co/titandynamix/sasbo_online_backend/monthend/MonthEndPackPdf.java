package za.co.titandynamix.sasbo_online_backend.monthend;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * A region's pack as a PDF (October build; Stephne MEN-05: "PDF and Excel") -- A4 landscape, one family of type
 * (Helvetica, one of the fonts every PDF reader carries, so nothing is embedded and the file stays small enough to send),
 * light: dark ink on white, a hairline under each table's heading, no shading, no images, no transparency.
 *
 * <p>Laid out a line at a time as it is written: a table that runs over a page carries its heading onto the next, and
 * every page is stamped at the foot with what it is and "Page n of N". Text is drawn, never interpreted: a character the
 * font cannot print is printed without its accent, or as '?', so one name never stops a pack.
 */
public final class MonthEndPackPdf implements Closeable {

    /** A column of a table: its heading, its share of the width, and whether it holds figures (set to the right). */
    public record Column(String heading, float weight, boolean figures) {

        public static Column text(String heading, float weight) {
            return new Column(heading, weight, false);
        }

        public static Column figures(String heading, float weight) {
            return new Column(heading, weight, true);
        }
    }

    static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    static final float MARGIN = 36f;
    static final float TOP = PAGE.getHeight() - 40f;
    static final float BOTTOM = 44f;
    static final float WIDTH = PAGE.getWidth() - 2 * MARGIN;
    static final float BODY = 8f;
    static final float LEADING = 11f;
    static final float CELL_GAP = 6f;

    /** Ink: a near-black, never pure black (the house style), and a quiet grey for notes and rules. */
    private static final float[] INK = {0.11f, 0.13f, 0.16f};
    private static final float[] QUIET = {0.38f, 0.42f, 0.47f};

    private final PDDocument document = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDType1Font italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private final String footer;
    private PDPageContentStream page;
    private float y;

    /** @param footer what every page says at its foot, beside its number */
    public MonthEndPackPdf(String title, String footer) {
        this.footer = footer;
        PDDocumentInformation information = document.getDocumentInformation();
        information.setTitle(printable(title));
        information.setCreator("SASBO Online");
        information.setProducer("SASBO Online");
    }

    // ------------------------------------------------------------------------------------------------ blocks

    /** The pack's title, large, and the lines under it. */
    public void title(String title, List<String> lines) {
        ensure(60f);
        write(bold, 16f, MARGIN, title, INK);
        y -= 22f;
        for (String line : lines) {
            ensure(LEADING);
            write(regular, 9.5f, MARGIN, line, INK);
            y -= 13f;
        }
        y -= 6f;
    }

    /** A section's heading: kept with at least the start of what follows. */
    public void heading(String text) {
        ensure(LEADING * 5);
        y -= 6f;
        write(bold, 11.5f, MARGIN, text, INK);
        y -= 16f;
    }

    /** A table's own heading, smaller, with its old title beside it where it has one. */
    public void subheading(String text, String aside) {
        ensure(LEADING * 4);
        write(bold, 9.5f, MARGIN, text, INK);
        if (aside != null && !aside.isBlank()) {
            float at = MARGIN + width(bold, 9.5f, text) + 8f;
            write(italic, 7.5f, at, fit(aside, italic, 7.5f, WIDTH - (at - MARGIN)), QUIET);
        }
        y -= 13f;
    }

    /** A note in quiet ink, wrapped to the width. */
    public void note(String text) {
        for (String line : wrap(printable(text), italic, 7.5f, WIDTH)) {
            ensure(10f);
            write(italic, 7.5f, MARGIN, line, QUIET);
            y -= 10f;
        }
    }

    /** Space between blocks. */
    public void gap(float points) {
        y -= points;
    }

    /** A table: its heading row, then each row -- the heading again at the top of every page it runs onto. */
    public void table(List<Column> columns, Iterable<List<String>> rows) {
        float[] widths = widths(columns);
        float size = columns.size() > 9 ? 7f : BODY;
        ensure(LEADING * 3);
        headingRow(columns, widths, size);
        for (List<String> row : rows) {
            if (y - LEADING < BOTTOM) {
                newPage();
                headingRow(columns, widths, size);
            }
            row(columns, widths, row, size, regular);
        }
        y -= 4f;
    }

    /** A row that totals the table, in bold, under a hairline. */
    public void totalRow(List<Column> columns, List<String> row) {
        float[] widths = widths(columns);
        float size = columns.size() > 9 ? 7f : BODY;
        ensure(LEADING + 4f);
        // Above the total's capitals, below the last row's descenders -- never through the total's own text.
        rule(y + LEADING - 2.5f, QUIET);
        row(columns, widths, row, size, bold);
        y -= 4f;
    }

    /** The PDF, every page stamped with its foot, to {@code out}. */
    public void writeTo(OutputStream out) {
        try {
            closePage();
            int total = document.getNumberOfPages();
            for (int n = 0; n < total; n++) {
                try (PDPageContentStream foot = new PDPageContentStream(document, document.getPage(n),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    foot.setStrokingColor(QUIET[0], QUIET[1], QUIET[2]);
                    foot.setLineWidth(0.4f);
                    foot.moveTo(MARGIN, 30f);
                    foot.lineTo(PAGE.getWidth() - MARGIN, 30f);
                    foot.stroke();
                    text(foot, regular, 7f, MARGIN, 20f, fit(footer, regular, 7f, WIDTH - 80f), QUIET);
                    String number = "Page " + (n + 1) + " of " + total;
                    text(foot, regular, 7f, PAGE.getWidth() - MARGIN - width(regular, 7f, number), 20f, number, QUIET);
                }
            }
            document.save(out);
        } catch (IOException unwritable) {
            throw new UncheckedIOException("A pack could not be laid out as a PDF", unwritable);
        }
    }

    @Override
    public void close() {
        try {
            closePage();
            document.close();
        } catch (IOException ignored) {
            // Nothing to do: the document is being discarded.
        }
    }

    // ------------------------------------------------------------------------------------------------ layout

    /** A table's heading row: a heading too wide for its column wraps onto a second line, and only then is cut. */
    private void headingRow(List<Column> columns, float[] widths, float size) {
        List<List<String>> headings = new ArrayList<>();
        int lines = 1;
        for (int c = 0; c < columns.size(); c++) {
            float room = widths[c] - CELL_GAP;
            List<String> wrapped = wrap(printable(columns.get(c).heading()), bold, size, room);
            List<String> shown = new ArrayList<>();
            for (int l = 0; l < Math.min(2, wrapped.size()); l++) {
                String line = l == 1 && wrapped.size() > 2 ? String.join(" ", wrapped.subList(1, wrapped.size()))
                        : wrapped.get(l);
                shown.add(fit(line, bold, size, room));
            }
            headings.add(shown);
            lines = Math.max(lines, shown.size());
        }
        float step = size + 1.5f;
        if (y - (lines - 1) * step - LEADING < BOTTOM) {
            newPage();
        }
        float x = MARGIN;
        for (int c = 0; c < columns.size(); c++) {
            List<String> shown = headings.get(c);
            for (int l = 0; l < shown.size(); l++) {
                // The last line of every heading sits on the same baseline, just above the rule.
                float at = y - (lines - shown.size() + l) * step;
                float left = columns.get(c).figures() ? x + widths[c] - CELL_GAP - width(bold, size, shown.get(l)) : x;
                writeAt(bold, size, left, at, shown.get(l));
            }
            x += widths[c];
        }
        float last = y - (lines - 1) * step;
        rule(last - 3f, INK);
        y = last - (LEADING + 1f);
    }

    private void row(List<Column> columns, float[] widths, List<String> cells, float size, PDType1Font font) {
        float x = MARGIN;
        for (int c = 0; c < columns.size(); c++) {
            String value = c < cells.size() && cells.get(c) != null ? cells.get(c) : "";
            String shown = fit(printable(value), font, size, widths[c] - CELL_GAP);
            float at = columns.get(c).figures() ? x + widths[c] - CELL_GAP - width(font, size, shown) : x;
            write(font, size, at, shown, INK);
            x += widths[c];
        }
        y -= LEADING;
    }

    private static float[] widths(List<Column> columns) {
        float total = 0;
        for (Column column : columns) {
            total += column.weight();
        }
        float[] widths = new float[columns.size()];
        for (int c = 0; c < columns.size(); c++) {
            widths[c] = WIDTH * columns.get(c).weight() / total;
        }
        return widths;
    }

    private void ensure(float needed) {
        if (page == null || y - needed < BOTTOM) {
            newPage();
        }
    }

    private void newPage() {
        try {
            closePage();
            PDPage next = new PDPage(PAGE);
            document.addPage(next);
            page = new PDPageContentStream(document, next);
            y = TOP;
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void closePage() throws IOException {
        if (page != null) {
            page.close();
            page = null;
        }
    }

    private void write(PDType1Font font, float size, float x, String text, float[] colour) {
        try {
            text(page, font, size, x, y, text, colour);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void writeAt(PDType1Font font, float size, float x, float baseline, String text) {
        try {
            text(page, font, size, x, baseline, text, INK);
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private void rule(float at, float[] colour) {
        try {
            page.setStrokingColor(colour[0], colour[1], colour[2]);
            page.setLineWidth(0.4f);
            page.moveTo(MARGIN, at);
            page.lineTo(PAGE.getWidth() - MARGIN, at);
            page.stroke();
        } catch (IOException unwritable) {
            throw new UncheckedIOException(unwritable);
        }
    }

    private static void text(PDPageContentStream stream, PDType1Font font, float size, float x, float y, String text,
                             float[] colour) throws IOException {
        if (text.isEmpty()) {
            return;
        }
        stream.beginText();
        stream.setNonStrokingColor(colour[0], colour[1], colour[2]);
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(text);
        stream.endText();
    }

    // ------------------------------------------------------------------------------------------------ text

    static float width(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000f * size;
        } catch (IOException | IllegalArgumentException unmeasurable) {
            return text.length() * size * 0.6f;
        }
    }

    /** {@code text} cut to fit {@code room}, ending in "..." where it was cut. */
    static String fit(String text, PDType1Font font, float size, float room) {
        if (width(font, size, text) <= room) {
            return text;
        }
        String ellipsis = "...";
        int keep = Math.max(0, (int) (text.length() * room / Math.max(width(font, size, text), 1f)));
        while (keep > 0 && width(font, size, text.substring(0, keep) + ellipsis) > room) {
            keep--;
        }
        return keep == 0 ? "" : text.substring(0, keep).stripTrailing() + ellipsis;
    }

    /** {@code text} wrapped at words to {@code room}. */
    static List<String> wrap(String text, PDType1Font font, float size, float room) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String tried = line.isEmpty() ? word : line + " " + word;
            if (width(font, size, tried) <= room || line.isEmpty()) {
                line.setLength(0);
                line.append(tried);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /**
     * What the standard fonts can print: printable ASCII and the Latin-1 letters as they are; anything else without its
     * accent where taking it off leaves such a letter, and '?' where it does not. Control characters become spaces.
     */
    static String printable(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x20 && c < 0x7F || c >= 0xA0 && c <= 0xFF) {
                out.append(c);
            } else if (Character.isISOControl(c) || Character.isWhitespace(c)) {
                out.append(' ');
            } else {
                String bare = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
                out.append(bare.length() == 1 && bare.charAt(0) >= 0x20 && bare.charAt(0) < 0x7F ? bare.charAt(0) : '?');
            }
        }
        return out.toString();
    }
}

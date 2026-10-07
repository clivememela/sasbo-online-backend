package za.co.titandynamix.sasbo_online_backend.payroll;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A {@link ReportDocument} as a PDF in the shape of IT's Reconciliation Report (fixtures' {@code RECON-REPORT.md}): A4
 * landscape, the title and the file's details at the head of every page, a table under each part's heading with its
 * columns repeated on every page it runs onto, and "Page n of m" at the foot. Light: dark ink on white, one teal for
 * rules and column headings (the app's light theme).
 *
 * <p>Text is drawn, never interpreted: a character the standard fonts cannot encode prints as '?', as the letters do
 * ({@code LetterPdf}), so one unusual name never refuses the whole report.
 */
public final class ReportPdf {

    private static final PDRectangle PAGE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 34f;
    private static final float FOOT = 26f;
    private static final float BODY = 7.6f;
    private static final float LEADING = 9.4f;
    private static final float CELL_PAD = 3f;
    private static final int MAX_CELL_LINES = 3;
    private static final Color INK = new Color(0x1f, 0x29, 0x33);
    private static final Color MUTED = new Color(0x55, 0x60, 0x6b);
    private static final Color TEAL = new Color(0x0f, 0x76, 0x6e);
    private static final Color HEADING_FILL = new Color(0xe3, 0xf2, 0xf0);
    private static final Color STRIPE = new Color(0xf5, 0xf7, 0xf9);
    private static final String DASH = "—";

    private ReportPdf() {
    }

    public static byte[] write(ReportDocument document) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Writer writer = new Writer(pdf, document);
            boolean first = true;
            for (ReportDocument.Section section : document.sections()) {
                writer.section(section, first);
                first = false;
            }
            writer.close();
            writer.footers();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("a payroll report could not be laid out as a PDF", e);
        }
    }

    /** Lays the report out page by page. */
    private static final class Writer {

        private final PDDocument pdf;
        private final ReportDocument document;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final float width = PAGE.getWidth() - 2 * MARGIN;
        private PDPageContentStream stream;
        private float y;

        Writer(PDDocument pdf, ReportDocument document) {
            this.pdf = pdf;
            this.document = document;
        }

        void section(ReportDocument.Section section, boolean first) throws IOException {
            if (stream == null || section.newPage() || first) {
                newPage();
            } else {
                y -= LEADING;
            }
            ensure(LEADING * 3);
            text(bold, 10.5f, TEAL, MARGIN, y, section.heading());
            y -= LEADING * 1.6f;
            for (String[] fact : section.facts()) {
                ensure(LEADING);
                text(bold, BODY + 0.4f, INK, MARGIN, y, fact[0]);
                text(regular, BODY + 0.4f, INK, MARGIN + 220, y, fact[1]);
                y -= LEADING;
            }
            List<ReportDocument.Column> columns = section.columns();
            if (columns == null || columns.isEmpty()) {
                return;
            }
            y -= LEADING * 0.5f;
            float[] widths = widths(columns);
            columnHeadings(columns, widths);
            if (section.rows().isEmpty() && section.empty() != null) {
                ensure(LEADING);
                text(regular, BODY, MUTED, MARGIN + CELL_PAD, y - BODY, section.empty());
                y -= LEADING * 1.4f;
            }
            int index = 0;
            for (List<Object> values : section.rows()) {
                row(columns, widths, values, false, index++ % 2 == 1);
            }
            if (section.totals() != null) {
                row(columns, widths, section.totals(), true, false);
            }
        }

        private float[] widths(List<ReportDocument.Column> columns) {
            float total = 0;
            for (ReportDocument.Column column : columns) {
                total += column.weight();
            }
            float[] widths = new float[columns.size()];
            for (int i = 0; i < widths.length; i++) {
                widths[i] = width * columns.get(i).weight() / total;
            }
            return widths;
        }

        private void columnHeadings(List<ReportDocument.Column> columns, float[] widths) throws IOException {
            List<List<String>> cells = new ArrayList<>();
            int lines = 1;
            for (int i = 0; i < columns.size(); i++) {
                List<String> wrapped = wrap(columns.get(i).heading(), bold, BODY, widths[i] - 2 * CELL_PAD);
                cells.add(wrapped);
                lines = Math.max(lines, wrapped.size());
            }
            float height = lines * LEADING + 2 * CELL_PAD;
            ensure(height + LEADING);
            stream.setNonStrokingColor(HEADING_FILL);
            stream.addRect(MARGIN, y - height, width, height);
            stream.fill();
            float x = MARGIN;
            for (int i = 0; i < columns.size(); i++) {
                float lineY = y - CELL_PAD - BODY;
                for (String line : cells.get(i)) {
                    boolean right = columns.get(i).kind() != ReportDocument.Kind.TEXT;
                    float textX = right ? x + widths[i] - CELL_PAD - textWidth(bold, BODY, line) : x + CELL_PAD;
                    text(bold, BODY, INK, textX, lineY, line);
                    lineY -= LEADING;
                }
                x += widths[i];
            }
            y -= height;
            stream.setStrokingColor(TEAL);
            stream.setLineWidth(0.8f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + width, y);
            stream.stroke();
            currentColumns = columns;
            currentWidths = widths;
        }

        private List<ReportDocument.Column> currentColumns;
        private float[] currentWidths;

        private void row(List<ReportDocument.Column> columns, float[] widths, List<Object> values, boolean total,
                         boolean striped) throws IOException {
            PDType1Font font = total ? bold : regular;
            List<List<String>> cells = new ArrayList<>();
            int lines = 1;
            for (int i = 0; i < columns.size(); i++) {
                Object value = i < values.size() ? values.get(i) : null;
                List<String> wrapped = wrap(format(value, columns.get(i).kind()), font, BODY,
                        widths[i] - 2 * CELL_PAD);
                if (wrapped.size() > MAX_CELL_LINES) {
                    wrapped = new ArrayList<>(wrapped.subList(0, MAX_CELL_LINES));
                    wrapped.set(MAX_CELL_LINES - 1, wrapped.get(MAX_CELL_LINES - 1) + "…");
                }
                cells.add(wrapped);
                lines = Math.max(lines, wrapped.size());
            }
            float height = lines * LEADING + CELL_PAD;
            if (y - height < MARGIN + FOOT) {
                newPage();
                columnHeadings(currentColumns, currentWidths);
            }
            if (striped || total) {
                stream.setNonStrokingColor(total ? HEADING_FILL : STRIPE);
                stream.addRect(MARGIN, y - height, width, height);
                stream.fill();
            }
            float x = MARGIN;
            for (int i = 0; i < columns.size(); i++) {
                float lineY = y - CELL_PAD / 2 - BODY;
                for (String line : cells.get(i)) {
                    boolean right = columns.get(i).kind() != ReportDocument.Kind.TEXT;
                    float textX = right ? x + widths[i] - CELL_PAD - textWidth(font, BODY, line) : x + CELL_PAD;
                    text(font, BODY, INK, textX, lineY, line);
                    lineY -= LEADING;
                }
                x += widths[i];
            }
            y -= height;
        }

        private void newPage() throws IOException {
            close();
            PDPage page = new PDPage(PAGE);
            pdf.addPage(page);
            stream = new PDPageContentStream(pdf, page);
            y = PAGE.getHeight() - MARGIN;
            text(bold, 12.5f, INK, MARGIN, y - 10, document.title());
            y -= 24;
            for (String line : document.header()) {
                text(regular, BODY + 0.4f, MUTED, MARGIN, y, line);
                y -= LEADING;
            }
            y -= 3;
            stream.setStrokingColor(TEAL);
            stream.setLineWidth(1.2f);
            stream.moveTo(MARGIN, y);
            stream.lineTo(MARGIN + width, y);
            stream.stroke();
            y -= LEADING * 1.2f;
        }

        private void ensure(float height) throws IOException {
            if (y - height < MARGIN + FOOT) {
                newPage();
            }
        }

        void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        /** "Page n of m" at the foot of every page, written once the pages are counted. */
        void footers() throws IOException {
            int count = pdf.getNumberOfPages();
            for (int i = 0; i < count; i++) {
                PDPage page = pdf.getPage(i);
                try (PDPageContentStream foot = new PDPageContentStream(pdf, page,
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    foot.setStrokingColor(MUTED);
                    foot.setLineWidth(0.3f);
                    foot.moveTo(MARGIN, MARGIN + 10);
                    foot.lineTo(PAGE.getWidth() - MARGIN, MARGIN + 10);
                    foot.stroke();
                    write(foot, regular, 7f, MUTED, MARGIN, MARGIN, "SASBO Online · Payroll Deductions and "
                            + "Reconciliation");
                    String number = "Page " + (i + 1) + " of " + count;
                    write(foot, regular, 7f, MUTED, PAGE.getWidth() - MARGIN - textWidth(regular, 7f, number),
                            MARGIN, number);
                }
            }
        }

        private void text(PDType1Font font, float size, Color color, float x, float atY, String value)
                throws IOException {
            write(stream, font, size, color, x, atY, value);
        }

        private static void write(PDPageContentStream on, PDType1Font font, float size, Color color, float x, float y,
                                  String value) throws IOException {
            on.beginText();
            on.setFont(font, size);
            on.setNonStrokingColor(color);
            on.newLineAtOffset(x, y);
            on.showText(printable(value, font));
            on.endText();
        }
    }

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(
            Locale.UK));

    static String format(Object value, ReportDocument.Kind kind) {
        if (value == null) {
            return DASH;
        }
        if (value instanceof BigDecimal amount) {
            synchronized (MONEY) {
                return (kind == ReportDocument.Kind.MONEY ? "R" : "") + MONEY.format(amount);
            }
        }
        return String.valueOf(value);
    }

    static List<String> wrap(String text, PDType1Font font, float size, float maxWidth) throws IOException {
        String clean = printable(text == null ? DASH : text, font);
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : clean.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (textWidth(font, size, candidate) <= maxWidth || current.isEmpty()) {
                if (current.isEmpty() && textWidth(font, size, word) > maxWidth) {
                    lines.add(cut(word, font, size, maxWidth));
                    continue;
                }
                current = new StringBuilder(candidate);
            } else {
                lines.add(current.toString());
                current = new StringBuilder(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static String cut(String word, PDType1Font font, float size, float maxWidth) throws IOException {
        StringBuilder cut = new StringBuilder();
        for (char c : word.toCharArray()) {
            if (textWidth(font, size, cut.toString() + c + "…") > maxWidth) {
                return cut + "…";
            }
            cut.append(c);
        }
        return cut.toString();
    }

    static float textWidth(PDType1Font font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** {@code text} with every character the font cannot encode as '?', and control characters as spaces. */
    static String printable(String text, PDType1Font font) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isISOControl(codePoint)) {
                out.append(' ');
                return;
            }
            String character = new String(Character.toChars(codePoint));
            try {
                font.encode(character);
                out.append(character);
            } catch (IllegalArgumentException | IOException unencodable) {
                out.append('?');
            }
        });
        return out.toString();
    }
}

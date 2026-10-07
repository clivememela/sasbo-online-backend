package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Block;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.DateLine;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Heading;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Letter;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Paragraph;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Points;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Recipient;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.References;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Row;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Signature;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Specimen;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Subject;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Table;
import za.co.titandynamix.sasbo_online_backend.documents.LetterTypeface.Face;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * The letters, laid out on SASBO's letterhead as PDFs: A4, light, every font embedded, every word text a reader can
 * select and search (Stephne, DOD-33: "a modern layout -- show it to us").
 *
 * <h2>The page</h2>
 * The logo at the top left and the address block at the top right, as her letterhead sets them; the member's address
 * at the left with the date and the membership number opposite; the subject in the letterhead's teal; short
 * paragraphs; the amount and dates in a small ruled table; the points the member must act on; the signing position;
 * and at the foot of every page the union's line and "Page X of Y", as the letterhead's footer has it.
 *
 * <h2>The same text makes the same bytes</h2>
 * Nothing is dated by the clock: no creation date, and the file identifier is taken from the letters' own text. So
 * a letter rendered twice is byte for byte the same document, and a test can say so.
 *
 * <h2>A batch is one parse of the fonts</h2>
 * {@link #renderAll} lays many letters into one PDF, each from a new page with its own page numbers; {@link #renderZip}
 * writes each as a PDF of its own into a ZIP. Both parse the typeface once and embed from that parse into every
 * document, and both stream to the caller's output.
 */
public final class LetterLayout {

    static final float PAGE_WIDTH = PDRectangle.A4.getWidth();
    static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();
    static final float MARGIN = 62f;
    static final float WIDTH = PAGE_WIDTH - 2 * MARGIN;
    /** The letterhead's top edge, from the top of the page. */
    static final float TOP = 38f;
    static final float LOGO_HEIGHT = 76f;
    /** Where the content stops, from the foot of the page: below it are the rule and the footer. */
    static final float BOTTOM = 58f;
    private static final float FOOTER_RULE = 46f;
    private static final float FOOTER_BASELINE = 33f;
    private static final float AFTER_LETTERHEAD = 22f;
    /** A line's baseline below its top, as a share of its size. */
    private static final float ASCENT = 0.8f;
    private static final float TABLE_LABELS = 132f;
    private static final float TABLE_PADDING = 3.5f;
    private static final float POINT_INDENT = 16f;

    private static final Rgb INK = new Rgb(0x21, 0x2B, 0x2E);
    /** The letterhead's own teal (its address block is set in #006666). */
    private static final Rgb TEAL = new Rgb(0x00, 0x66, 0x66);
    private static final Rgb MUTED = new Rgb(0x5E, 0x6E, 0x70);
    private static final Rgb RULE = new Rgb(0xD3, 0xDF, 0xDF);
    private static final Rgb RULE_STRONG = new Rgb(0x9C, 0xC3, 0xC3);
    private static final Rgb AMBER = new Rgb(0x9A, 0x5B, 0x00);

    private static final Type BODY = new Type(Face.REGULAR, 10f, 13.6f, INK);
    private static final Type SUBJECT = new Type(Face.BOLD, 13.5f, 17f, TEAL);
    private static final Type HEADING = new Type(Face.BOLD, 10.5f, 14f, TEAL);
    private static final Type LABEL = new Type(Face.REGULAR, 8.5f, 13f, MUTED);
    private static final Type VALUE = new Type(Face.REGULAR, 10f, 13f, INK);
    private static final Type LETTERHEAD = new Type(Face.ITALIC, 7.5f, 9f, TEAL);
    private static final Type SIGNATORY = new Type(Face.BOLD, 10f, 14f, INK);
    private static final Type SIGNATORY_LINE = new Type(Face.REGULAR, 9.5f, 13f, MUTED);
    private static final Type FOOTER = new Type(Face.REGULAR, 7f, 9f, MUTED);
    private static final Type FOOTER_NUMBER = new Type(Face.BOLD, 7f, 9f, MUTED);
    private static final Type SPECIMEN = new Type(Face.ITALIC, 8f, 10f, AMBER);

    /** A ZIP entry's file name: no folders, nothing that climbs out of one. */
    private static final Pattern ENTRY_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    /** Every ZIP entry's time, fixed, so the same letters make the same archive. */
    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    private LetterLayout() {
    }

    /** One letter as a PDF. */
    public static byte[] render(String text, LetterTypeface typeface, Letterhead letterhead) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        renderAll(List.of(text), typeface, letterhead, out);
        return out.toByteArray();
    }

    /** Letters as one PDF, written to {@code out}: each from a new page, numbered "Page X of Y" within itself. */
    public static void renderAll(List<String> texts, LetterTypeface typeface, Letterhead letterhead,
                                 OutputStream out) {
        if (texts.isEmpty()) {
            throw new IllegalArgumentException("There are no letters to lay out");
        }
        try (LetterTypeface.Opened faces = typeface.open(); PDDocument pdf = new PDDocument()) {
            Document document = new Document(pdf, faces, letterhead);
            for (String text : texts) {
                document.letter(LetterMarkup.parse(text));
            }
            document.finish(texts);
            pdf.save(new Unclosed(out));
        } catch (IOException e) {
            throw new UncheckedIOException("The letters could not be laid out as a PDF", e);
        }
    }

    /** A letter and the file name it takes in a ZIP. */
    public record Named(String fileName, String text) {
        public Named {
            if (fileName == null || !ENTRY_NAME.matcher(fileName).matches()) {
                throw new IllegalArgumentException("A letter's file name is letters, digits, '.', '_' and '-' only");
            }
        }
    }

    /** Letters as a ZIP of PDFs, one each, written to {@code out}. */
    public static void renderZip(List<Named> letters, LetterTypeface typeface, Letterhead letterhead,
                                 OutputStream out) {
        try (LetterTypeface.Opened faces = typeface.open(); ZipOutputStream zip = new ZipOutputStream(new Unclosed(out))) {
            for (Named letter : letters) {
                ZipEntry entry = new ZipEntry(letter.fileName());
                entry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(entry);
                try (PDDocument pdf = new PDDocument()) {
                    Document document = new Document(pdf, faces, letterhead);
                    document.letter(LetterMarkup.parse(letter.text()));
                    document.finish(List.of(letter.text()));
                    pdf.save(new Unclosed(zip));
                }
                zip.closeEntry();
            }
            zip.finish();
        } catch (IOException e) {
            throw new UncheckedIOException("The letters could not be written as a ZIP", e);
        }
    }

    // ------------------------------------------------------------------------------------------

    /** A colour, 0-255 a channel. */
    private record Rgb(int r, int g, int b) {
    }

    /** How a line is set: face, size, the distance to the next line, colour. */
    private record Type(Face face, float size, float leading, Rgb color) {
    }

    /** A stretch of a line printed in one font: the typeface's, or the fallback's for glyphs it lacks. */
    private record Run(TrueTypeFont font, String text) {
    }

    /** One PDF being written: the page in hand, the cursor down it, and the fonts embedded so far. */
    private static final class Document {

        private final PDDocument pdf;
        private final LetterTypeface.Opened faces;
        private final Letterhead letterhead;
        private final Map<TrueTypeFont, PDType0Font> fonts = new IdentityHashMap<>();
        private final List<PDPage> pagesOfLetter = new ArrayList<>();
        private final List<String> subjects = new ArrayList<>();
        private PDImageXObject logo;
        private PDPageContentStream stream;
        /** The cursor: the top of the next line, from the top of the page. */
        private float y;

        Document(PDDocument pdf, LetterTypeface.Opened faces, Letterhead letterhead) {
            this.pdf = pdf;
            this.faces = faces;
            this.letterhead = letterhead;
        }

        void letter(Letter letter) throws IOException {
            subjects.add(letter.subject());
            pagesOfLetter.clear();
            newPage();
            y = drawLetterhead() + AFTER_LETTERHEAD;
            List<Block> blocks = letter.blocks();
            Block previous = null;
            for (int i = 0; i < blocks.size(); i++) {
                Block block = blocks.get(i);
                if (block instanceof Specimen specimen) {
                    centred(stream, specimen.text(), SPECIMEN, PAGE_WIDTH / 2, 22f);
                    continue;
                }
                if (addressPart(block)) {
                    int end = i;
                    while (end < blocks.size() && addressPart(blocks.get(end))) {
                        end++;
                    }
                    addressArea(blocks.subList(i, end));
                    previous = blocks.get(end - 1);
                    i = end - 1;
                    continue;
                }
                y += spaceBefore(block, previous);
                switch (block) {
                    case Subject subject -> lines(subject.text(), SUBJECT, MARGIN, WIDTH, 0f);
                    case Heading heading -> lines(heading.text(), HEADING, MARGIN, WIDTH, 2 * BODY.leading());
                    case Paragraph paragraph -> lines(paragraph.text(), BODY, MARGIN, WIDTH, 0f);
                    case Table table -> table(table.rows());
                    case Points points -> points(points.items());
                    case Signature signature -> signature(signature.lines());
                    default -> throw new IllegalStateException("No layout for " + block.getClass().getSimpleName());
                }
                previous = block;
            }
            endPage();
            footers();
        }

        /** The document's description, its language, and a file identifier taken from its letters' text. */
        void finish(List<String> texts) {
            PDDocumentInformation info = pdf.getDocumentInformation();
            info.setTitle(subjects.size() == 1 && subjects.get(0) != null ? subjects.get(0) : "Letters from SASBO");
            info.setAuthor("SASBO, The Finance Union");
            pdf.getDocumentCatalog().setLanguage("en-GB");
            byte[] id = Arrays.copyOf(sha256(String.join("\u0000", texts)), 16);
            COSArray ids = new COSArray();
            ids.add(new COSString(id));
            ids.add(new COSString(id));
            pdf.getDocument().getTrailer().setItem(COSName.ID, ids);
        }

        // -------------------------------------------------------------------------------- blocks

        private static boolean addressPart(Block block) {
            return block instanceof DateLine || block instanceof Recipient || block instanceof References;
        }

        private static float spaceBefore(Block block, Block previous) {
            if (previous == null) {
                return 0f;
            }
            return switch (block) {
                case Subject s -> 18f;
                case Heading h -> 12f;
                case Paragraph p -> previous instanceof Subject ? 10f : previous instanceof Heading ? 3f : 9f;
                case Points p -> previous instanceof Heading ? 4f : 8f;
                case Signature s -> 10f;
                default -> 9f;
            };
        }

        /** The member's address at the left; the date and the references opposite, right-aligned. */
        private void addressArea(List<Block> parts) throws IOException {
            float columnWidth = WIDTH * 0.55f;
            List<String> left = new ArrayList<>();
            List<String> dates = new ArrayList<>();
            List<Row> references = new ArrayList<>();
            for (Block part : parts) {
                switch (part) {
                    case Recipient recipient -> {
                        for (String line : recipient.lines()) {
                            left.addAll(wrap(line, VALUE, columnWidth));
                        }
                    }
                    case DateLine date -> dates.add(date.text());
                    case References refs -> references.addAll(refs.rows());
                    default -> throw new IllegalStateException("not part of the address area");
                }
            }
            float top = y;
            float right = PAGE_WIDTH - MARGIN;
            for (int i = 0; i < left.size(); i++) {
                text(stream, left.get(i), VALUE, MARGIN, baseline(top + i * VALUE.leading(), VALUE));
            }
            int row = 0;
            for (String date : dates) {
                rightAligned(stream, date, VALUE, right, baseline(top + row++ * VALUE.leading(), VALUE));
            }
            for (Row reference : references) {
                // Label, then value, left to right: the order a reader of the PDF's text meets them.
                float baseline = baseline(top + row++ * VALUE.leading(), VALUE);
                float valueX = right - width(reference.value(), VALUE);
                rightAligned(stream, reference.label(), LABEL, valueX - 6f, baseline);
                text(stream, reference.value(), VALUE, valueX, baseline);
            }
            y = top + Math.max(left.size(), row) * VALUE.leading();
        }

        /** {@code text} wrapped to {@code width} and drawn, a line at a time; kept with {@code keepWith} below. */
        private void lines(String text, Type type, float x, float width, float keepWith) throws IOException {
            List<String> wrapped = wrap(text, type, width);
            ensure(type.leading() + keepWith);
            for (String line : wrapped) {
                ensure(type.leading());
                text(stream, line, type, x, baseline(y, type));
                y += type.leading();
            }
        }

        /** The small ruled table: labels in grey at the left, values beside them. */
        private void table(List<Row> rows) throws IOException {
            float valueX = MARGIN + TABLE_LABELS;
            float valueWidth = WIDTH - TABLE_LABELS - 6f;
            ensure(TABLE_PADDING * 2 + VALUE.leading() * 2);
            rule(y, RULE_STRONG, 0.75f);
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                List<String> labels = wrap(row.label(), LABEL, TABLE_LABELS - 14f);
                List<String> values = wrap(row.value(), VALUE, valueWidth);
                float height = TABLE_PADDING * 2 + Math.max(labels.size(), values.size()) * VALUE.leading();
                if (y + height > PAGE_HEIGHT - BOTTOM) {
                    newPage();
                    y = TOP;
                    rule(y, RULE_STRONG, 0.75f);
                }
                float first = baseline(y + TABLE_PADDING, VALUE);
                for (int l = 0; l < labels.size(); l++) {
                    text(stream, labels.get(l), LABEL, MARGIN + 6f, first + l * VALUE.leading());
                }
                for (int v = 0; v < values.size(); v++) {
                    text(stream, values.get(v), VALUE, valueX, first + v * VALUE.leading());
                }
                y += height;
                boolean last = i == rows.size() - 1;
                rule(y, last ? RULE_STRONG : RULE, last ? 0.75f : 0.5f);
            }
        }

        /** The points: a bullet, and the text indented beside it. */
        private void points(List<String> items) throws IOException {
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    y += 3f;
                }
                List<String> wrapped = wrap(items.get(i), BODY, WIDTH - POINT_INDENT);
                for (int l = 0; l < wrapped.size(); l++) {
                    ensure(BODY.leading());
                    float baseline = baseline(y, BODY);
                    if (l == 0) {
                        text(stream, "•", BODY, MARGIN + 3f, baseline);
                    }
                    text(stream, wrapped.get(l), BODY, MARGIN + POINT_INDENT, baseline);
                    y += BODY.leading();
                }
            }
        }

        /** The signing position in bold, the union's name below it. */
        private void signature(List<String> lines) throws IOException {
            ensure(SIGNATORY.leading() + (lines.size() - 1) * SIGNATORY_LINE.leading());
            for (int i = 0; i < lines.size(); i++) {
                Type type = i == 0 ? SIGNATORY : SIGNATORY_LINE;
                text(stream, lines.get(i), type, MARGIN, baseline(y, type));
                y += type.leading();
            }
        }

        // -------------------------------------------------------------------------------- the page

        /** The logo and the address block; answers where the letterhead ends, from the top of the page. */
        private float drawLetterhead() throws IOException {
            if (logo == null) {
                logo = letterhead.logo(pdf);
            }
            float logoWidth = LOGO_HEIGHT * letterhead.logoAspect();
            stream.drawImage(logo, MARGIN, PAGE_HEIGHT - TOP - LOGO_HEIGHT, logoWidth, LOGO_HEIGHT);
            float lineTop = TOP;
            for (String line : letterhead.address()) {
                if (!line.isBlank()) {
                    rightAligned(stream, line, LETTERHEAD, PAGE_WIDTH - MARGIN, baseline(lineTop, LETTERHEAD));
                }
                lineTop += LETTERHEAD.leading();
            }
            return Math.max(TOP + LOGO_HEIGHT, lineTop);
        }

        /** The rule and the footer on each page of the letter just laid out: the union's line, "Page X of Y". */
        private void footers() throws IOException {
            int count = pagesOfLetter.size();
            for (int i = 0; i < count; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(pdf, pagesOfLetter.get(i),
                        PDPageContentStream.AppendMode.APPEND, true, true)) {
                    // PDF measures up from the foot of the page.
                    footer.setStrokingColor(RULE.r() / 255f, RULE.g() / 255f, RULE.b() / 255f);
                    footer.setLineWidth(0.5f);
                    footer.moveTo(MARGIN, FOOTER_RULE);
                    footer.lineTo(PAGE_WIDTH - MARGIN, FOOTER_RULE);
                    footer.stroke();
                    float baseline = PAGE_HEIGHT - FOOTER_BASELINE;
                    text(footer, letterhead.footer(), FOOTER, MARGIN, baseline);
                    String page = String.valueOf(i + 1);
                    String of = String.valueOf(count);
                    float x = PAGE_WIDTH - MARGIN - width("Page ", FOOTER) - width(page, FOOTER_NUMBER)
                            - width(" of ", FOOTER) - width(of, FOOTER_NUMBER);
                    x = text(footer, "Page ", FOOTER, x, baseline);
                    x = text(footer, page, FOOTER_NUMBER, x, baseline);
                    x = text(footer, " of ", FOOTER, x, baseline);
                    text(footer, of, FOOTER_NUMBER, x, baseline);
                }
            }
        }

        private void newPage() throws IOException {
            endPage();
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            pagesOfLetter.add(page);
            stream = new PDPageContentStream(pdf, page);
        }

        private void endPage() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        /** A new page, without the letterhead, when {@code height} more would run into the footer. */
        private void ensure(float height) throws IOException {
            if (y + height > PAGE_HEIGHT - BOTTOM) {
                newPage();
                y = TOP;
            }
        }

        private void rule(float top, Rgb color, float lineWidth) throws IOException {
            stream.setStrokingColor(color.r() / 255f, color.g() / 255f, color.b() / 255f);
            stream.setLineWidth(lineWidth);
            stream.moveTo(MARGIN, PAGE_HEIGHT - top);
            stream.lineTo(PAGE_WIDTH - MARGIN, PAGE_HEIGHT - top);
            stream.stroke();
        }

        // -------------------------------------------------------------------------------- text

        private static float baseline(float top, Type type) {
            return top + type.size() * ASCENT;
        }

        /** Draws {@code text} with its left edge at {@code x}; answers where it ends. */
        private float text(PDPageContentStream to, String text, Type type, float x, float baselineFromTop)
                throws IOException {
            if (text.isEmpty()) {
                return x;
            }
            List<Run> runs = runs(text, type.face());
            to.setNonStrokingColor(type.color().r() / 255f, type.color().g() / 255f, type.color().b() / 255f);
            to.beginText();
            to.newLineAtOffset(x, PAGE_HEIGHT - baselineFromTop);
            float end = x;
            for (Run run : runs) {
                PDType0Font font = font(run.font());
                to.setFont(font, type.size());
                to.showText(run.text());
                end += font.getStringWidth(run.text()) / 1000f * type.size();
            }
            to.endText();
            return end;
        }

        private void rightAligned(PDPageContentStream to, String text, Type type, float right, float baseline)
                throws IOException {
            text(to, text, type, right - width(text, type), baseline);
        }

        private void centred(PDPageContentStream to, String text, Type type, float centre, float baseline)
                throws IOException {
            text(to, text, type, centre - width(text, type) / 2, baseline);
        }

        private float width(String text, Type type) throws IOException {
            float width = 0f;
            for (Run run : runs(text, type.face())) {
                width += font(run.font()).getStringWidth(run.text()) / 1000f * type.size();
            }
            return width;
        }

        /**
         * {@code text} broken at spaces into lines no wider than {@code maxWidth}. A word wider than the line stands
         * alone on it. There is no kerning, so a line's width is its words' widths and its spaces'.
         */
        private List<String> wrap(String text, Type type, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            String body = text.strip();
            if (body.isEmpty()) {
                lines.add("");
                return lines;
            }
            float space = width(" ", type);
            StringBuilder line = new StringBuilder();
            float lineWidth = 0f;
            for (String word : body.split(" +")) {
                float wordWidth = width(word, type);
                if (line.isEmpty()) {
                    line.append(word);
                    lineWidth = wordWidth;
                } else if (lineWidth + space + wordWidth <= maxWidth) {
                    line.append(' ').append(word);
                    lineWidth += space + wordWidth;
                } else {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                    lineWidth = wordWidth;
                }
            }
            lines.add(line.toString());
            return lines;
        }

        /**
         * {@code text} split into runs by the font that prints each character: the typeface's face where it has the
         * glyph, the fallback where only it does. A character neither prints refuses the letter (register A68).
         */
        private List<Run> runs(String text, Face face) {
            TrueTypeFont primary = faces.face(face);
            List<Run> runs = new ArrayList<>(1);
            StringBuilder current = new StringBuilder(text.length());
            TrueTypeFont currentFont = null;
            for (int i = 0; i < text.length(); ) {
                int codePoint = text.codePointAt(i);
                TrueTypeFont font = primary;
                if (!faces.prints(primary, codePoint)) {
                    font = faces.fallback();
                    if (!faces.prints(font, codePoint)) {
                        throw new UnprintableCharacterException(codePoint);
                    }
                }
                if (font != currentFont && !current.isEmpty()) {
                    runs.add(new Run(currentFont, current.toString()));
                    current.setLength(0);
                }
                currentFont = font;
                current.appendCodePoint(codePoint);
                i += Character.charCount(codePoint);
            }
            if (!current.isEmpty()) {
                runs.add(new Run(currentFont, current.toString()));
            }
            return runs;
        }

        /** {@code font} embedded in this document -- once, as a subset of the glyphs its letters print. */
        private PDType0Font font(TrueTypeFont font) {
            return fonts.computeIfAbsent(font, parsed -> {
                try {
                    return PDType0Font.load(pdf, parsed, true);
                } catch (IOException e) {
                    throw new UncheckedIOException("A letter font could not be embedded", e);
                }
            });
        }
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", impossible);
        }
    }

    /** {@code out}, kept open: PDFBox closes the stream it saves to, and a ZIP or a response has more to come. */
    private static final class Unclosed extends OutputStream {

        private final OutputStream out;

        Unclosed(OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }
    }
}

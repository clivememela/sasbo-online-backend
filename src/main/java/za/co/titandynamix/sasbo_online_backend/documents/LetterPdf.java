package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The welcome letter's PDF path: A4, 48-point margins, each line wrapped at words to the width, a page added when one
 * fills, a blank line where the text has one -- exactly the layout the welcome letter had when it had its own copy
 * of this code.
 *
 * <p>The debit order letters left it in October 2026 for {@link LetterLayout}: the letterhead, a modern layout and an
 * embedded typeface (DOD-33). The welcome letter stays here, in a standard font, until its own owners move it: a
 * character the font cannot encode is printed as '?' -- register A68, whose pins are the automation arc's
 * ({@code WelcomeLetterDefectsTest}); moving the welcome letter onto {@link LetterLayout}'s embedded fonts is the fix.
 *
 * <p>🔒 A letterhead, when given, is an image EMBEDDED in the document and drawn at the head of the first page --
 * never a remote reference, which a mail client would fetch (SD3-C).
 */
public final class LetterPdf {

    /** How the text is set: the welcome letter in Times. */
    public record Style(Standard14Fonts.FontName font, float size, float leading) {
        public static final Style WELCOME = new Style(Standard14Fonts.FontName.TIMES_ROMAN, 12f, 14.5f);
    }

    static final float MARGIN = 48f;

    /** The letterhead's height on the page; its width follows from the image. */
    static final float LETTERHEAD_HEIGHT = 84f;

    private LetterPdf() {
    }

    /**
     * The document, one entry of {@code lines} a line: wrapped when it is wider than the page, a blank line when
     * it is blank. {@code letterhead} is PNG or JPEG bytes, or {@code null} for none.
     */
    public static byte[] render(List<String> lines, Style style, byte[] letterhead) {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(style.font());
            Pages pages = new Pages(pdf, font, style);
            if (letterhead != null) {
                pages.letterhead(PDImageXObject.createFromByteArray(pdf, letterhead, "letterhead"));
            }
            float width = PDRectangle.A4.getWidth() - 2 * MARGIN;
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    pages.blank();
                    continue;
                }
                for (String wrapped : wrap(printable(line, font), font, style.size(), width)) {
                    pages.line(wrapped);
                }
            }
            pages.close();
            pdf.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("A letter could not be laid out as a PDF", e);
        }
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

    /**
     * {@code text} broken at words into lines no wider than {@code maxWidth}. A line's leading spaces are kept on
     * its first line -- the templates align with them -- and a word wider than the page stands on a line alone.
     */
    static List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        String body = text.stripLeading();
        String indent = text.substring(0, text.length() - body.length());
        List<String> lines = new ArrayList<>();
        String current = null;
        for (String word : body.split("\\s+")) {
            String candidate = current == null ? indent + word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000f * fontSize <= maxWidth) {
                current = candidate;
            } else {
                if (current != null) {
                    lines.add(current);
                }
                current = word;
            }
        }
        if (current != null) {
            lines.add(current);
        }
        return lines;
    }

    /** The page being written, and the next one when it fills. */
    private static final class Pages {

        private final PDDocument pdf;
        private final PDType1Font font;
        private final Style style;
        private PDPageContentStream stream;
        private float y;

        Pages(PDDocument pdf, PDType1Font font, Style style) throws IOException {
            this.pdf = pdf;
            this.font = font;
            this.style = style;
            newPage();
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.endText();
                stream.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            y = page.getMediaBox().getHeight() - MARGIN;
            stream = new PDPageContentStream(pdf, page);
            startText();
        }

        private void startText() throws IOException {
            stream.beginText();
            stream.setFont(font, style.size());
            stream.newLineAtOffset(MARGIN, y);
        }

        /** Draws the letterhead at the head of the page, and starts the text below it. */
        void letterhead(PDImageXObject image) throws IOException {
            stream.endText();
            float height = LETTERHEAD_HEIGHT;
            float width = image.getWidth() * height / image.getHeight();
            float top = PDRectangle.A4.getHeight() - MARGIN;
            stream.drawImage(image, MARGIN, top - height, width, height);
            y = top - height - style.leading();
            startText();
        }

        void line(String text) throws IOException {
            if (y - style.leading() <= MARGIN) {
                newPage();
            }
            stream.showText(text);
            stream.newLineAtOffset(0, -style.leading());
            y -= style.leading();
        }

        void blank() throws IOException {
            if (y - style.leading() <= MARGIN) {
                newPage();
            }
            stream.newLineAtOffset(0, -style.leading());
            y -= style.leading();
        }

        void close() throws IOException {
            stream.endText();
            stream.close();
        }
    }
}

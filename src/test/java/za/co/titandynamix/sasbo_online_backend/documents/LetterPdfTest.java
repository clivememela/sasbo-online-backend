package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The welcome letter's PDF path. (The debit order letters are laid out by {@code LetterLayout} since October 2026.)
 */
@DisplayName("LetterPdf -- the welcome letter's PDF path")
class LetterPdfTest {

    private static byte[] letterhead() throws IOException {
        try (InputStream stream = LetterPdfTest.class.getClassLoader().getResourceAsStream("letters/letterhead/logo.png")) {
            assertThat(stream).as("the letterhead on the classpath").isNotNull();
            return stream.readAllBytes();
        }
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static List<PDImageXObject> imagesOn(PDPage page) throws IOException {
        List<PDImageXObject> images = new ArrayList<>();
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().getXObject(name) instanceof PDImageXObject image) {
                images.add(image);
            }
        }
        return images;
    }

    @Test
    @DisplayName("lays out every line, in order, as text a reader can find")
    void everyLineIsText() throws IOException {
        byte[] pdf = LetterPdf.render(List.of("Dear Member", "", "Your debit was returned."), LetterPdf.Style.WELCOME, null);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = textOf(pdf);
        assertThat(text).contains("Dear Member").contains("Your debit was returned.");
        assertThat(text.indexOf("Dear Member")).isLessThan(text.indexOf("Your debit was returned."));
    }

    @Test
    @DisplayName("🔒 SD3-C: the letterhead is an image embedded in the document -- nothing a mail client would fetch")
    void theLetterheadIsEmbedded() throws IOException {
        try (PDDocument document = Loader.loadPDF(LetterPdf.render(List.of("Dear Member"), LetterPdf.Style.WELCOME,
                letterhead()))) {
            PDPage first = document.getPage(0);
            assertThat(imagesOn(first)).singleElement()
                    .satisfies(image -> assertThat(image.getWidth()).as("the logo of SASBO's letterhead").isEqualTo(410));
            assertThat(first.getAnnotations()).as("no link, no remote reference").isEmpty();
        }
        // The must-pass twin: without a letterhead there is no image at all.
        try (PDDocument document = Loader.loadPDF(LetterPdf.render(List.of("Dear Member"), LetterPdf.Style.WELCOME, null))) {
            assertThat(imagesOn(document.getPage(0))).isEmpty();
        }
    }

    @Test
    @DisplayName("🔴 A68, still open on the welcome letter: a character the standard fonts cannot encode prints as '?'")
    void anUnencodableCharacterIsPrinted() throws IOException {
        // U+014A and an emoji are outside the standard fonts' encoding; e-diaeresis is inside it.
        String text = textOf(LetterPdf.render(List.of("Dear Ŋgwenya 😀 and Zoë"), LetterPdf.Style.WELCOME,
                null));

        assertThat(text).contains("Zoë").contains("?gwenya");
    }

    @Test
    @DisplayName("a line's leading spaces are kept; a long line wraps at words, inside the width")
    void alignmentAndWrapping() throws IOException {
        PDType1Font times = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

        assertThat(LetterPdf.wrap("          Membership Number M-100001", times, 10.5f, 499f))
                .containsExactly("          Membership Number M-100001");
        List<String> wrapped = LetterPdf.wrap("word ".repeat(40).trim(), times, 10.5f, 100f);
        assertThat(wrapped).hasSizeGreaterThan(1);
        for (String line : wrapped) {
            assertThat(times.getStringWidth(line) / 1000f * 10.5f).as(line).isLessThanOrEqualTo(100f);
        }
        assertThat(String.join(" ", wrapped)).as("nothing lost in the wrapping").isEqualTo("word ".repeat(40).trim());
    }

    @Test
    @DisplayName("a letter longer than a page turns the page, and loses nothing")
    void aLongLetterTurnsThePage() throws IOException {
        List<String> lines = new ArrayList<>();
        for (int i = 1; i <= 80; i++) {
            lines.add("Line number " + i + ".");
        }
        byte[] pdf = LetterPdf.render(lines, LetterPdf.Style.WELCOME, letterhead());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
        String text = textOf(pdf);
        assertThat(text).contains("Line number 1.").contains("Line number 80.");
    }
}

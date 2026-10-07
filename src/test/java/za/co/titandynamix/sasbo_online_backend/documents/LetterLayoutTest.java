package za.co.titandynamix.sasbo_online_backend.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The letters as PDFs (DOD-33's modern layout): A4, every font embedded, text a reader can select, the same text
 * making the same bytes, accented names printed whole, and many letters in a batch in seconds. Invented members only.
 */
@DisplayName("LetterLayout -- the letters as PDFs")
class LetterLayoutTest {

    private static final LetterTypeface TYPEFACE = LetterTypeface.bundled();
    private static final Letterhead LETTERHEAD = Letterhead.sasbo();

    /** An invented member's letter, in the shape the templates make. */
    private static String letter(String name, String membershipNumber) {
        return """
                @date 14 October 2026
                @to %s
                @to 12 Invented Street
                @to Testville
                @to 2021
                @ref Membership number | %s

                # Your debit order for September 2026 was returned unpaid

                Dear Member

                Your bank returned your SASBO subscription debit order for September 2026 unpaid, because there were
                not enough funds in your account.

                | Subscription for | September 2026
                | Amount | R174.00
                | Debit date | 25 September 2026
                | Bank's reason | Insufficient funds
                | Bank account | Alpha Bank ****9911

                ## What you need to do

                - Please make sure there are enough funds in your account for both debits.
                - If your banking details have changed, please send us your new details.

                Yours sincerely

                @sign Membership Administration
                @sign SASBO, The Finance Union
                """.formatted(name, membershipNumber);
    }

    private static String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static List<PDFont> fontsOn(PDPage page) throws IOException {
        List<PDFont> fonts = new ArrayList<>();
        PDResources resources = page.getResources();
        for (COSName name : resources.getFontNames()) {
            fonts.add(resources.getFont(name));
        }
        return fonts;
    }

    @Test
    @DisplayName("a letter is one A4 page, on the letterhead, every font embedded, every word text a reader can find")
    void aLetterIsA4WithEmbeddedFontsAndText() throws IOException {
        byte[] pdf = LetterLayout.render(letter("Ms Z Exemplé", "M-099116"), TYPEFACE, LETTERHEAD);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            PDPage page = document.getPage(0);
            assertThat(page.getMediaBox().getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(page.getMediaBox().getHeight()).isEqualTo(PDRectangle.A4.getHeight());
            assertThat(fontsOn(page)).isNotEmpty().allSatisfy(font -> {
                assertThat(font.isEmbedded()).as(font.getName() + " embedded").isTrue();
                assertThat(font.getName()).contains("AdventorTT");
            });
            assertThat(page.getResources().getXObjectNames()).as("the letterhead's logo, embedded once").hasSize(1);
            assertThat(page.getAnnotations()).as("no link, nothing a mail client would fetch").isEmpty();
            assertThat(document.getDocumentInformation().getTitle())
                    .isEqualTo("Your debit order for September 2026 was returned unpaid");
            assertThat(document.getDocumentCatalog().getLanguage()).isEqualTo("en-GB");
        }
        assertThat(textOf(pdf)).contains("Sasbo Headquarters", "members@sasbo.org.za", "Ms Z Exemplé",
                "Membership number M-099116", "Your debit order for September 2026 was returned unpaid",
                "Amount R174.00", "Membership Administration", "Page 1 of 1");
    }

    @Test
    @DisplayName("the same text makes the same bytes; another member's makes others; the file's identifier is its text's")
    void theSameTextMakesTheSameBytes() throws Exception {
        String text = letter("Ms Z Exemplé", "M-099116");
        byte[] first = LetterLayout.render(text, TYPEFACE, LETTERHEAD);
        byte[] second = LetterLayout.render(text, TYPEFACE, LETTERHEAD);

        assertThat(second).isEqualTo(first);
        assertThat(LetterLayout.render(letter("Mr Y Exemplar", "M-099117"), TYPEFACE, LETTERHEAD)).isNotEqualTo(first);
        try (PDDocument document = Loader.loadPDF(first)) {
            COSArray ids = document.getDocument().getTrailer().getCOSArray(COSName.ID);
            byte[] expected = Arrays.copyOf(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)), 16);
            assertThat(((COSString) ids.getObject(0)).getBytes()).isEqualTo(expected);
            assertThat(document.getDocumentInformation().getCreationDate()).as("dated by nothing").isNull();
        }
    }

    @Test
    @DisplayName("🔤 A68: names with é, ë, ô, ñ and å print whole, in the typeface itself, and read back letter for letter")
    void accentedNamesPrintWhole() throws IOException {
        String name = "Ms Zoë Ñandú-Côté Ångström-Exemplé";
        byte[] pdf = LetterLayout.render(letter(name, "M-099116"), TYPEFACE, LETTERHEAD);

        assertThat(textOf(pdf)).contains(name).doesNotContain("?");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(fontsOn(document.getPage(0))).as("no glyph borrowed from the fallback")
                    .allSatisfy(font -> assertThat(font.getName()).contains("AdventorTT"));
        }
    }

    @Test
    @DisplayName("🔤 A68: a letter the typeface lacks -- the Tshivenda ḓ -- prints from DejaVu Sans, never as '?'")
    void aLetterTheTypefaceLacksPrintsFromTheFallback() throws IOException {
        String name = "Mr N Exampleḓi";
        byte[] pdf = LetterLayout.render(letter(name, "M-099116"), TYPEFACE, LETTERHEAD);

        assertThat(textOf(pdf)).contains(name);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(fontsOn(document.getPage(0))).extracting(PDFont::getName)
                    .anySatisfy(font -> assertThat(font).contains("DejaVuSans"))
                    .anySatisfy(font -> assertThat(font).contains("AdventorTT"));
        }
    }

    @Test
    @DisplayName("🔒 a character no font prints refuses the letter, naming its code point and nothing of the member")
    void anUnprintableCharacterRefusesTheLetter() {
        assertThatThrownBy(() -> LetterLayout.render(letter("Ms Exemplé\u4E2D", "M-099116"), TYPEFACE, LETTERHEAD))
                .isInstanceOf(UnprintableCharacterException.class)
                .hasMessage("No letter font can print U+4E2D")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Exemplé").doesNotContain("M-099116"));
    }

    @Test
    @DisplayName("a letter longer than a page turns it, keeps every word, and numbers each page 'Page X of Y'")
    void aLongLetterTurnsThePage() throws IOException {
        StringBuilder text = new StringBuilder("# A long letter\n\n");
        for (int i = 1; i <= 90; i++) {
            text.append("Paragraph number ").append(i).append(" of an invented letter.\n\n");
        }
        byte[] pdf = LetterLayout.render(text.toString(), TYPEFACE, LETTERHEAD);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            assertThat(pages).isGreaterThan(1);
            assertThat(document.getPage(0).getResources().getXObjectNames()).as("the letterhead on page one").hasSize(1);
            assertThat(document.getPage(1).getResources().getXObjectNames()).as("and only there").isEmpty();
            assertThat(textOf(pdf)).contains("Paragraph number 1 of", "Paragraph number 90 of", "Page 1 of " + pages,
                    "Page " + pages + " of " + pages);
        }
    }

    @Test
    @DisplayName("a batch is one PDF, each letter from a new page with its own page numbers")
    void aBatchIsOnePdf() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        LetterLayout.renderAll(List.of(letter("Ms A Exemplé", "M-099001"), letter("Mr B Exemplé", "M-099002"),
                letter("Ms C Exemplé", "M-099003")), TYPEFACE, LETTERHEAD, out);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(3);
            for (int page = 0; page < 3; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page + 1);
                stripper.setEndPage(page + 1);
                assertThat(stripper.getText(document)).contains("M-09900" + (page + 1)).contains("Page 1 of 1");
            }
        }
        assertThatThrownBy(() -> LetterLayout.renderAll(List.of(), TYPEFACE, LETTERHEAD, new ByteArrayOutputStream()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("⏱ 500 letters render as one PDF in seconds: the fonts parsed once, the logo compressed once")
    void fiveHundredLettersInSeconds() throws IOException {
        List<String> warmUp = new ArrayList<>();
        List<String> batch = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            String text = letter("Ms Z Exemplé-" + i, String.format("M-%06d", 90000 + i));
            batch.add(text);
            if (i < 20) {
                warmUp.add(text);
            }
        }
        LetterLayout.renderAll(warmUp, TYPEFACE, LETTERHEAD, new ByteArrayOutputStream());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        long started = System.nanoTime();
        LetterLayout.renderAll(batch, TYPEFACE, LETTERHEAD, out);
        long millis = (System.nanoTime() - started) / 1_000_000;

        // About three seconds on a busy laptop. Thirty is the ceiling: ten times that, and well under the fifty a
        // font with a GSUB table took (LetterTypeface).
        assertThat(millis).as("milliseconds for 500 letters").isLessThan(30_000);
        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(500);
        }
    }

    @Test
    @DisplayName("a ZIP holds a PDF a letter, named as asked, and the same letters make the same archive")
    void aZipHoldsAPdfALetter() throws IOException {
        List<LetterLayout.Named> letters = List.of(
                new LetterLayout.Named("letter-0001.pdf", letter("Ms A Exemplé", "M-099001")),
                new LetterLayout.Named("letter-0002.pdf", letter("Mr B Exemplé", "M-099002")));
        ByteArrayOutputStream first = new ByteArrayOutputStream();
        LetterLayout.renderZip(letters, TYPEFACE, LETTERHEAD, first);
        ByteArrayOutputStream second = new ByteArrayOutputStream();
        LetterLayout.renderZip(letters, TYPEFACE, LETTERHEAD, second);

        assertThat(second.toByteArray()).isEqualTo(first.toByteArray());
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(first.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                names.add(entry.getName());
                byte[] pdf = zip.readAllBytes();
                assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
                assertThat(textOf(pdf)).contains("Page 1 of 1");
            }
        }
        assertThat(names).containsExactly("letter-0001.pdf", "letter-0002.pdf");
    }

    @Test
    @DisplayName("🔒 a ZIP entry's name cannot climb out of the archive or carry a folder")
    void aZipEntryNameIsChecked() {
        for (String name : List.of("../letter.pdf", "a/b.pdf", "C:\\letter.pdf", "", ".hidden")) {
            assertThatThrownBy(() -> new LetterLayout.Named(name, "Dear Member"))
                    .as(name).isInstanceOf(IllegalArgumentException.class);
        }
    }
}

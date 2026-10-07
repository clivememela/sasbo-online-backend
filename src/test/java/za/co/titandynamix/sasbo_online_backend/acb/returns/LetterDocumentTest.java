package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import za.co.titandynamix.sasbo_online_backend.documents.LetterTypeface;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The debit order letters as members receive them, and the specimens SASBO's letters review reads ("show it to us",
 * DOD-33). Read back the way a member reads them: the text of the finished PDF.
 */
@DisplayName("LetterDocument -- the debit order letters as documents")
class LetterDocumentTest {

    /** The deck's day: Wednesday 14 October 2026, 09:30 in Johannesburg. */
    private static final Clock DECK_DAY = Clock.fixed(Instant.parse("2026-10-14T07:30:00Z"), ZoneOffset.UTC);

    private final LetterDocument documents = new LetterDocument(LetterTypeface.bundled(), DECK_DAY);

    /** The specimen's text, line by line, whitespace inside a line collapsed and blank lines left out. */
    private static List<String> linesOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return Arrays.stream(new PDFTextStripper().getText(document).split("\\R"))
                    .map(line -> line.replaceAll("\\s+", " ").strip())
                    .filter(line -> !line.isEmpty())
                    .toList();
        }
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("each letter has a specimen: one A4 page on the letterhead, marked, invented, the accents whole, the account masked")
    void everyLetterHasASpecimen(LetterTemplate template) throws IOException {
        byte[] pdf = documents.specimen(template);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).as("a letter fits one page").isEqualTo(1);
            assertThat(document.getPage(0).getResources().getXObjectNames()).as("the letterhead's logo").hasSize(1);
        }
        String text = String.join("\n", linesOf(pdf));
        assertThat(text).contains("Specimen for the letters review, made from an invented member")
                .contains("Ms Z Exemplé-Côté")
                .contains("Dear Ms Exemplé-Côté")
                .contains("Membership number M-000000")
                .contains("14 October 2026")
                .contains("September 2026")
                .contains("members@sasbo.org.za")
                .contains("Membership Administration")
                .contains("Page 1 of 1")
                .doesNotContain("{{")
                .doesNotContain(LetterDocument.SPECIMEN_ACCOUNT);
    }

    @Test
    @DisplayName("the unpaid letters print the bank's reason in Stephne's words, as a phrase")
    void theReasonsInHerWords() throws IOException {
        assertThat(linesOf(documents.specimen(LetterTemplate.RE_DEBIT))).contains("Bank's reason Insufficient funds");
        assertThat(linesOf(documents.specimen(LetterTemplate.RETURN_DEBIT))).contains("Bank's reason Debit returned");
        assertThat(linesOf(documents.specimen(LetterTemplate.ACCOUNT_CLOSED))).contains("Bank's reason Account closed");
        assertThat(linesOf(documents.specimen(LetterTemplate.PAYMENT_STOPPED))).contains("Bank's reason Payment stopped");
        assertThat(linesOf(documents.specimen(LetterTemplate.NO_AUTHORITY)))
                .contains("Bank's reason No authority to debit account");
    }

    @Test
    @DisplayName("a letter's text is laid out on the letterhead, and nothing marks it a specimen")
    void aLetterIsNotASpecimen() throws IOException {
        String text = LetterRenderer.render(LetterTemplate.RE_DEBIT, LetterDocument.specimenData(LetterTemplate.RE_DEBIT,
                java.time.LocalDate.of(2026, 10, 14)));

        List<String> lines = linesOf(documents.render(text));
        assertThat(lines).contains("Sasbo Headquarters", "Your debit order for September 2026 was returned unpaid");
        assertThat(String.join("\n", lines)).doesNotContain("Specimen for the letters review");
    }
}

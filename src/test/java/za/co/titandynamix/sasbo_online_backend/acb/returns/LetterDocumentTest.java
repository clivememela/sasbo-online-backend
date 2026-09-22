package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A letter as the member receives it (ACB {@code 03.3}), and the specimens SASBO's letters review reads (Q32 c).
 */
@DisplayName("LetterDocument -- the union's letters as documents")
class LetterDocumentTest {

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("each letter has a specimen: on the letterhead, every placeholder filled, synthetic, and saying so")
    void everyLetterHasASpecimen(LetterTemplate template) throws IOException {
        byte[] pdf = LetterDocument.specimen(template, LocalDate.of(2026, 9, 22));

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("SPECIMEN").contains("Specimen").contains("M-000000");
            assertThat(text).as("no placeholder left unfilled").doesNotContain("{{");
            assertThat(text).as("R-SEC-C: the account masked to its last four digits").doesNotContain("99990000001");
            assertThat(document.getPage(0).getResources().getXObjectNames()).as("the letterhead").hasSize(1);
        }
    }

    @Test
    @DisplayName("a letter's text is laid out line for line, as the union typed it")
    void theTextIsTheLetter() throws IOException {
        byte[] pdf = LetterDocument.render("Dear Member\n\nUNION SUBSCRIPTIONS\n");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Dear Member").contains("UNION SUBSCRIPTIONS");
            assertThat(text).doesNotContain("SPECIMEN");
        }
    }
}

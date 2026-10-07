package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import za.co.titandynamix.sasbo_online_backend.acb.returns.LetterRenderer;
import za.co.titandynamix.sasbo_online_backend.documents.LetterLayout;
import za.co.titandynamix.sasbo_online_backend.documents.LetterTypeface;
import za.co.titandynamix.sasbo_online_backend.documents.Letterhead;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 🔒 Making and sending the letters logs counts, never a member: no name, street, account or address -- in this
 * application's loggers or PDFBox's.
 */
// @Isolated: it captures loggers, and Spring Boot resets Logback whenever a context starts in another class (see
// PiiInLogsTest and LogCapture). The values below are invented and occur nowhere else, so a sibling's lines cannot
// make the absence true or false.
@Isolated
@DisplayName("PII in logs -- the debit order letters")
class LettersLogNoPiiTest {

    private static final String SURNAME = "Quixotéqua";
    private static final String STREET = "77 Unlikely Invented Lane";
    private static final String ACCOUNT = "6200777711";
    private static final String EMAIL = "quixotequa.invented@example.org";

    @Test
    @DisplayName("rendering a batch and sending a letter log no member's name, street, account or address")
    void lettersLogNoMember() {
        LetterSenderFixture fixture = new LetterSenderFixture();
        fixture.mailServer();
        fixture.renderedLetter(SURNAME, STREET, ACCOUNT, EMAIL);
        String batchLetter = LetterRenderer.render(LetterTemplate.ACCOUNT_CLOSED, new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 14), "L", "Ms", "Z", SURNAME, List.of(STREET, "Testville"), "2021", "M-099116",
                ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), "Account Closed", 25, new BigDecimal("174.00"),
                LocalDate.of(2026, 9, 25)));

        try (LogCapture application = LogCapture.under("za.co.titandynamix.sasbo_online_backend");
             LogCapture pdfbox = LogCapture.under("org.apache.pdfbox");
             LogCapture fontbox = LogCapture.under("org.apache.fontbox")) {
            LetterLayout.renderAll(List.of(batchLetter, batchLetter), LetterTypeface.bundled(), Letterhead.sasbo(),
                    new ByteArrayOutputStream());
            assertThat(fixture.sender(true, AcbLetterSender.DEFAULT_FROM).sendDue()).isEqualTo(1);

            String logged = application.rendered() + pdfbox.rendered() + fontbox.rendered();
            assertThat(logged).as("the capture saw the sender's count line, so an absence below means something")
                    .contains("Debit order letters: 1 sent, 0 failed, of 1 due");
            assertThat(logged).doesNotContain(SURNAME).doesNotContain(STREET).doesNotContain(ACCOUNT)
                    .doesNotContain(EMAIL);
        }
    }
}

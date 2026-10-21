package za.co.titandynamix.sasbo_online_backend.documents;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** How a letter's text is read into the blocks its pages are laid out from. */
@DisplayName("LetterMarkup -- a letter's text read into blocks")
class LetterMarkupTest {

    @Test
    @DisplayName("every kind of line is read into its block, in order")
    void everyKindOfLine() {
        Letter letter = LetterMarkup.parse("""
                @specimen Specimen only
                @date 14 October 2026
                @to Ms Z Exemplé
                @to
                @to 12 Invented Street
                @ref Membership number | M-099116
                # Your debit order was returned
                Dear Ms Exemplé

                Your bank returned it.
                Twice, in fact.
                | Amount | R174.00
                | Debit date | 25 September 2026
                ## What you need to do
                - Please make sure there are funds.
                - If you meant to cancel, please tell us
                in writing.
                @sign Membership Administration
                @sign SASBO, The Finance Union
                """);

        assertThat(letter.subject()).isEqualTo("Your debit order was returned");
        assertThat(letter.blocks()).containsExactly(
                new Specimen("Specimen only"),
                new DateLine("14 October 2026"),
                new Recipient(java.util.List.of("Ms Z Exemplé", "12 Invented Street")),
                new References(java.util.List.of(new Row("Membership number", "M-099116"))),
                new Subject("Your debit order was returned"),
                new Paragraph("Dear Ms Exemplé"),
                new Paragraph("Your bank returned it. Twice, in fact."),
                new Table(java.util.List.of(new Row("Amount", "R174.00"), new Row("Debit date", "25 September 2026"))),
                new Heading("What you need to do"),
                new Points(java.util.List.of("Please make sure there are funds.",
                        "If you meant to cancel, please tell us in writing.")),
                new Signature(java.util.List.of("Membership Administration", "SASBO, The Finance Union")));
    }

    @Test
    @DisplayName("a table value keeps a bar inside it as text; a row without one is a label alone")
    void tableValues() {
        Letter letter = LetterMarkup.parse("| Bank account | Alpha | Beta ****9911\n| Note\n");

        assertThat(letter.blocks()).containsExactly(new Table(java.util.List.of(
                new Row("Bank account", "Alpha | Beta ****9911"), new Row("Note", ""))));
    }

    @Test
    @DisplayName("a tab or any other control character is a space, never layout")
    void controlCharactersAreSpaces() {
        Letter letter = LetterMarkup.parse("Dear\tMember\u0007\n");

        assertThat(letter.blocks()).containsExactly(new Paragraph("Dear Member"));
    }

    @Test
    @DisplayName("a line beginning with an unknown directive is the template's mistake, and says so")
    void anUnknownDirectiveIsRefused() {
        assertThatThrownBy(() -> LetterMarkup.parse("@signature The President\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("@signature");
    }

    @Test
    @DisplayName("a letter without a subject line has none; the first subject line is the letter's")
    void subjectOf() {
        assertThat(LetterMarkup.subjectOf("Dear Member\n")).isNull();
        assertThat(LetterMarkup.subjectOf("# First\n# Second\n")).isEqualTo("First");
    }
}

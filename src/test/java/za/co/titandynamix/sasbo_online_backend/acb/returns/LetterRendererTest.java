package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Block;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Row;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Signature;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Subject;
import za.co.titandynamix.sasbo_online_backend.documents.LetterMarkup.Table;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The debit order letters' words, filled from an invented member's unpaid.
 *
 * <p>Invented data only -- nothing of a real member is used here. The PDFs the words make are
 * {@code LetterDocumentTest}'s.
 */
@DisplayName("LetterRenderer -- the debit order letters' words")
class LetterRendererTest {

    private static final String SYNTHETIC_ACCOUNT = "6200999911";

    static LetterRenderer.LetterData data(String wording) {
        return new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 14), "L", "Ms", "Z", "Exemplé",
                List.of("12 Invented Street", "Testville"), "2021",
                "M-099116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), wording, 25,
                new BigDecimal("174.00"), LocalDate.of(2026, 9, 25));
    }

    private static String render(LetterTemplate template) {
        return LetterRenderer.render(template, data("Insufficient Funds"));
    }

    /** The letter's words as one line: a template wraps its paragraphs, a reader does not see where. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private static <T extends Block> List<T> blocks(String letter, Class<T> kind) {
        return LetterMarkup.parse(letter).blocks().stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("every letter is filled in its frame -- date, address, membership number, greeting -- with the account masked")
    void everyLetterIsFilledAndFramed(LetterTemplate template) {
        String letter = render(template);

        assertThat(letter).doesNotContain("{{").doesNotContain("}}").doesNotContain(SYNTHETIC_ACCOUNT);
        assertThat(letter).contains("@date 14 October 2026", "@to Ms Z Exemplé", "@to 12 Invented Street",
                "@ref Membership number | M-099116", "Dear Ms Exemplé");
        assertThat(blocks(letter, Subject.class)).as("one subject line").hasSize(1);
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("DOD-38, DOD-39: 'debit order' throughout, never 'ACB'")
    void debitOrderNeverAcb(LetterTemplate template) {
        String letter = render(template);

        assertThat(letter).doesNotContainIgnoringCase("ACB").doesNotContain("A.C.B");
        assertThat(letter.toLowerCase(java.util.Locale.ROOT)).contains("debit order");
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("🔒 DOD-35: signed by a position only -- Membership Administration -- and no person's name anywhere")
    void signedByPositionOnly(LetterTemplate template) {
        String letter = render(template);

        assertThat(blocks(letter, Signature.class)).singleElement().satisfies(signature ->
                assertThat(signature.lines()).containsExactly("Membership Administration", "SASBO, The Finance Union"));
        assertThat(letter).endsWith("Yours sincerely\n\n@sign Membership Administration\n@sign SASBO, The Finance Union\n");
        // The only person a letter names is the member it is addressed to.
        String unfilled = LetterRenderer.resource(LetterRenderer.FRAME) + LetterRenderer.resource(template.resource());
        assertThat(Pattern.compile("\\b(Mr|Mrs|Ms|Miss|Dr|Prof)\\.? [A-Z]").matcher(unfilled).find())
                .as("a title before a name in the %s letter's own words", template).isFalse();
    }

    @ParameterizedTest
    @EnumSource(LetterTemplate.class)
    @DisplayName("DOD-34: every letter sends the member's questions to members@sasbo.org.za, quoting the membership number")
    void theSenderAddress(LetterTemplate template) {
        assertThat(oneLine(render(template))).contains("please e-mail us at members@sasbo.org.za or call "
                + "(011) 467-0192, and quote your membership number, M-099116.");
    }

    @Test
    @DisplayName("DOD-36, DOD-15: the re-debit letter states the month-end date rule as SASBO does, and the member's own day")
    void theReDebitLetterStatesTheDateRule() {
        String letter = oneLine(render(LetterTemplate.RE_DEBIT));

        assertThat(letter).contains("When your debit day falls on a Saturday, Sunday or public holiday, your debit "
                + "order goes off on the next working day. At the end of the month it moves back instead, to the "
                + "last working day of the month, so it never goes into the next month.");
        assertThat(letter).contains("Under the debit order rules, we may present this debit order again next month, "
                + "on your usual debit day, the 25th, together with that month's subscription.");
    }

    @Test
    @DisplayName("the unpaid letters show the amount and the dates in the table: month, R174.00, the debit date, the reason, the last four")
    void theTable() {
        assertThat(blocks(render(LetterTemplate.RE_DEBIT), Table.class)).singleElement().satisfies(table ->
                assertThat(table.rows()).containsExactly(
                        new Row("Subscription for", "September 2026"),
                        new Row("Amount", "R174.00"),
                        new Row("Debit date", "25 September 2026"),
                        new Row("Bank's reason", "Insufficient funds"),
                        new Row("Bank account", "Alpha Bank ****9911")));
    }

    @Test
    @DisplayName("each family says its own thing: account closed, stopped, no authority, returned, ended")
    void eachFamilysOwnWords() {
        assertThat(oneLine(render(LetterTemplate.ACCOUNT_CLOSED)))
                .contains("because the account it is drawn on has been closed")
                .contains("If you have left your employer, please let us know.")
                .contains("the union does not represent members who are in arrears");
        assertThat(oneLine(render(LetterTemplate.PAYMENT_STOPPED)))
                .contains("because the payment was stopped on your account")
                .contains("we cannot present a stopped debit order again")
                .contains("please tell us in writing, as your signed membership application requires.");
        assertThat(oneLine(render(LetterTemplate.NO_AUTHORITY)))
                .contains("because it holds no authority for SASBO to debit your account")
                .contains("please contact us to authorise a new debit order.");
        assertThat(oneLine(render(LetterTemplate.RETURN_DEBIT)))
                .contains("The reason the bank gave is in the table below.")
                .contains("If your banking details have changed, please send us your new details.");
        assertThat(oneLine(render(LetterTemplate.MEMBERSHIP_ENDED)))
                .contains("returned unpaid three times in a row, most recently for September 2026")
                .contains("as a new member with a new join date")
                .doesNotContain("present this debit order again");
    }

    @Test
    @DisplayName("🔒 a line break in a member's details is a space: a surname can never begin a line, make a heading or sign")
    void aMembersDetailsCannotBecomeLayout() {
        LetterRenderer.LetterData hostile = new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 14), "L", "Ms", "Z", "Exemplé\n# Your membership has ended\n@sign The President",
                List.of("12 Invented Street\n| Amount | R0.00", "Testville\r\n- Pay nothing"), "2021",
                "M-099116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), "Insufficient Funds", 25,
                new BigDecimal("174.00"), LocalDate.of(2026, 9, 25));
        String letter = LetterRenderer.render(LetterTemplate.RE_DEBIT, hostile);

        assertThat(blocks(letter, Subject.class)).extracting(Subject::text)
                .containsExactly("Your debit order for September 2026 was returned unpaid");
        assertThat(blocks(letter, Signature.class)).singleElement().satisfies(signature ->
                assertThat(signature.lines()).containsExactly("Membership Administration", "SASBO, The Finance Union"));
        assertThat(blocks(letter, Table.class)).singleElement().satisfies(table ->
                assertThat(table.rows()).extracting(Row::label).containsExactly(
                        "Subscription for", "Amount", "Debit date", "Bank's reason", "Bank account"));
        // ...and the words are kept, as text, where they belong.
        assertThat(letter).contains("@to Ms Z Exemplé # Your membership has ended @sign The President")
                .contains("@to 12 Invented Street | Amount | R0.00");
    }

    @Test
    @DisplayName("🔒 R3-16, SD3-C: a surname and an address holding markup are text, character for character")
    void markupIsText() {
        LetterRenderer.LetterData marked = new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 14), "L", "Ms", "Z", "<b>Exemplé</b>",
                List.of("<img src=x onerror=alert(1)>", "Testville"), "2021",
                "M-099116", SYNTHETIC_ACCOUNT, "Alpha Bank", YearMonth.of(2026, 9), "Account Closed", 25,
                new BigDecimal("174.00"), LocalDate.of(2026, 9, 25));
        assertThat(LetterRenderer.render(LetterTemplate.ACCOUNT_CLOSED, marked))
                .contains("<b>Exemplé</b>").contains("<img src=x onerror=alert(1)>");
    }

    @Test
    @DisplayName("a member with no title is greeted 'Dear Member'; an empty address line, amount or date leaves no gap or error")
    void missingParts() {
        LetterRenderer.LetterData sparse = new LetterRenderer.LetterData(
                LocalDate.of(2026, 10, 14), null, null, "Z", "Exemplé", List.of(), null,
                "M-099116", null, null, YearMonth.of(2026, 9), null, null, null, null);
        String letter = LetterRenderer.render(LetterTemplate.RE_DEBIT, sparse);

        assertThat(letter).contains("Dear Member", "@to Z Exemplé", "on your usual debit day, together");
        assertThat(LetterMarkup.parse(letter).blocks()).filteredOn(LetterMarkup.Recipient.class::isInstance)
                .singleElement().satisfies(r -> assertThat(((LetterMarkup.Recipient) r).lines()).containsExactly("Z Exemplé"));
    }

    @Test
    @DisplayName("the letter reads its amount and debit date from the unpaid line, and its month from the run")
    void dataForReadsTheLine() {
        AcbRun run = new AcbRun();
        run.setActionMonth(LocalDate.of(2026, 9, 1));
        AcbRunLine line = new AcbRunLine();
        line.setInitials("Z");
        line.setSurname("Exemplé");
        line.setMembershipNumber("M-099116");
        line.setAccountNumber(SYNTHETIC_ACCOUNT);
        line.setBankName("Alpha Bank");
        line.setDebitDay(27);
        line.setAmount(new BigDecimal("174.00"));
        line.setActionDate(LocalDate.of(2026, 9, 28));
        LetterRecipient recipient = new LetterRecipient(7, "Ms", "12 Invented Street, Testville", null, null, null, "L");

        assertThat(LetterRenderer.dataFor(LocalDate.of(2026, 10, 14), run, line, recipient, "Insufficient Funds").values())
                .containsEntry("amount", "R174.00")
                .containsEntry("debitDate", "28 September 2026")
                .containsEntry("debitMonthYear", "September 2026")
                .containsEntry("debitDayPhrase", "your usual debit day, the 27th")
                .containsEntry("accountNumber", "****9911")
                .containsEntry("memberName", "Ms Z Exemplé")
                .containsEntry("addressLine2", "Testville");
    }

    @Test
    @DisplayName("a day is an ordinal; an amount is rands and cents; the bank's wording reads as a phrase")
    void formats() {
        assertThat(LetterRenderer.ordinal(21)).isEqualTo("21st");
        assertThat(LetterRenderer.ordinal(22)).isEqualTo("22nd");
        assertThat(LetterRenderer.ordinal(23)).isEqualTo("23rd");
        assertThat(LetterRenderer.ordinal(25)).isEqualTo("25th");
        assertThat(LetterRenderer.ordinal(31)).isEqualTo("31st");
        assertThat(LetterRenderer.rands(new BigDecimal("174"))).isEqualTo("R174.00");
        assertThat(LetterRenderer.rands(new BigDecimal("1234.5"))).isEqualTo("R1,234.50");
        assertThat(LetterRenderer.rands(null)).isEmpty();
        assertThat(LetterRenderer.sentenceCase("Insufficient Funds")).isEqualTo("Insufficient funds");
        assertThat(LetterRenderer.sentenceCase("Not Provided For")).isEqualTo("Not provided for");
        assertThat(LetterRenderer.sentenceCase("No authority to debit account")).isEqualTo("No authority to debit account");
        assertThat(LetterRenderer.sentenceCase("payment stopped")).isEqualTo("Payment stopped");
        assertThat(LetterRenderer.sentenceCase("Debit returned by SBSA")).isEqualTo("Debit returned by SBSA");
    }

    @Test
    @DisplayName("masking keeps the last four digits and never the length")
    void masking() {
        assertThat(LetterRenderer.masked("1234567890")).isEqualTo("****7890");
        assertThat(LetterRenderer.masked("123")).isEqualTo("****");
        assertThat(LetterRenderer.masked(null)).isEqualTo("****");
        assertThat(data("Insufficient Funds").toString()).doesNotContain(SYNTHETIC_ACCOUNT);
    }

    @Test
    @DisplayName("each reason names the letter it sends -- the letters review's table (October build); a deceased "
            + "account holder and an unknown code get none")
    void reasonsToLetters() {
        assertThat(UnpaidReason.NOT_PROVIDED_FOR.letter()).isEqualTo(LetterTemplate.RE_DEBIT);
        assertThat(UnpaidReason.PAYMENT_STOPPED.letter()).isEqualTo(LetterTemplate.PAYMENT_STOPPED);
        assertThat(UnpaidReason.ACCOUNT_FROZEN.letter()).isEqualTo(LetterTemplate.RETURN_DEBIT);
        assertThat(UnpaidReason.INVALID_ACCOUNT_NUMBER.letter()).isEqualTo(LetterTemplate.RETURN_DEBIT);
        assertThat(UnpaidReason.ACCOUNT_CLOSED.letter()).isEqualTo(LetterTemplate.ACCOUNT_CLOSED);
        assertThat(UnpaidReason.NO_AUTHORITY_TO_DEBIT.letter()).isEqualTo(LetterTemplate.NO_AUTHORITY);
        assertThat(UnpaidReason.AUTHORISATION_CANCELLED.letter()).isEqualTo(LetterTemplate.NO_AUTHORITY);
        assertThat(UnpaidReason.ACCOUNT_HOLDER_DECEASED.letter()).isNull();
        assertThat(UnpaidReason.OTHER.letter()).isNull();
    }
}

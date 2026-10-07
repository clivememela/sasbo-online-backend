package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.documents.LetterLayout;
import za.co.titandynamix.sasbo_online_backend.documents.LetterTypeface;
import za.co.titandynamix.sasbo_online_backend.documents.Letterhead;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A debit order letter as the member receives it: its text -- the union's words, filled by {@link LetterRenderer} --
 * laid out on SASBO's letterhead as a PDF, in the configured typeface ({@code LetterTypeface}).
 *
 * <h2>The specimens are what the letters review reads</h2>
 * {@link #specimen} fills a letter from a member who does not exist -- an invented name with accented letters, an
 * invented street and bank, an account of nines -- and a band across its top says it is a specimen. SASBO's review
 * reads them ("show it to us", DOD-33), and so do the screens and the deck. Nothing in one belongs to a member.
 *
 * <h2>Many letters at once</h2>
 * {@link #renderAll} streams letters as one PDF, each from a new page; {@link #renderZip} streams them as a ZIP of a
 * PDF each. Either parses the fonts once for the whole batch.
 */
@Component
public class LetterDocument {

    /** The band across the top of a specimen. */
    static final String SPECIMEN_LINE = "@specimen Specimen for the letters review, made from an invented member: "
            + "not a letter to anybody";

    /** The invented member every specimen is addressed to: a name a font must print with its accents. */
    static final String SPECIMEN_TITLE = "Ms";
    static final String SPECIMEN_INITIALS = "Z";
    static final String SPECIMEN_SURNAME = "Exemplé-Côté";
    static final String SPECIMEN_NUMBER = "M-000000";
    static final String SPECIMEN_ACCOUNT = "99990000001";

    private final LetterTypeface typeface;
    private final Letterhead letterhead;
    private final Clock clock;

    @Autowired
    public LetterDocument(LetterTypeface typeface, Clock clock) {
        this(typeface, Letterhead.sasbo(), clock);
    }

    LetterDocument(LetterTypeface typeface, Letterhead letterhead, Clock clock) {
        this.typeface = typeface;
        this.letterhead = letterhead;
        this.clock = clock;
    }

    /** The letter, laid out on the letterhead. */
    public byte[] render(String letterText) {
        return LetterLayout.render(letterText, typeface, letterhead);
    }

    /** Letters as one PDF, each from a new page, written to {@code out}. */
    public void renderAll(List<String> letterTexts, OutputStream out) {
        LetterLayout.renderAll(letterTexts, typeface, letterhead, out);
    }

    /** Letters as a ZIP of PDFs, one each, written to {@code out}. */
    public void renderZip(List<LetterLayout.Named> letters, OutputStream out) {
        LetterLayout.renderZip(letters, typeface, letterhead, out);
    }

    /** A specimen of {@code template}, dated today -- invented from end to end. */
    public byte[] specimen(LetterTemplate template) {
        return specimen(template, AcbTime.today(clock));
    }

    byte[] specimen(LetterTemplate template, LocalDate today) {
        return render(specimenText(template, today));
    }

    /** The specimen's text: the band, then the letter filled from the invented member. */
    static String specimenText(LetterTemplate template, LocalDate today) {
        return SPECIMEN_LINE + "\n" + LetterRenderer.render(template, specimenData(template, today));
    }

    /** The invented member's unpaid of last month: their debit day the 25th, at the rate SASBO charges in 2026. */
    static LetterRenderer.LetterData specimenData(LetterTemplate template, LocalDate today) {
        YearMonth month = YearMonth.from(today).minusMonths(1);
        return new LetterRenderer.LetterData(today, "Specimen Area", SPECIMEN_TITLE, SPECIMEN_INITIALS,
                SPECIMEN_SURNAME, List.of("1 Invented Street", "Specimen Park", "Johannesburg"), "2001",
                SPECIMEN_NUMBER, SPECIMEN_ACCOUNT, "Example Bank", month, wordingOf(template), 25,
                new BigDecimal("174.00"), month.atDay(25));
    }

    /**
     * The bank's reason a specimen of {@code template} prints, in the words of Stephne's "Suspension codes" sheet
     * (DOD-01) for the bank code that sends it.
     */
    static String wordingOf(LetterTemplate template) {
        return switch (template) {
            case RE_DEBIT, MEMBERSHIP_ENDED -> "Insufficient Funds";
            case RETURN_DEBIT -> "Debit returned";
            case ACCOUNT_CLOSED -> "Account Closed";
            case PAYMENT_STOPPED -> "Payment stopped";
            case NO_AUTHORITY -> "No authority to debit account";
        };
    }
}

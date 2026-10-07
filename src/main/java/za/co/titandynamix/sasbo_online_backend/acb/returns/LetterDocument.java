package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.documents.LetterPdf;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * A letter as the member receives it (ACB {@code 03.3}): its text -- the union's words, filled by {@link
 * LetterRenderer} -- set as the union typed it, on the union's letterhead, as a PDF.
 *
 * <h2>🟡 The letterhead is provisional</h2>
 * SASBO asked to "look at the letters and decide as it need to be printed on a letterhead" (question 32 c). Until
 * that review the letterhead is the union's own mark, embedded -- never fetched (SD3-C) -- and the review decides
 * what else a letterhead carries. Nothing is sent to a member before it: the sender is switched off until then.
 *
 * <h2>The specimens are what the review reads</h2>
 * {@link #specimen} fills a letter from a member who does not exist -- a specimen street, a specimen bank, an
 * account of nines -- and says on its first line that it is not a letter to anybody. It is served openly, like the
 * holiday calendar: nothing in it belongs to a member.
 */
public final class LetterDocument {

    static final String LETTERHEAD = "acb/letters/letterhead.png";

    static final String SPECIMEN_LINE = "SPECIMEN -- for the letters review; not a letter to a member";

    private static byte[] letterhead;

    private LetterDocument() {
    }

    /** The letter, laid out on the letterhead. */
    public static byte[] render(String letterText) {
        return LetterPdf.render(List.of(letterText.split("\n", -1)), LetterPdf.Style.TYPED, letterhead());
    }

    /** A specimen of {@code template} dated {@code today}, for the letters review -- synthetic from end to end. */
    public static byte[] specimen(LetterTemplate template, LocalDate today) {
        LetterRenderer.LetterData data = new LetterRenderer.LetterData(today, "Specimen Area", "Mr", "A",
                "Specimen", List.of("1 Specimen Street", "Specimen Suburb", "Johannesburg"), "2001", "M-000000",
                "99990000001", "Specimen Bank", YearMonth.from(today).minusMonths(1), wordingOf(template), 25);
        return render(SPECIMEN_LINE + "\n\n" + LetterRenderer.render(template, data));
    }

    /** The reason a specimen of {@code template} names: the one its letter is sent for. */
    static String wordingOf(LetterTemplate template) {
        return switch (template) {
            case RE_DEBIT -> UnpaidReason.NOT_PROVIDED_FOR.wording();
            case RETURN_DEBIT -> UnpaidReason.ACCOUNT_FROZEN.wording();
            case ACCOUNT_CLOSED -> UnpaidReason.ACCOUNT_CLOSED.wording();
            case PAYMENT_STOPPED -> UnpaidReason.PAYMENT_STOPPED.wording();
            case NO_AUTHORITY -> UnpaidReason.NO_AUTHORITY_TO_DEBIT.wording();
            // The third unpaid is most often a debit not provided for.
            case MEMBERSHIP_ENDED -> UnpaidReason.NOT_PROVIDED_FOR.wording();
        };
    }

    /** The letterhead's bytes, read once. */
    static synchronized byte[] letterhead() {
        if (letterhead == null) {
            try (InputStream stream = LetterDocument.class.getClassLoader().getResourceAsStream(LETTERHEAD)) {
                if (stream == null) {
                    throw new IllegalStateException("The letterhead is missing: " + LETTERHEAD);
                }
                letterhead = stream.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return letterhead;
    }
}

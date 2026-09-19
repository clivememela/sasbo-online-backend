package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills one of the union's three letters from an unpaid debit.
 *
 * <h2>The words are the union's, and they are resources</h2>
 * The templates live under {@code acb/letters/} exactly as SASBO printed them, with four typing
 * slips corrected (the owner's decision: a spelling slip is not wording) and every WORD otherwise
 * untouched -- including the re-debit letter's sentence about "the 20 of each month", which is a
 * statement of a rule SASBO has been asked about and stays as written until they answer.
 *
 * <h2>It refuses to guess</h2>
 * Every {@code {{placeholder}}} in a template must have a value. A template asking for one nothing
 * supplies is an error, not a blank in a letter to a member.
 *
 * <h2>🔒 The account number is masked</h2>
 * To its last four digits, unless SASBO says the letter must carry it in full (question 28): the
 * letter now travels by email, and an email is forwarded, printed and kept.
 */
public final class LetterRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9]+)}}");
    private static final DateTimeFormatter LETTER_DATE = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final Map<LetterTemplate, String> TEMPLATES = new EnumMap<>(LetterTemplate.class);

    private LetterRenderer() {
    }

    /** What a letter is filled from. 🔒 Carries the account in full; {@link #toString()} does not. */
    public record LetterData(
            LocalDate letterDate,
            String area,
            String memberTitle,
            String memberInitials,
            String memberSurname,
            List<String> addressLines,
            String postalCode,
            String membershipNumber,
            String accountNumber,
            String bankName,
            YearMonth debitMonth,
            String unpaidReason) {

        /** Every placeholder's value. The account is masked here, so nothing downstream sees it whole. */
        public Map<String, String> values() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("letterDate", letterDate.format(LETTER_DATE));
            values.put("area", blank(area));
            values.put("memberTitle", blank(memberTitle));
            values.put("memberInitials", blank(memberInitials));
            values.put("memberSurname", blank(memberSurname));
            for (int i = 0; i < 3; i++) {
                values.put("addressLine" + (i + 1), i < addressLines.size() ? blank(addressLines.get(i)) : "");
            }
            values.put("postalCode", blank(postalCode));
            values.put("salutationName", salutation(memberTitle, memberSurname));
            values.put("membershipNumber", blank(membershipNumber));
            values.put("membershipNumberPlain", blank(membershipNumber).replaceFirst("/[0-9]+$", ""));
            values.put("accountNumber", masked(accountNumber));
            values.put("bankName", blank(bankName));
            // The legacy letters print the word UNIVERSAL: the line debits the bank's universal branch.
            values.put("branchLabel", "UNIVERSAL");
            String month = debitMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            values.put("debitMonth", month);
            values.put("debitMonthUpper", month.toUpperCase(Locale.ROOT));
            values.put("unpaidReason", blank(unpaidReason));
            return values;
        }

        @Override
        public String toString() {
            return "LetterData[membershipNumber=" + membershipNumber + ", month=" + debitMonth + "]";
        }
    }

    /** The letter's text, every placeholder filled. */
    public static String render(LetterTemplate template, LetterData data) {
        Map<String, String> values = data.values();
        Matcher placeholder = PLACEHOLDER.matcher(template(template));
        StringBuilder letter = new StringBuilder();
        while (placeholder.find()) {
            String name = placeholder.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new IllegalStateException("The " + template + " letter asks for {{" + name
                        + "}}, which nothing supplies");
            }
            placeholder.appendReplacement(letter, Matcher.quoteReplacement(value));
        }
        placeholder.appendTail(letter);
        return letter.toString();
    }

    /** The template's text, read once from its resource. */
    static synchronized String template(LetterTemplate template) {
        return TEMPLATES.computeIfAbsent(template, t -> {
            try (InputStream stream = LetterRenderer.class.getClassLoader().getResourceAsStream(t.resource())) {
                if (stream == null) {
                    throw new IllegalStateException("The " + t + " letter's resource is missing: " + t.resource());
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** The last four digits behind four asterisks -- never the whole number, never its length. */
    static String masked(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * How a letter addressed by name greets the member: title and surname. The exact form the old
     * letters used was not transcribed (nothing personal was copied from the scans), so this is the
     * conventional one, and "Member" when there is no title to use.
     */
    static String salutation(String title, String surname) {
        if (title == null || title.isBlank() || surname == null || surname.isBlank()) {
            return "Member";
        }
        return title.trim() + " " + surname.trim();
    }

    private static String blank(String value) {
        return value == null ? "" : value.trim();
    }
}

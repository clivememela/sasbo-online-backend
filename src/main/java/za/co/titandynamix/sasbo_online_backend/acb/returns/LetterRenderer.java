package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills one of the union's debit order letters from an unpaid debit.
 *
 * <h2>The words are resources, in plain British English</h2>
 * Each letter is a body under {@code acb/letters/} set in one frame ({@value #FRAME}): the date, the member's address,
 * the membership number, then the body, then how to reach the union and the signing position. The bodies keep the
 * purpose of the letters SASBO printed and say it in the modern layout Stephne asked to see (DOD-33): "debit order",
 * never "ACB", and "the debit order rules" (DOD-38, DOD-39); the date rule in the re-debit letter (DOD-36); signed by
 * a position only, "Membership Administration" (DOD-35); replies to members@sasbo.org.za (DOD-34). How the text is
 * laid out is {@code LetterMarkup}.
 *
 * <h2>It refuses to guess</h2>
 * Every {@code {{placeholder}}} must have a value: a template asking for one nothing supplies is an error, not a gap
 * in a letter to a member.
 *
 * <h2>🔒 A member's details are text, never layout, and never the whole account</h2>
 * The frame and the body are joined BEFORE anything is filled, and filled in one pass, so no value is ever read as a
 * placeholder. No template line may begin with a placeholder, and every control character inside a value becomes a
 * space -- so a surname or an address can never begin a line, which is the only place a line says what it is. The
 * account number is masked to its last four digits ("showing the last four digits of the account", SASBO's answers
 * of 18 and 21 September).
 */
public final class LetterRenderer {

    static final String FRAME = "acb/letters/frame.txt";

    private static final String BODY = "{{body}}";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9]+)}}");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK);
    private static final Map<String, String> RESOURCES = new ConcurrentHashMap<>();

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
            String unpaidReason,
            Integer debitDay,
            BigDecimal amount,
            LocalDate debitDate) {

        /** Every placeholder's value, cleaned. The account is masked here, so nothing downstream sees it whole. */
        public Map<String, String> values() {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("letterDate", letterDate.format(LONG_DATE));
            values.put("memberName", joined(memberTitle, memberInitials, memberSurname));
            for (int i = 0; i < 3; i++) {
                values.put("addressLine" + (i + 1), i < addressLines.size() ? clean(addressLines.get(i)) : "");
            }
            values.put("postalCode", clean(postalCode));
            values.put("membershipNumber", clean(membershipNumber));
            values.put("salutationName", salutation(memberTitle, memberSurname));
            values.put("area", clean(area));
            values.put("debitMonthYear", debitMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.UK) + " "
                    + debitMonth.getYear());
            values.put("amount", rands(amount));
            values.put("debitDate", debitDate == null ? "" : debitDate.format(LONG_DATE));
            values.put("unpaidReason", sentenceCase(clean(unpaidReason)));
            values.put("bankName", clean(bankName));
            values.put("accountNumber", masked(accountNumber));
            // Q32: the member's OWN debit day -- "if a member pays by the 25th or 27, or 30 the letter will indicate that".
            values.put("debitDayPhrase", debitDay == null ? "your usual debit day"
                    : "your usual debit day, the " + ordinal(debitDay));
            return values;
        }

        @Override
        public String toString() {
            return "LetterData[membershipNumber=" + membershipNumber + ", month=" + debitMonth + "]";
        }
    }

    /**
     * The data a letter is filled from, on {@code date}: the run's month, the unpaid line's snapshot -- its amount and
     * the date it was debited -- and the member's address as the record holds it. The recording renders with today;
     * the sender renders again with the day it was first rendered, so the two can be compared by their hash (03.3).
     */
    public static LetterData dataFor(LocalDate date,
                                     za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun run,
                                     za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunLine line,
                                     LetterRecipient recipient, String wording) {
        return new LetterData(date, recipient.areaName(), recipient.title(), line.getInitials(), line.getSurname(),
                recipient.addressLines(), null, line.getMembershipNumber(), line.getAccountNumber(),
                line.getBankName(), YearMonth.from(run.getActionMonth()), wording, line.getDebitDay(),
                line.getAmount(), line.getActionDate());
    }

    /**
     * The letter's fingerprint, kept by the letters table instead of its body (R-SEC-C): the SHA-256 of its UTF-8
     * text, in lower-case hex. The recording writes it, and the sender compares it before sending (03.3), so the two
     * share this one definition.
     */
    public static String bodySha256(String body) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", impossible);
        }
    }

    /** The letter's text: the frame around the template's body, every placeholder filled. */
    public static String render(LetterTemplate template, LetterData data) {
        String letter = resource(FRAME).replace(BODY, resource(template.resource()).strip());
        for (String line : letter.split("\n", -1)) {
            if (line.startsWith("{{")) {
                // A value at the start of a line could make itself a heading or a table row.
                throw new IllegalStateException("The " + template + " letter starts a line with a placeholder: "
                        + line);
            }
        }
        Map<String, String> values = data.values();
        Matcher placeholder = PLACEHOLDER.matcher(letter);
        StringBuilder filled = new StringBuilder();
        while (placeholder.find()) {
            String name = placeholder.group(1);
            String value = values.get(name);
            if (value == null) {
                throw new IllegalStateException("The " + template + " letter asks for {{" + name
                        + "}}, which nothing supplies");
            }
            placeholder.appendReplacement(filled, Matcher.quoteReplacement(value));
        }
        placeholder.appendTail(filled);
        return filled.toString();
    }

    /** A letter resource's text, read once. */
    static String resource(String path) {
        return RESOURCES.computeIfAbsent(path, p -> {
            try (InputStream stream = LetterRenderer.class.getClassLoader().getResourceAsStream(p)) {
                if (stream == null) {
                    throw new IllegalStateException("A letter's resource is missing: " + p);
                }
                return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** {@code 25} -> {@code 25th}; {@code 21} -> {@code 21st}; {@code 22} -> {@code 22nd}. */
    static String ordinal(int day) {
        String suffix = day % 100 >= 11 && day % 100 <= 13 ? "th" : switch (day % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return day + suffix;
    }

    /** The last four digits behind four asterisks -- never the whole number, never its length. */
    static String masked(String accountNumber) {
        String digits = clean(accountNumber);
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }

    /** {@code 174} -> {@code R174.00}, as SASBO writes its rate ("R166.65 to R174.00"); none when there is none. */
    static String rands(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.UK));
        return "R" + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * How a letter greets the member: title and surname, and "Member" when there is no title to use -- the old
     * letters' own greeting.
     */
    static String salutation(String title, String surname) {
        String t = clean(title);
        String s = clean(surname);
        return t.isEmpty() || s.isEmpty() ? "Member" : t + " " + s;
    }

    /**
     * The bank's wording as a phrase in a sentence: "Insufficient Funds" -> "Insufficient funds". A word in capitals
     * throughout is left as it is.
     */
    static String sentenceCase(String wording) {
        if (wording.isEmpty()) {
            return wording;
        }
        String[] words = wording.split(" ");
        StringBuilder out = new StringBuilder(wording.length());
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (i > 0) {
                out.append(' ');
                boolean capitalised = word.length() > 1 && Character.isUpperCase(word.charAt(0))
                        && word.substring(1).equals(word.substring(1).toLowerCase(Locale.ROOT));
                if (capitalised) {
                    word = word.toLowerCase(Locale.ROOT);
                }
            }
            out.append(word);
        }
        return Character.toUpperCase(out.charAt(0)) + out.substring(1);
    }

    /**
     * {@code value} trimmed, every control character -- and the Unicode line and paragraph separators, U+2028 and
     * U+2029 -- a space: a line break in a surname is a space in the letter.
     */
    static String clean(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        value.codePoints().forEach(cp -> out.appendCodePoint(
                Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029 ? ' ' : cp));
        return out.toString().trim();
    }

    private static String joined(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String cleaned = clean(part);
            if (!cleaned.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(cleaned);
            }
        }
        return out.toString();
    }
}

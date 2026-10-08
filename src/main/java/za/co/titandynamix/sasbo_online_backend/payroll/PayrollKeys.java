package za.co.titandynamix.sasbo_online_backend.payroll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a staff number, an ID number, an amount and a month are compared -- the same on a file's line and on a member's
 * record, so the two can only agree or disagree about the person, never about how a number was written (fixtures'
 * {@code LAYOUTS.md}, "Rules every parser needs": numbers held as text with leading zeros, an apostrophe or {@code ID}
 * before an ID number, deductions stored negative, an amount as text with spaces).
 */
public final class PayrollKeys {

    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern THIRTEEN_DIGITS = Pattern.compile("\\d{13}");
    /**
     * Up to twenty places: a formula's cached result carries a binary fraction's tail ({@code 174.00000000000003} is
     * fourteen), and a double prints up to seventeen significant digits. Rounded to cents after.
     */
    private static final Pattern AMOUNT = Pattern.compile("-?\\d{1,9}(\\.\\d{1,20})?");
    private static final Pattern YEAR_MONTH_DIGITS = Pattern.compile("(\\d{4})(\\d{2})");

    private PayrollKeys() {
    }

    /**
     * A staff number as it is compared: letters and digits only, upper case, and -- where only digits are left --
     * without leading zeros ({@code 0900006} and {@code 900006} are one number). {@code null} when nothing is left.
     */
    public static String staffKey(String staffNumber) {
        if (staffNumber == null) {
            return null;
        }
        String key = NOT_ALPHANUMERIC.matcher(staffNumber.toUpperCase(Locale.ROOT)).replaceAll("");
        if (key.isEmpty()) {
            return null;
        }
        if (DIGITS.matcher(key).matches()) {
            String stripped = key.replaceFirst("^0+", "");
            return stripped.isEmpty() ? "0" : stripped;
        }
        return key.length() > 40 ? key.substring(0, 40) : key;
    }

    /**
     * An ID number as it is compared: FNB's leading apostrophe and Access's {@code ID} dropped, spaces dropped. A South
     * African ID number is its 13 digits; anything else (a passport) is its letters and digits, upper case.
     */
    public static String idKey(String idNumber) {
        if (idNumber == null) {
            return null;
        }
        String key = NOT_ALPHANUMERIC.matcher(idNumber.toUpperCase(Locale.ROOT)).replaceAll("");
        if (key.startsWith("ID") && key.length() > 2 && DIGITS.matcher(key.substring(2)).matches()) {
            key = key.substring(2);
        }
        if (key.isEmpty()) {
            return null;
        }
        return key.length() > 20 ? key.substring(0, 20) : key;
    }

    /** Whether an ID key is a South African ID number's thirteen digits. */
    public static boolean isSaId(String idKey) {
        return idKey != null && THIRTEEN_DIGITS.matcher(idKey).matches();
    }

    /** An ID number as a screen shows it: its last four digits, the rest hidden. */
    public static String masked(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            return null;
        }
        String trimmed = idNumber.trim();
        return trimmed.length() <= 4 ? "****" : "****" + trimmed.substring(trimmed.length() - 4);
    }

    /**
     * An amount as written on a line -- {@code -174}, {@code "  174.00"}, {@code R1 218,00}, {@code (174.00)},
     * {@code 174.00-} -- as rands and cents, its sign dropped (deductions are stored negative by five of the employers).
     * {@code null} when it cannot be read as an amount.
     */
    public static BigDecimal amount(String written) {
        BigDecimal signed = signedAmount(written);
        return signed == null ? null : signed.abs();
    }

    /** As {@link #amount}, keeping the sign as written -- for a debit and a credit that are summed. */
    public static BigDecimal signedAmount(String written) {
        if (written == null) {
            return null;
        }
        String text = written.trim().replace(" ", "").replace(" ", "");
        boolean negative = false;
        if (text.startsWith("(") && text.endsWith(")")) {
            negative = true;
            text = text.substring(1, text.length() - 1);
        }
        if (text.endsWith("-")) {
            negative = true;
            text = text.substring(0, text.length() - 1);
        }
        text = text.replaceFirst("^[Rr]", "");
        if (text.startsWith("-")) {
            negative = !negative;
            text = text.substring(1);
        }
        text = text.replaceFirst("^[Rr]", "");
        if (text.contains(",") && text.contains(".")) {
            text = text.replace(",", "");
        } else if (text.contains(",")) {
            // A comma alone is the decimal mark where two digits follow it (South African habit), else a thousands mark.
            int comma = text.lastIndexOf(',');
            text = text.length() - comma - 1 == 2 && text.indexOf(',') == comma
                    ? text.replace(',', '.') : text.replace(",", "");
        }
        if (!AMOUNT.matcher(text).matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
        return negative ? value.negate() : value;
    }

    /** An amount as a screen and a report write it: two places, a point. */
    public static String money(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * The month a line deducts for, from {@code 202608} (Absa's number, Nedbank's text), {@code 2026-08} or an ISO day;
     * {@code null} when it cannot be read.
     */
    public static YearMonth forPeriod(String written) {
        if (written == null) {
            return null;
        }
        String text = written.trim();
        Matcher digits = YEAR_MONTH_DIGITS.matcher(text);
        try {
            if (digits.matches()) {
                return YearMonth.of(Integer.parseInt(digits.group(1)), Integer.parseInt(digits.group(2)));
            }
            if (text.length() == 7) {
                return YearMonth.parse(text);
            }
            if (text.length() == 10) {
                return YearMonth.from(LocalDate.parse(text));
            }
        } catch (java.time.DateTimeException unreadable) {
            return null;
        }
        return null;
    }
}

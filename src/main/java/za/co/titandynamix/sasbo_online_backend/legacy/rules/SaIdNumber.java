package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * A South African identity number, checked the whole way: {@code YYMMDD SSSS C A Z} -- thirteen digits, a real date of
 * birth, and the check digit, computed exactly as the capture screen's {@code validateSAID} computes it, so a number
 * the screen refuses is refused here too (the server's own {@code SaIdParser} checks the length alone, and is left as
 * it is: it serves capture, and is known for it).
 *
 * <p>The century is decided by the day the check runs, handed in -- never the wall clock: {@code 94} is 1994 because
 * 2094 has not happened yet.
 */
public final class SaIdNumber {

    /** The first thing wrong with a number, in the order a person would fix it. */
    public enum Flaw {
        FORMAT,
        DATE,
        CHECK_DIGIT
    }

    private SaIdNumber() {
    }

    /** What is wrong with {@code digits}, judged on {@code today}; empty when it is a valid number. */
    public static Optional<Flaw> flaw(String digits, LocalDate today) {
        if (digits == null || !digits.matches("[0-9]{13}")) {
            return Optional.of(Flaw.FORMAT);
        }
        if (dateOfBirth(digits, today).isEmpty()) {
            return Optional.of(Flaw.DATE);
        }
        if (!checkDigitIsValid(digits)) {
            return Optional.of(Flaw.CHECK_DIGIT);
        }
        return Optional.empty();
    }

    /** The date of birth a well-formed number carries, in the latest century that puts it on or before {@code today}. */
    public static Optional<LocalDate> dateOfBirth(String digits, LocalDate today) {
        if (digits == null || !digits.matches("[0-9]{13}")) {
            return Optional.empty();
        }
        int yy = Integer.parseInt(digits.substring(0, 2));
        int month = Integer.parseInt(digits.substring(2, 4));
        int day = Integer.parseInt(digits.substring(4, 6));
        try {
            LocalDate thisCentury = LocalDate.of(today.getYear() / 100 * 100 + yy, month, day);
            return Optional.of(thisCentury.isAfter(today) ? thisCentury.minusYears(100) : thisCentury);
        } catch (DateTimeException notADate) {
            // 29 February of a year that is not a leap year in this century may be one in the last.
            try {
                LocalDate lastCentury = LocalDate.of(today.getYear() / 100 * 100 - 100 + yy, month, day);
                return Optional.of(lastCentury);
            } catch (DateTimeException stillNotADate) {
                return Optional.empty();
            }
        }
    }

    /** The thirteenth digit, as the capture screen computes it: digits in odd places doubled, the sum to a ten. */
    public static boolean checkDigitIsValid(String digits) {
        if (digits == null || !digits.matches("[0-9]{13}")) {
            return false;
        }
        return checkDigit(digits.substring(0, 12)) == digits.charAt(12) - '0';
    }

    /** The check digit for the first twelve digits of a number. */
    public static int checkDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int digit = first12.charAt(i) - '0';
            if (i % 2 != 0) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
        }
        return (10 - sum % 10) % 10;
    }

    /** The gender a number's sequence carries: 0000 to 4999 female, 5000 to 9999 male -- as the record spells them. */
    public static String gender(String digits) {
        return Integer.parseInt(digits.substring(6, 10)) >= 5000 ? "Male" : "Female";
    }
}

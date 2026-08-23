package za.co.titandynamix.sasbo_online_backend.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Utility for parsing South African ID numbers.
 *
 * SA ID number format (13 digits): YYMMDDSSSSCAZ
 *   YY   — 2-digit year (century derived from SS range)
 *   MM   — month (01–12)
 *   DD   — day (01–31)
 *   SSSS — sequence number: 0000–4999 → Female, 5000–9999 → Male
 *   C    — citizenship: 0 = SA citizen, 1 = permanent resident
 *   A    — usually 8 (unused)
 *   Z    — Luhn checksum digit
 */
public final class SaIdParser {

    private SaIdParser() {
        // utility class — not instantiable
    }

    /**
     * Extracts the date of birth from a 13-digit SA ID number.
     *
     * @param idNumber the SA ID number
     * @return an Optional containing the date of birth, or empty if the ID is invalid
     */
    public static Optional<LocalDate> extractDateOfBirth(String idNumber) {
        if (!isValidIdFormat(idNumber)) {
            return Optional.empty();
        }
        String yy = idNumber.substring(0, 2);
        String mm = idNumber.substring(2, 4);
        String dd = idNumber.substring(4, 6);

        // Determine full year: if the resulting date is in the future, assume previous century
        int currentYear = LocalDate.now().getYear();
        int century = (currentYear / 100) * 100;
        int twoDigitCurrentYear = currentYear % 100;

        int parsedYY = Integer.parseInt(yy);
        if (parsedYY > twoDigitCurrentYear) {
            century -= 100; // born in previous century
        }
        int fullYear = century + parsedYY;

        String dateStr = String.format("%04d-%s-%s", fullYear, mm, dd);
        try {
            LocalDate dob = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            // Sanity check: DOB must not be in the future
            if (dob.isAfter(LocalDate.now())) {
                return Optional.empty();
            }
            return Optional.of(dob);
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Extracts the gender from a 13-digit SA ID number based on the sequence number (digits 6–9).
     * <p>
     * Sequence 0000–4999 → Female; 5000–9999 → Male.
     *
     * @param idNumber the SA ID number
     * @return an Optional containing "Male" or "Female", or empty if the ID is invalid
     */
    public static Optional<String> extractGender(String idNumber) {
        if (!isValidIdFormat(idNumber)) {
            return Optional.empty();
        }
        int sequence = Integer.parseInt(idNumber.substring(6, 10));
        return Optional.of(sequence >= 5000 ? "Male" : "Female");
    }

    /**
     * Validates that the given string is a 13-digit numeric value.
     *
     * @param idNumber the value to validate
     * @return true if the format is valid
     */
    public static boolean isValidIdFormat(String idNumber) {
        return idNumber != null && idNumber.matches("\\d{13}");
    }
}

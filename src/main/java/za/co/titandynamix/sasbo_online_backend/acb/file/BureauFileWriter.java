package za.co.titandynamix.sasbo_online_backend.acb.file;

import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Renders an approved run as the bureau file Standard Bank accepts: the layout measured, field by
 * field, from the July 2026 file SASBO sent -- 180-character fixed-width ASCII records, CRLF.
 *
 * <h2>Transcribed, not designed</h2>
 * Every position below is where the July file puts it, and every constant is what the July file
 * carries there. The NAMES are inferred -- the bank's layout document never arrived -- so each field
 * is named for what the arithmetic proved it holds, and the ones that could not be proved are
 * settings with July's values as defaults. The record types:
 *
 * <pre>
 *   02  user-set header   -- the union, the creation and purge dates, the file label
 *   04  user header       -- the four dates, the first sequence number, the service
 *   50  transaction       -- one per debit, grouped by action date
 *   52  contra            -- one per action date: the day's debits, credited to the union
 *   92  user trailer      -- sequence range, dates, counts, values, and the hash total
 *   94  user-set trailer  -- the 02 header again, and the record count
 * </pre>
 *
 * <h2>The trailer is derived from what was written, never trusted</h2>
 * Counts, values and the hash total -- the last twelve digits of the sum of every homing account
 * number, contras included -- are accumulated while the records are written. The caller compares
 * them with the run's own control totals before the file is released.
 *
 * <h2>It refuses rather than guesses</h2>
 * A line with no account type, an account wider than eleven digits, a membership number wider than
 * its eight-character field, a header whose dates are out of order or disagree with the lines, a
 * setting that is not configured: each refuses the file with a code, before a byte is written. A
 * bureau file that is wrong is a month's collection that fails at the bank.
 */
public final class BureauFileWriter {

    public static final int RECORD_LENGTH = 180;
    private static final String CRLF = "\r\n";

    /** Measured in the July file: every debit, every contra, every constant field. */
    static final String ENTRY_CLASS_DEBIT = "35";
    static final String ENTRY_CLASS_CREDIT = "10";
    static final String TAX_CODE = "0000";
    static final String HOMING_INSTITUTION = "21";
    static final String CURRENT_MONTH_REFERENCE = "SUBSCRIP";
    static final String FIRST_SEQUENCE = "000001";
    static final String GENERATION = "0000";
    static final String CONTRA_ACCOUNT_TYPE = "1";

    /**
     * Record 94, positions 57-62, read {@code 000323} in a file of 3,223 records: ceiling(3,223 / 10).
     * INFERRED to be a count of ten-record blocks; one sample cannot prove it, and question 36 asks.
     */
    static final int RECORDS_PER_BLOCK = 10;

    /** Record 94, positions 69-74: {@code 000002} in July, inferred as the two header/trailer pairs. */
    static final String HEADER_TRAILER_PAIRS = "000002";

    private static final DateTimeFormatter YYMMDD = DateTimeFormatter.ofPattern("yyMMdd");
    private static final BigInteger HASH_MODULUS = BigInteger.TEN.pow(12);

    private BureauFileWriter() {
    }

    /** The run's header dates, as the file prints them. */
    public record Header(LocalDate creationDate, LocalDate purgeDate, LocalDate firstActionDate,
                         LocalDate lastActionDate) {
    }

    /**
     * The file, and what writing it established.
     *
     * @param sequenceByLine the sequence number each run line was given -- stored on the line, since
     *                       the bank's returns report names an unpaid by action date and sequence
     */
    public record Written(byte[] bytes, Map<Long, Integer> sequenceByLine, int debitCount, int contraCount,
                          long debitCents, String hashTotal, int records) {

        @Override
        public String toString() {
            return "Written[records=" + records + ", debits=" + debitCount + ", cents=" + debitCents + "]";
        }
    }

    /** Renders the file, or refuses with a code before writing anything. */
    public static Written write(BureauFileSettings settings, Header header, List<BureauFileLine> lines) {
        requireSettings(settings);
        requireHeader(header, lines);
        requireLines(lines);

        List<BureauFileLine> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparing(BureauFileLine::actionDate).thenComparing(BureauFileLine::lineId));
        Map<LocalDate, List<BureauFileLine>> byDate = new TreeMap<>();
        for (BureauFileLine line : ordered) {
            byDate.computeIfAbsent(line.actionDate(), d -> new ArrayList<>()).add(line);
        }

        StringBuilder file = new StringBuilder((lines.size() + byDate.size() + 4) * (RECORD_LENGTH + 2));
        String userSet = userSetHeader(settings, header);
        append(file, userSet);
        append(file, userHeader(settings, header));

        Map<Long, Integer> sequences = new LinkedHashMap<>();
        int sequence = 0;
        long debitCents = 0;
        BigInteger hash = BigInteger.ZERO;
        for (Map.Entry<LocalDate, List<BureauFileLine>> day : byDate.entrySet()) {
            long dayCents = 0;
            for (BureauFileLine line : day.getValue()) {
                sequence++;
                long cents = cents(line.amount());
                append(file, transaction(settings, line, sequence, cents));
                sequences.put(line.lineId(), sequence);
                dayCents += cents;
                hash = hash.add(new BigInteger(line.homingAccount()));
            }
            sequence++;
            append(file, contra(settings, day.getKey(), sequence, dayCents));
            hash = hash.add(new BigInteger(settings.nominatedAccount()));
            debitCents += dayCents;
        }

        int debits = ordered.size();
        int contras = byDate.size();
        String hashTotal = digits(hash.mod(HASH_MODULUS).longValueExact(), 12);
        append(file, userTrailer(settings, header, sequence, debits, contras, debitCents, hashTotal));
        int records = 2 + debits + contras + 2;
        append(file, userSetTrailer(userSet, records));

        return new Written(file.toString().getBytes(StandardCharsets.US_ASCII), sequences, debits, contras,
                debitCents, hashTotal, records);
    }

    // ------------------------------------------------------------------------------------------
    // The six records
    // ------------------------------------------------------------------------------------------

    private static String userSetHeader(BureauFileSettings s, Header h) {
        return "02" + s.setNumber() + left(s.userName(), 6) + s.bankIdentifiers()
                + yymmdd(h.creationDate()) + yymmdd(h.purgeDate())
                + s.headerCounter() + digits(RECORD_LENGTH, 6) + left(s.fileLabel(), 4)
                + spaces(130);
    }

    private static String userHeader(BureauFileSettings s, Header h) {
        return "04" + s.userCode() + yymmdd(h.creationDate()) + yymmdd(h.purgeDate())
                + yymmdd(h.firstActionDate()) + yymmdd(h.lastActionDate())
                + FIRST_SEQUENCE + GENERATION + left(s.serviceType(), 10)
                + spaces(130);
    }

    private static String transaction(BureauFileSettings s, BureauFileLine line, int sequence, long cents) {
        return "50" + s.userBranch() + zeroFilled(s.nominatedAccount(), 11) + s.userCode()
                + digits(sequence, 6)
                + line.homingBranch() + zeroFilled(line.homingAccount(), 11) + line.accountType()
                + digits(cents, 11) + yymmdd(line.actionDate())
                + ENTRY_CLASS_DEBIT + TAX_CODE
                + left(s.userReference(), 10)
                + right(line.membershipNumber().trim(), 8)
                // SASBO's own two-character bank code followed the "C" in the old system's file. The
                // new system does not hold it; whether the reference keeps it is question 36.
                + "C" + spaces(2) + " 0"
                + left(monthReference(line.rePresentsMonth()), 8)
                + left(ascii(line.surname()), 20)
                + left(ascii(line.initials()), 4)
                + spaces(41) + HOMING_INSTITUTION + spaces(12);
    }

    private static String contra(BureauFileSettings s, LocalDate actionDate, int sequence, long dayCents) {
        return "52" + s.userBranch() + zeroFilled(s.nominatedAccount(), 11) + s.userCode()
                + digits(sequence, 6)
                + s.userBranch() + zeroFilled(s.nominatedAccount(), 11) + CONTRA_ACCOUNT_TYPE
                + digits(dayCents, 11) + yymmdd(actionDate)
                + ENTRY_CLASS_CREDIT + TAX_CODE
                + left(s.userReference(), 10)
                + left("CONTRA " + yymmdd(actionDate), 20)
                + left(s.userReference(), 10)
                + spaces(70);
    }

    private static String userTrailer(BureauFileSettings s, Header h, int lastSequence, int debits,
                                      int contras, long debitCents, String hashTotal) {
        return "92" + s.userCode() + FIRST_SEQUENCE + digits(lastSequence, 6)
                + yymmdd(h.firstActionDate()) + yymmdd(h.lastActionDate())
                + digits(debits, 6) + digits(contras, 6) + digits(contras, 6)
                + digits(debitCents, 12) + digits(debitCents, 12) + hashTotal
                + spaces(96);
    }

    private static String userSetTrailer(String userSetHeader, int records) {
        int blocks = (records + RECORDS_PER_BLOCK - 1) / RECORDS_PER_BLOCK;
        return "94" + userSetHeader.substring(2, 50) + spaces(6)
                + digits(blocks, 6) + digits(records, 6) + HEADER_TRAILER_PAIRS
                + spaces(106);
    }

    // ------------------------------------------------------------------------------------------
    // Refusals -- every one before a byte is written
    // ------------------------------------------------------------------------------------------

    private static void requireSettings(BureauFileSettings settings) {
        List<String> missing = settings.missing();
        if (!missing.isEmpty()) {
            throw new AcbRefusal(AcbRefusalCode.FILE_SETTINGS_MISSING,
                    "The bureau file cannot be written until the union's collection settings are "
                            + "configured: " + String.join("; ", missing) + ".",
                    Map.of("settings", String.join("; ", missing)));
        }
    }

    private static void requireHeader(Header h, List<BureauFileLine> lines) {
        List<String> problems = new ArrayList<>();
        if (h.creationDate() == null || h.purgeDate() == null || h.firstActionDate() == null
                || h.lastActionDate() == null) {
            problems.add("a header date is missing");
        } else {
            if (h.firstActionDate().isBefore(h.creationDate())) {
                problems.add("the first action date is before the creation date");
            }
            if (h.lastActionDate().isBefore(h.firstActionDate())) {
                problems.add("the last action date is before the first");
            }
            if (!h.purgeDate().isAfter(h.lastActionDate())) {
                problems.add("the purge date is not after the last action date");
            }
            LocalDate first = lines.stream().map(BureauFileLine::actionDate).min(Comparator.naturalOrder()).orElse(null);
            LocalDate last = lines.stream().map(BureauFileLine::actionDate).max(Comparator.naturalOrder()).orElse(null);
            if (first != null && (!first.equals(h.firstActionDate()) || !last.equals(h.lastActionDate()))) {
                problems.add("the header's first and last action dates are not the lines' own");
            }
        }
        if (!problems.isEmpty()) {
            throw new AcbRefusal(AcbRefusalCode.FILE_HEADER_INVALID,
                    "The file's header is wrong: " + String.join("; ", problems) + ".",
                    Map.of("problems", String.join("; ", problems)));
        }
    }

    private static void requireLines(List<BureauFileLine> lines) {
        if (lines.isEmpty()) {
            throw new AcbRefusal(AcbRefusalCode.FILE_LINES_INCOMPLETE, "A file with no debits is not a file.",
                    Map.of("lines", "0"));
        }
        List<Long> unfit = new ArrayList<>();
        Map<String, Integer> byProblem = new TreeMap<>();
        for (BureauFileLine line : lines) {
            String problem = problem(line);
            if (problem != null) {
                unfit.add(line.lineId());
                byProblem.merge(problem, 1, Integer::sum);
            }
        }
        if (!unfit.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("lines", String.valueOf(unfit.size()));
            details.put("firstLineIds", unfit.stream().limit(10).map(String::valueOf).reduce((a, b) -> a + ", " + b).orElse(""));
            byProblem.forEach((problem, count) -> details.put(problem, String.valueOf(count)));
            throw new AcbRefusal(AcbRefusalCode.FILE_LINES_INCOMPLETE,
                    unfit.size() + " line(s) cannot be written to the bureau file: " + byProblem.keySet() + ".",
                    details);
        }
    }

    /** What stops one line being written, or {@code null}. 🔒 Names the problem, never the value. */
    static String problem(BureauFileLine line) {
        if (line.accountType() == null) {
            return "no account type";
        }
        if (line.accountType() < 1 || line.accountType() > 3) {
            return "an account type other than 1, 2 or 3";
        }
        if (line.homingAccount() == null || !line.homingAccount().matches("[0-9]{1,11}")) {
            return "an account number that is not one to eleven digits";
        }
        if (line.homingBranch() == null || !line.homingBranch().matches("[0-9]{6}")) {
            return "a branch code that is not six digits";
        }
        if (line.membershipNumber() == null || line.membershipNumber().isBlank()
                || line.membershipNumber().trim().length() > 8) {
            return "a membership number that is blank or longer than eight characters";
        }
        if (line.surname() == null || line.surname().isBlank()) {
            return "no surname";
        }
        // 174.000 is a rand value written with a zero too many; 174.005 is not a rand value.
        if (line.amount() == null || line.amount().signum() <= 0
                || line.amount().stripTrailingZeros().scale() > 2) {
            return "an amount that is not a positive rand value";
        }
        return null;
    }

    // ------------------------------------------------------------------------------------------
    // Field formatting
    // ------------------------------------------------------------------------------------------

    private static void append(StringBuilder file, String record) {
        if (record.length() != RECORD_LENGTH) {
            throw new IllegalStateException("A " + record.substring(0, 2) + " record came out "
                    + record.length() + " characters wide, not " + RECORD_LENGTH);
        }
        file.append(record).append(CRLF);
    }

    static long cents(BigDecimal amount) {
        return amount.movePointRight(2).longValueExact();
    }

    static String yymmdd(LocalDate date) {
        return date.format(YYMMDD);
    }

    static String digits(long value, int width) {
        String text = Long.toString(value);
        if (value < 0 || text.length() > width) {
            throw new IllegalStateException(value + " does not fit a " + width + "-digit field");
        }
        return "0".repeat(width - text.length()) + text;
    }

    private static String zeroFilled(String digitsText, int width) {
        return "0".repeat(width - digitsText.length()) + digitsText;
    }

    static String left(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() >= width ? value.substring(0, width) : value + spaces(width - value.length());
    }

    static String right(String text, int width) {
        String value = text == null ? "" : text;
        return value.length() >= width ? value.substring(value.length() - width) : spaces(width - value.length()) + value;
    }

    private static String spaces(int width) {
        return " ".repeat(width);
    }

    /** The month an unpaid was for, as the file names it: {@code JUNE}; a month's own debit is {@code SUBSCRIP}. */
    static String monthReference(YearMonth rePresents) {
        return rePresents == null
                ? CURRENT_MONTH_REFERENCE
                : rePresents.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase(Locale.ROOT);
    }

    /**
     * A name as the file prints it: capitals, ASCII only. The file is ASCII by the bank's rule and the
     * July file prints every name in capitals; a diacritic loses its mark rather than the letter.
     */
    static String ascii(String text) {
        if (text == null) {
            return "";
        }
        String stripped = Normalizer.normalize(text.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder out = new StringBuilder(stripped.length());
        for (char c : stripped.toUpperCase(Locale.ROOT).toCharArray()) {
            out.append(c >= 0x20 && c < 0x7F ? c : ' ');
        }
        return out.toString();
    }
}

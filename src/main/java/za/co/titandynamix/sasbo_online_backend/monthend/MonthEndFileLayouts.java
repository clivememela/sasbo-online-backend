package za.co.titandynamix.sasbo_online_backend.monthend;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndListingsRepository.MemberLine;
import za.co.titandynamix.sasbo_online_backend.utils.SpreadsheetText;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The layouts of the files that leave SASBO each month (October build) -- each written a member at a time, as the rows
 * arrive from the database, never gathered first.
 *
 * <h2>Where each layout comes from</h2>
 * <ul>
 *   <li><b>Absa</b> ({@link BankLayout#ABSA_FIXED_WIDTH}): measured from the example records Stephne sent with MEN-34
 *       (the answers' attachments, section 2): the employee number at 1-8, the surname at 9-28, the initials at 29-31,
 *       the title at 32-35, the digit {@code 4} at 36, three spaces; no header, no trailer. <b>What the 4 means is MEN-34's
 *       clarification</b> ("please verify with the IT the reason for the number (4)"): it is written on every record, as
 *       on every one of hers. Where her example is silent this file decides, and each decision is a question for SASBO's
 *       IT: the employee number is zero-filled on the left (eleven of her twelve records are eight digits; the twelfth
 *       is seven, with every field after it a position to the left); lines end CR LF; capitals without accents;</li>
 *   <li><b>A full list</b> in a workbook or CSV: SASBO's own <em>Miscellaneous Extract</em>, layout D of the August
 *       files (SBVFULL, ACCESSFULL, NATIONALHOUSINGFULL): {@code Title · Initial · Name · Membership Number · Employee
 *       Number · Bank Name · ID Number · SUB}, under the title row {@code MISCELLANEOUS EXTRACT - yyyy/mm/dd};</li>
 *   <li><b>New and deleted</b> in a workbook or CSV: SASBO's standard, layout A (NEDBANK, OLDMUTUAL, SBV, AFRICAN,
 *       SARES, LANDBANK, SANTAM): {@code Class · Title · Initial · Name · Employee Number · Membership Number · ID Number
 *       · Branch Name}, {@code Class} NEW or DEL, under {@code <EMPLOYER> NEW AND DELETED MEMBERS - yyyy/mm/dd}. Every
 *       bank whose own layout has not reached us -- Nedbank's among them (MEN-34) -- is sent this;</li>
 *   <li><b>Old Mutual's funeral benefit list</b> ({@link ProviderLayout#OLD_MUTUAL_FUNERAL}): the 24 headings of the
 *       example Stephne sent with MEN-35, in their order, each value space-filled to the width her rows show. Eight
 *       headings held words our reader could not print; their names here are OUR READING, to be replaced by her heading
 *       row: {@link #OLD_MUTUAL_READ_HEADINGS};</li>
 *   <li><b>Law Call, and a suspended benefit should it return</b> ({@link ProviderLayout#PROVISIONAL_MEMBER_LIST}):
 *       PROVISIONAL -- no layout has reached us; who a member is, and nothing more.</li>
 * </ul>
 */
public final class MonthEndFileLayouts {

    /** How a bank's file is laid out (data: {@code month_end_banks.layout}). */
    public enum BankLayout { ABSA_FIXED_WIDTH, SHEET, CSV }

    /** How a provider's list is laid out (data: {@code month_end_providers.layout}). */
    public enum ProviderLayout { OLD_MUTUAL_FUNERAL, PROVISIONAL_MEMBER_LIST }

    /** Absa's record without its line ending: 36 significant characters and three spaces. */
    public static final int ABSA_RECORD_LENGTH = 39;

    /** The digit on every Absa record, at position 36 (MEN-34: its meaning is to be confirmed by SASBO's IT). */
    public static final char ABSA_RECORD_MARK = '4';

    static final String LINE_END = "\r\n";

    static final DateTimeFormatter TITLE_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** How a member paying by debit order is stored (BR41): every other way is read as a deduction. */
    static final String DEBIT_ORDER = "Debit Order";

    /** Layout D's columns, the full list (August's Miscellaneous Extract). */
    public static final List<String> FULL_LIST_COLUMNS = List.of("Title", "Initial", "Name", "Membership Number",
            "Employee Number", "Bank Name", "ID Number", "SUB");

    /** Layout A's columns, the month's new and deleted. */
    public static final List<String> NEW_AND_DELETED_COLUMNS = List.of("Class", "Title", "Initial", "Name",
            "Employee Number", "Membership Number", "ID Number", "Branch Name");

    /** Old Mutual's 24 headings, in the order of her example, and the width each value is space-filled to. */
    public static final List<String> OLD_MUTUAL_COLUMNS = List.of("SchemeCode", "Prefix", "Initials", "Surname",
            "FirstNames", "Employer", "EmployerAddress1", "EmployerAddress2", "EmployerAddress3", "EmployerPostalCode",
            "PostalAddr1", "PostalAddr2", "PostalAddr3", "PostalPoCode", "PayMethod", "IdentityNo", "EmployeeNo",
            "MemberNo", "FromMonthYear", "EmployeeAddress1", "EmployeeAddress2", "EmployeeAddress3",
            "EmployeePostalCode", "MemberContactNumber");

    /** The headings whose words our reader printed as signatures: named here by our reading, to be confirmed. */
    public static final Set<String> OLD_MUTUAL_READ_HEADINGS = Set.of("SchemeCode", "Prefix", "PostalAddr1",
            "PostalAddr2", "PostalAddr3", "PostalPoCode", "PayMethod", "FromMonthYear");

    static final int[] OLD_MUTUAL_WIDTHS = {0, 4, 4, 20, 33, 30, 30, 25, 20, 4, 30, 25, 20, 4, 1, 13, 7, 0, 5, 0, 0, 0,
        0, 0};

    /** The provisional list's columns: who a member is, and nothing more, until the provider's layout arrives. */
    public static final List<String> PROVISIONAL_COLUMNS = List.of("MemberNo", "Title", "Initials", "FirstNames",
            "Surname", "IdentityNo", "Employer", "DateJoined");

    private MonthEndFileLayouts() {
    }

    /** What a file held once written: its rows, of them new and deleted, and the members it could not carry. */
    public record Counts(int rows, int newRows, int deletedRows, int leftOut) {
    }

    // ------------------------------------------------------------------------------------------------ Absa

    /**
     * One member as Absa's record, without its line ending -- or {@code null} where the record cannot carry them: an
     * employee number that is not one to eight digits, or no surname. Such a member is counted, never named.
     */
    public static String absaRecord(MemberLine member) {
        String employee = member.employeeNumber() == null ? "" : member.employeeNumber().trim();
        if (!employee.matches("\\d{1,8}")) {
            return null;
        }
        String surname = capitals(member.surname(), true);
        if (surname.isEmpty()) {
            return null;
        }
        String initials = capitals(member.initials(), false).replace(" ", "");
        if (initials.isEmpty()) {
            initials = initialsOf(member.firstNames());
        }
        String title = capitals(member.title(), false).replace(" ", "");
        return "0".repeat(8 - employee.length()) + employee + field(surname, 20) + field(initials, 3) + field(title, 4)
                + ABSA_RECORD_MARK + "   ";
    }

    /** Absa's file: a record a member, CR LF after each, in US-ASCII. */
    public static final class AbsaFile implements Consumer<MemberLine> {

        private final Writer out;
        private final Set<String> leaveOut;
        private int rows;
        private int leftOut;

        /** @param leaveOut membership numbers never written (a seeded member where the production marker is set) */
        public AbsaFile(OutputStream out, Set<String> leaveOut) {
            this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.US_ASCII));
            this.leaveOut = leaveOut;
        }

        @Override
        public void accept(MemberLine member) {
            String record = leaveOut.contains(member.membershipNumber()) ? null : absaRecord(member);
            if (record == null) {
                leftOut++;
                return;
            }
            try {
                out.write(record);
                out.write(LINE_END);
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
            rows++;
        }

        public Counts finish() {
            try {
                out.flush();
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
            return new Counts(rows, 0, 0, leftOut);
        }
    }

    // ------------------------------------------------------------------------------------------------ the rows

    /** Layout D's row for a member of a full list. */
    public static List<String> fullListRow(MemberLine m) {
        return List.of(upper(m.title()), upper(m.initials()), upper(m.surname()), text(m.membershipNumber()),
                text(m.employeeNumber()), upper(m.employerName()), text(m.identity()), rand(m.subscription()));
    }

    /** Layout A's row for a new or a deleted member: {@code Class} NEW or DEL. */
    public static List<String> newAndDeletedRow(MemberLine m) {
        return List.of(text(m.className()), upper(m.title()), upper(m.initials()), upper(m.surname()),
                text(m.employeeNumber()), text(m.membershipNumber()), text(m.identity()), upper(m.workplaceName()));
    }

    /**
     * Old Mutual's row: the 24 columns, each space-filled to its width, blank where nothing of ours fills it -- the
     * employer's address (not on record here), the contact columns her example leaves empty, and the month column whose
     * word her example does not show.
     */
    public static List<String> oldMutualRow(MemberLine m) {
        String[] address = addressLines(m.address());
        String[] values = {
            "", upper(m.title()), upper(m.initials()), upper(m.surname()), upper(m.firstNames()),
            upper(m.employerName()), "", "", "", "",
            upper(address[0]), upper(address[1]), upper(address[2]), "",
            DEBIT_ORDER.equals(m.paymentType()) ? "D" : "P",
            text(m.identity()), text(m.employeeNumber()), text(m.membershipNumber()), "",
            "", "", "", "", ""};
        String[] padded = new String[values.length];
        for (int c = 0; c < values.length; c++) {
            int width = OLD_MUTUAL_WIDTHS[c];
            // A number that names someone is filled out to its width, never cut short: a cut number is somebody else's.
            padded[c] = width == 0 ? values[c] : OLD_MUTUAL_NEVER_CUT.contains(c) && values[c].length() > width
                    ? values[c] : field(values[c], width);
        }
        return List.of(padded);
    }

    /** The columns of a number that names someone -- the identity, employee and membership numbers. */
    static final Set<Integer> OLD_MUTUAL_NEVER_CUT = Set.of(15, 16, 17);

    /** The provisional list's row. */
    public static List<String> provisionalRow(MemberLine m) {
        return List.of(text(m.membershipNumber()), upper(m.title()), upper(m.initials()), text(m.firstNames()),
                text(m.surname()), text(m.identity()), text(m.employerName()),
                m.joinDate() == null ? "" : m.joinDate().toString());
    }

    // ------------------------------------------------------------------------------------------------ CSV

    /** A CSV file: a heading row, a row a member, CR LF, UTF-8, every cell safe to open in a spreadsheet. */
    public static final class CsvFile {

        private final Writer out;
        private int rows;

        public CsvFile(OutputStream out, List<String> headings) {
            this.out = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            write(headings);
        }

        public void row(List<String> cells) {
            write(cells);
            rows++;
        }

        public int rows() {
            return rows;
        }

        public void finish() {
            try {
                out.flush();
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }

        private void write(List<String> cells) {
            try {
                for (int c = 0; c < cells.size(); c++) {
                    if (c > 0) {
                        out.write(',');
                    }
                    out.write(csvCell(cells.get(c)));
                }
                out.write(LINE_END);
            } catch (IOException unwritable) {
                throw new UncheckedIOException(unwritable);
            }
        }
    }

    /** One CSV cell: quoted where it must be, and never a formula. */
    static String csvCell(String value) {
        String safe = SpreadsheetText.safe(value == null ? "" : value);
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0 || safe.indexOf('\r') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    // ------------------------------------------------------------------------------------------------ workbooks

    /** A workbook of one sheet: an optional title row, a heading row, a row a member -- streamed. */
    public static final class SheetFile implements AutoCloseable {

        /** Rows a streaming sheet keeps in memory; the rest are on disk. */
        static final int WINDOW = 100;

        private final SXSSFWorkbook book = new SXSSFWorkbook(WINDOW);
        private final Sheet sheet;
        private int next;
        private int rows;

        public SheetFile(String sheetName, String title, List<String> headings) {
            sheet = book.createSheet(sheetName);
            Font bold = book.createFont();
            bold.setBold(true);
            CellStyle strong = book.createCellStyle();
            strong.setFont(bold);
            if (title != null) {
                cells(sheet.createRow(next++), List.of(title), strong);
            }
            cells(sheet.createRow(next++), headings, strong);
        }

        public void row(List<String> values) {
            cells(sheet.createRow(next++), values, null);
            rows++;
        }

        public int rows() {
            return rows;
        }

        /** The workbook's bytes, to {@code out} -- an encrypting stream, so the clear bytes are never kept. */
        public void writeTo(OutputStream out) throws IOException {
            book.write(out);
        }

        @Override
        public void close() {
            try {
                book.close();
            } catch (IOException ignored) {
                // The temporary files go with the workbook; there is nothing else to undo.
            }
        }

        private static void cells(Row row, List<String> values, CellStyle style) {
            for (int c = 0; c < values.size(); c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(SpreadsheetText.safe(values.get(c) == null ? "" : values.get(c)));
                if (style != null) {
                    cell.setCellStyle(style);
                }
            }
        }
    }

    /** Layout D's title row. */
    public static String fullListTitle(LocalDate monthEnd) {
        return "MISCELLANEOUS EXTRACT - " + monthEnd.format(TITLE_DATE);
    }

    /** Layout A's title row. */
    public static String newAndDeletedTitle(String employer, LocalDate monthEnd) {
        return upper(employer) + " NEW AND DELETED MEMBERS - " + monthEnd.format(TITLE_DATE);
    }

    // ------------------------------------------------------------------------------------------------ text

    /** {@code value} cut to {@code width} and space-filled to it -- a fixed-width field. */
    public static String field(String value, int width) {
        String v = value == null ? "" : value;
        return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
    }

    /**
     * Capitals without accents: A to Z, and -- for a surname -- the space, hyphen and apostrophe a surname may hold;
     * every other character dropped, runs of spaces made one.
     */
    static String capitals(String value, boolean surname) {
        if (value == null) {
            return "";
        }
        String bare = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(bare.length());
        for (int i = 0; i < bare.length(); i++) {
            char c = bare.charAt(i);
            if (c >= 'A' && c <= 'Z' || surname && (c == ' ' || c == '-' || c == '\'')) {
                out.append(c);
            }
        }
        return out.toString().trim().replaceAll(" {2,}", " ");
    }

    /** The first letter of each first name: the initials where none are on record. */
    static String initialsOf(String firstNames) {
        StringBuilder initials = new StringBuilder();
        for (String name : capitals(firstNames, true).split("[ \\-']+")) {
            if (!name.isEmpty()) {
                initials.append(name.charAt(0));
            }
        }
        return initials.toString();
    }

    static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** A subscription as SASBO's files print it: {@code 174.00}, as text. */
    static String rand(BigDecimal value) {
        return value == null ? "" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /** A member's address as the three lines a provider's list has room for: its parts, the rest in the third. */
    static String[] addressLines(String address) {
        String[] lines = {"", "", ""};
        if (address == null || address.isBlank()) {
            return lines;
        }
        String[] parts = address.split(",");
        for (int p = 0, line = 0; p < parts.length; p++) {
            String part = parts[p].trim();
            if (part.isEmpty()) {
                continue;
            }
            lines[line] = lines[line].isEmpty() ? part : lines[line] + ", " + part;
            if (line < 2) {
                line++;
            }
        }
        return lines;
    }
}

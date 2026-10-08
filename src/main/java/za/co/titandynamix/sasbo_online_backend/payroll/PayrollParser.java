package za.co.titandynamix.sasbo_online_backend.payroll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.PayrollRefusalCode;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns an employer's sheet into lines: finds the row of headings by its words, takes each field's column by the
 * employer's layout (or an administrator's mapping), and reads each line under it -- with a reason for every line that
 * cannot be read.
 *
 * <h2>The rules, from SASBO's fifteen samples (fixtures' {@code LAYOUTS.md})</h2>
 * <ul>
 *   <li>The heading row is found by its words, never by its position: row 1 in nine files, row 2, 3 and 7 in others.
 *       It is the first row, within the first {@value PayrollLimits#HEADING_SCAN_ROWS}, that names an amount and at
 *       least one way of knowing the person (a staff number, an ID number, a name).</li>
 *   <li>The table ends where the headings come round again, or at a blank row followed by anything but another line
 *       of it -- a caption, the headings, a total: Absa and Capitec put a second list -- withdrawn or deleted members --
 *       under the first. Those rows are counted, said on the file's page, and not compared. A blank row with more
 *       lines under it (a page or branch break in an export) does not end it: ended there, every member below would
 *       be "missing" and on the employer's ADD list.</li>
 *   <li>A row with no amount, staff number or ID number before the first line is a caption (African Bank's
 *       "Company :" and "Group :" rows) and is passed over. A row with an amount and nobody beside it is the file's
 *       total where it says TOTAL, and a line that cannot be read where it does not.</li>
 *   <li>Each line's amount is its amount plus any adjustment (FNB), or its debit plus its credit where it has no total
 *       (Standard Bank), its sign dropped. Bankserv prints two months: the amount is the column named for the file's
 *       month, else the later one.</li>
 * </ul>
 */
public final class PayrollParser {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** The fields that say who a line is. One of them, and the amount, make a heading row. */
    private static final List<PayrollField> IDENTIFIERS = List.of(PayrollField.STAFF_NUMBER, PayrollField.ID_NUMBER,
            PayrollField.SURNAME, PayrollField.FULL_NAME);

    /** The order fields take their columns in, so a column is never given to two fields. */
    private static final List<PayrollField> ORDER = List.of(PayrollField.AMOUNT, PayrollField.STAFF_NUMBER,
            PayrollField.ID_NUMBER, PayrollField.SURNAME, PayrollField.INITIALS, PayrollField.FIRST_NAMES,
            PayrollField.FULL_NAME, PayrollField.ADJUSTMENT, PayrollField.DEBIT, PayrollField.CREDIT,
            PayrollField.FOR_PERIOD);

    /** One line as read. 🔒 Personal data: never logged, no generated text of it. */
    public record Line(int lineNumber, String staffNumber, String staffKey, String idNumber, String idKey,
                       PayrollNames.Name name, BigDecimal amount, YearMonth forPeriod, boolean valid, String problem,
                       String raw) {

        @Override
        public String toString() {
            return "Line[" + lineNumber + (valid ? "" : ", not read") + "]";
        }
    }

    /**
     * A file as read: where its headings were, every heading on that row as printed, the heading taken for each field,
     * the lines, the total it states (where it states one), and how many rows below the first table were not read.
     */
    public record Parsed(String sheet, int headingRow, List<String> headingCells, Map<String, String> headings,
                         List<Line> lines, BigDecimal statedTotal, int rowsNotRead) {

        public Parsed {
            headingCells = List.copyOf(headingCells);
            headings = Collections.unmodifiableMap(new LinkedHashMap<>(headings));
            lines = List.copyOf(lines);
        }
    }

    private PayrollParser() {
    }

    /**
     * @param mapping an administrator's override, field ({@link PayrollField#wire()}) to heading as printed; empty or
     *                {@code null} to go by the layout
     * @param period  the month the file deducts for -- it names Bankserv's amount column
     */
    public static Parsed parse(SheetGrid grid, PayrollLayout layout, Map<String, String> mapping, YearMonth period) {
        Map<PayrollField, String> override = override(mapping);
        Located located = override.isEmpty() ? locate(grid, layout, period) : locateByMapping(grid, override);
        return readLines(grid, layout, located);
    }

    // ------------------------------------------------------------------------------------------- finding headings

    /** The heading row, and each field's column on it. */
    private record Located(int index, SheetGrid.Row row, Map<PayrollField, Integer> columns) {
    }

    private static Located locate(SheetGrid grid, PayrollLayout layout, YearMonth period) {
        List<SheetGrid.Row> rows = grid.rows();
        for (int i = 0; i < rows.size() && rows.get(i).number() <= PayrollLimits.HEADING_SCAN_ROWS; i++) {
            Map<PayrollField, Integer> columns = columnsOf(rows.get(i), layout, period);
            if (columns.containsKey(PayrollField.AMOUNT) && IDENTIFIERS.stream().anyMatch(columns::containsKey)) {
                return new Located(i, rows.get(i), columns);
            }
        }
        throw new PayrollRefusal(PayrollRefusalCode.HEADING_ROW_NOT_FOUND, "No row of headings was found that names an "
                + "amount and a staff number, an ID number or a name. Say which heading is which field, and load the "
                + "file again.", Map.of("lookedFor", "amount; staff number, ID number or name"));
    }

    /** Each field's column on {@code row}: by the layout's own headings first, then by the general vocabulary. */
    static Map<PayrollField, Integer> columnsOf(SheetGrid.Row row, PayrollLayout layout, YearMonth period) {
        Map<PayrollField, Integer> columns = new EnumMap<>(PayrollField.class);
        Set<Integer> taken = new HashSet<>();
        for (PayrollField field : ORDER) {
            List<String> own = layout.headings().get(field);
            Integer column = own == null ? null : pick(row, own, taken, field, period);
            if (column == null && own == null && PayrollLayout.VOCABULARY.containsKey(field)) {
                column = pick(row, PayrollLayout.VOCABULARY.get(field), taken, field, period);
            }
            if (column != null) {
                columns.put(field, column);
                taken.add(column);
            }
        }
        return columns;
    }

    /** The column whose heading is one of {@code aliases}; for the amount, the one named for the month, else the last. */
    private static Integer pick(SheetGrid.Row row, List<String> aliases, Set<Integer> taken, PayrollField field,
                                YearMonth period) {
        List<Integer> found = new ArrayList<>();
        for (int column = 0; column < row.cells().size(); column++) {
            if (!taken.contains(column) && PayrollLayout.matchesAny(aliases, PayrollLayout.headingKey(row.cell(column)))) {
                found.add(column);
            }
        }
        if (found.isEmpty()) {
            return null;
        }
        if (field == PayrollField.AMOUNT && found.size() > 1) {
            String month = period == null ? null : period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    .toUpperCase(Locale.ROOT);
            for (Integer column : found) {
                String key = PayrollLayout.headingKey(row.cell(column));
                if (month != null && key.contains(month) && key.contains(String.valueOf(period.getYear()))) {
                    return column;
                }
            }
            return found.get(found.size() - 1);
        }
        return found.get(0);
    }

    /** An administrator's mapping, validated: known fields, an amount, and at least one way of knowing the person. */
    private static Map<PayrollField, String> override(Map<String, String> mapping) {
        Map<PayrollField, String> fields = new EnumMap<>(PayrollField.class);
        if (mapping == null) {
            return fields;
        }
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            PayrollField field = PayrollField.fromWire(entry.getKey()).orElseThrow(() -> mappingInvalid(
                    "That is not a field a column can be: use staffNumber, idNumber, initials, firstNames, surname, "
                            + "fullName, amount, adjustment, debit, credit or forPeriod.",
                    PayrollRefusal.echo(entry.getKey())));
            if (entry.getValue().length() > PayrollLimits.MAX_CELL_LENGTH) {
                throw mappingInvalid("A heading in the mapping is longer than any heading on a file.", field.wire());
            }
            fields.put(field, entry.getValue());
        }
        if (fields.isEmpty()) {
            return fields;
        }
        if (!fields.containsKey(PayrollField.AMOUNT) && !fields.containsKey(PayrollField.DEBIT)) {
            throw mappingInvalid("Say which heading holds the amount.", "amount");
        }
        if (IDENTIFIERS.stream().noneMatch(fields::containsKey)) {
            throw mappingInvalid("Say which heading holds the staff number, the ID number or the name.", "staffNumber");
        }
        return fields;
    }

    private static Located locateByMapping(SheetGrid grid, Map<PayrollField, String> override) {
        List<SheetGrid.Row> rows = grid.rows();
        for (int i = 0; i < rows.size() && rows.get(i).number() <= PayrollLimits.HEADING_SCAN_ROWS; i++) {
            SheetGrid.Row row = rows.get(i);
            Map<PayrollField, Integer> columns = new EnumMap<>(PayrollField.class);
            for (Map.Entry<PayrollField, String> entry : override.entrySet()) {
                String wanted = PayrollLayout.headingKey(entry.getValue());
                for (int column = 0; column < row.cells().size(); column++) {
                    if (!columns.containsValue(column) && wanted.equals(PayrollLayout.headingKey(row.cell(column)))) {
                        columns.put(entry.getKey(), column);
                        break;
                    }
                }
            }
            if (columns.size() == override.size()) {
                return new Located(i, row, columns);
            }
        }
        PayrollField missing = override.keySet().iterator().next();
        throw mappingInvalid("A heading named in the mapping is not on the file's heading row. Use the headings as "
                + "they are printed.", missing.wire());
    }

    private static PayrollRefusal mappingInvalid(String message, String field) {
        return new PayrollRefusal(PayrollRefusalCode.COLUMN_MAPPING_INVALID, message,
                PayrollRefusal.details("field", field));
    }

    // ------------------------------------------------------------------------------------------------ the lines

    private static Parsed readLines(SheetGrid grid, PayrollLayout layout, Located located) {
        List<SheetGrid.Row> rows = grid.rows();
        Map<PayrollField, Integer> columns = located.columns();
        String headingSignature = signature(located.row());
        List<Line> lines = new ArrayList<>();
        BigDecimal statedTotal = null;
        int previous = located.row().number();
        int notRead = 0;
        boolean ended = false;
        for (int i = located.index() + 1; i < rows.size(); i++) {
            SheetGrid.Row row = rows.get(i);
            if (ended) {
                notRead++;
                continue;
            }
            boolean afterBlank = row.number() > previous + 1;
            previous = row.number();
            if (!lines.isEmpty() && (signature(row).equals(headingSignature)
                    || (afterBlank && !carriesOn(row, columns, layout)))) {
                ended = true;
                notRead++;
                continue;
            }
            String staff = cell(row, columns, PayrollField.STAFF_NUMBER);
            String id = cell(row, columns, PayrollField.ID_NUMBER);
            String amountText = cell(row, columns, PayrollField.AMOUNT);
            boolean hasMoney = amountText != null || cell(row, columns, PayrollField.DEBIT) != null
                    || cell(row, columns, PayrollField.CREDIT) != null;
            if (!hasMoney && staff == null && id == null) {
                continue;
            }
            PayrollNames.Name name = PayrollNames.read(layout.nameStyle(), cell(row, columns, PayrollField.INITIALS),
                    cell(row, columns, PayrollField.FIRST_NAMES), cell(row, columns, PayrollField.SURNAME),
                    cell(row, columns, PayrollField.FULL_NAME));
            String staffKey = PayrollKeys.staffKey(staff);
            String idKey = PayrollKeys.idKey(id);
            // A totals row: money, nobody's ID or name, and the word TOTAL -- often in the staff number's column.
            if (hasMoney && idKey == null && name.isEmpty() && mentionsTotal(row)
                    && (staffKey == null || !staffKey.chars().allMatch(Character::isDigit))) {
                statedTotal = amountOf(row, columns);
                continue;
            }
            lines.add(line(row, columns, staff, staffKey, id, idKey, name));
            if (lines.size() > PayrollLimits.MAX_ROWS) {
                throw GridReader.tooManyRows();
            }
        }
        if (lines.isEmpty()) {
            throw new PayrollRefusal(PayrollRefusalCode.NO_ROWS, "The file has its headings but no lines under them.");
        }
        Map<String, String> headings = new LinkedHashMap<>();
        for (PayrollField field : ORDER) {
            Integer column = columns.get(field);
            if (column != null) {
                headings.put(field.wire(), located.row().cell(column));
            }
        }
        List<String> headingCells = located.row().cells().stream().filter(c -> c != null).toList();
        return new Parsed(grid.sheetName(), located.row().number(), headingCells, headings, lines, statedTotal,
                notRead);
    }

    /**
     * Whether the row right after a blank row carries the list on: a line of this table -- an amount that reads as one,
     * and a staff number, an ID number or a name -- and not the file's total.
     */
    private static boolean carriesOn(SheetGrid.Row row, Map<PayrollField, Integer> columns, PayrollLayout layout) {
        String staffKey = PayrollKeys.staffKey(cell(row, columns, PayrollField.STAFF_NUMBER));
        String idKey = PayrollKeys.idKey(cell(row, columns, PayrollField.ID_NUMBER));
        PayrollNames.Name name = PayrollNames.read(layout.nameStyle(), cell(row, columns, PayrollField.INITIALS),
                cell(row, columns, PayrollField.FIRST_NAMES), cell(row, columns, PayrollField.SURNAME),
                cell(row, columns, PayrollField.FULL_NAME));
        if (amountOf(row, columns) == null || (staffKey == null && idKey == null && name.isEmpty())) {
            return false;
        }
        boolean total = idKey == null && name.isEmpty() && mentionsTotal(row)
                && (staffKey == null || !staffKey.chars().allMatch(Character::isDigit));
        return !total;
    }

    private static Line line(SheetGrid.Row row, Map<PayrollField, Integer> columns, String staff, String staffKey,
                             String id, String idKey, PayrollNames.Name name) {
        BigDecimal amount = amountOf(row, columns);
        YearMonth forPeriod = PayrollKeys.forPeriod(cell(row, columns, PayrollField.FOR_PERIOD));
        String problem = null;
        if (amount == null) {
            problem = cell(row, columns, PayrollField.AMOUNT) == null && cell(row, columns, PayrollField.DEBIT) == null
                    && cell(row, columns, PayrollField.CREDIT) == null
                    ? "The line has no amount." : "The amount could not be read.";
        } else if (amount.compareTo(PayrollLimits.MAX_LINE_AMOUNT) > 0) {
            problem = "The amount is far larger than any subscription.";
        } else if (staffKey == null && idKey == null && name.isEmpty()) {
            problem = "The line has an amount but no staff number, ID number or name.";
        }
        return new Line(row.number(), cap(staff, 40), staffKey, cap(id, 20), idKey, name, amount, forPeriod,
                problem == null, problem, raw(row));
    }

    /** The line's amount: amount plus adjustment, or debit plus credit, its sign dropped; {@code null} if unreadable. */
    static BigDecimal amountOf(SheetGrid.Row row, Map<PayrollField, Integer> columns) {
        String amountText = cell(row, columns, PayrollField.AMOUNT);
        BigDecimal total;
        if (amountText != null) {
            total = PayrollKeys.signedAmount(amountText);
            if (total == null) {
                return null;
            }
        } else if (columns.containsKey(PayrollField.DEBIT) || columns.containsKey(PayrollField.CREDIT)) {
            BigDecimal debit = optionalAmount(cell(row, columns, PayrollField.DEBIT));
            BigDecimal credit = optionalAmount(cell(row, columns, PayrollField.CREDIT));
            if (debit == null || credit == null) {
                return null;
            }
            total = debit.add(credit);
        } else {
            return null;
        }
        String adjustment = cell(row, columns, PayrollField.ADJUSTMENT);
        if (adjustment != null) {
            BigDecimal value = PayrollKeys.signedAmount(adjustment);
            if (value == null) {
                return null;
            }
            total = total.add(value);
        }
        return total.abs();
    }

    /** A blank debit or credit is nought; text that is not an amount is {@code null}. */
    private static BigDecimal optionalAmount(String text) {
        return text == null ? BigDecimal.ZERO.setScale(2) : PayrollKeys.signedAmount(text);
    }

    private static boolean mentionsTotal(SheetGrid.Row row) {
        return row.cells().stream().anyMatch(c -> c != null && PayrollLayout.headingKey(c).contains("TOTAL"));
    }

    private static String cell(SheetGrid.Row row, Map<PayrollField, Integer> columns, PayrollField field) {
        Integer column = columns.get(field);
        return column == null ? null : row.cell(column);
    }

    private static String signature(SheetGrid.Row row) {
        StringBuilder key = new StringBuilder();
        for (String cell : row.cells()) {
            key.append(PayrollLayout.headingKey(cell)).append('|');
        }
        return key.toString();
    }

    /** The whole row as printed, as a JSON array -- kept with the line, and only there. */
    private static String raw(SheetGrid.Row row) {
        ArrayNode cells = JSON.createArrayNode();
        row.cells().forEach(cells::add);
        String text = cells.toString();
        return text.length() > 4000 ? text.substring(0, 4000) : text;
    }

    private static String cap(String text, int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }
}

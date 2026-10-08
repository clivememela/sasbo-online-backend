package za.co.titandynamix.sasbo_online_backend.legacy.read;

import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns the records of a delimited file or a sheet into rows: passes over the rows before the data, reads the header,
 * names each cell by its column, and says what was wrong -- for the CSV reader and the workbook reader alike, so both
 * name a column, a short row and a long one the same way.
 *
 * <p>A column the header leaves blank, and every column of a file with no header, is named by its position: {@code #5}.
 * Two columns with the same name keep both, the second as {@code NAME (2)}.
 */
final class RecordAssembler {

    /** A column's name is the file's own text, cut here: it is a key, not a value. */
    private static final int MAX_COLUMN_NAME = 100;

    private final ReaderSettings settings;
    private final ReadLimits limits;
    private final RowSink sink;

    private int recordsSeen;
    private List<String> columns;
    private int rows;

    RecordAssembler(ReaderSettings settings, ReadLimits limits, RowSink sink) {
        this.settings = settings;
        this.limits = limits;
        this.sink = sink;
    }

    /** How many columns the header named; 0 before the header, or where there is none. */
    int width() {
        return columns == null ? 0 : columns.size();
    }

    /**
     * One record as read.
     *
     * @param rowNumber where it starts in the file, 1-based
     * @param cells     its cells, at most the column ceiling of them
     * @param truncated the positions of cells cut at the cell ceiling
     * @param malformed a quote not closed, or text after a closing quote
     * @param overflow  more cells than the column ceiling: the rest were dropped
     */
    void record(int rowNumber, List<String> cells, Set<Integer> truncated, boolean malformed, boolean overflow) {
        if (!malformed && !overflow && blank(cells)) {
            return;
        }
        recordsSeen++;
        if (recordsSeen <= settings.skipRows()) {
            return;
        }
        if (settings.header() && columns == null) {
            columns = header(cells, overflow);
            return;
        }
        emit(rowNumber, cells, truncated, malformed, overflow);
    }

    ReadSummary finish() {
        if (rows == 0) {
            throw new SourceRefused(SourceRefused.Reason.EMPTY, "The file holds no rows to import.");
        }
        return new ReadSummary(rows, columns == null ? List.of() : columns);
    }

    private List<String> header(List<String> cells, boolean overflow) {
        if (overflow || cells.size() > limits.maxColumns()) {
            throw new SourceRefused(SourceRefused.Reason.NOT_READABLE,
                    "The header has more columns than the limit: this is not a member export, or the delimiter is wrong.");
        }
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            String name = cells.get(i).trim().replaceAll("\\s+", " ");
            if (name.length() > MAX_COLUMN_NAME) {
                name = name.substring(0, MAX_COLUMN_NAME);
            }
            if (name.isEmpty()) {
                name = "#" + (i + 1);
            }
            String unique = name;
            for (int n = 2; !seen.add(unique.toUpperCase(Locale.ROOT)); n++) {
                unique = name + " (" + n + ")";
            }
            names.add(unique);
        }
        return List.copyOf(names);
    }

    private void emit(int rowNumber, List<String> cells, Set<Integer> truncated, boolean malformed, boolean overflow) {
        Map<String, String> values = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            String key = columns != null && i < columns.size() ? columns.get(i) : "#" + (i + 1);
            values.put(key, cells.get(i));
            if (truncated.contains(i)) {
                problems.add(new Problem(RuleCode.FIELD_TOO_LONG, key));
            }
        }
        boolean broken = malformed || overflow;
        if (columns != null) {
            for (int i = cells.size(); i < columns.size(); i++) {
                values.put(columns.get(i), "");
            }
            if (cells.size() > columns.size()) {
                // More cells than the header names: a delimiter inside a value that was not quoted, and every value
                // after it has moved one column along. Nothing in such a row can be trusted where it stands.
                broken = true;
            } else if (cells.size() < columns.size()) {
                problems.add(new Problem(RuleCode.ROW_SHORT, null));
            }
        }
        if (broken) {
            problems.add(0, new Problem(RuleCode.ROW_MALFORMED, null));
        }
        if (values.values().stream().anyMatch(value -> value.indexOf('�') >= 0)) {
            problems.add(new Problem(RuleCode.ENCODING_SUSPECT, null));
        }
        rows++;
        if (rows > limits.maxRows()) {
            throw new SourceRefused(SourceRefused.Reason.TOO_MANY_ROWS, limits.maxRows(),
                    "The file has more rows than the limit.");
        }
        sink.accept(new SourceRow(rowNumber, values, problems));
    }

    private static boolean blank(List<String> cells) {
        for (String cell : cells) {
            if (!cell.isBlank()) {
                return false;
            }
        }
        return true;
    }
}

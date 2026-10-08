package za.co.titandynamix.sasbo_online_backend.legacy.read;

import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One data row as read: its number in the file (the line a person opens the file at, or the sheet's row number), its
 * values by column in the file's order, and what was wrong with reading it. Its {@code toString} names the number and
 * nothing it holds.
 */
public record SourceRow(int rowNumber, Map<String, String> values, List<Problem> problems) {

    public SourceRow {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        problems = List.copyOf(problems);
    }

    @Override
    public String toString() {
        return "SourceRow[" + rowNumber + ", " + values.size() + " values, " + problems.size() + " problems]";
    }
}

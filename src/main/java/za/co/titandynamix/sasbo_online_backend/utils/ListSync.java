package za.co.titandynamix.sasbo_online_backend.utils;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How month end's seeders write a list (01.1, 01.2, MD44): BY JDBC BATCH, and deciding what is absent by ONE QUERY of
 * the keys on record -- never {@code saveAll}, which this codebase's {@code IDENTITY} ids turn into a round trip a row
 * (MR-PERF-7), and never a question a row.
 *
 * <p>A list is brought up to the resource's rows, keyed by one column: a row that is absent is inserted, a row whose
 * values differ is updated -- the seeder owns a list until SASBO confirms it (MR-ROB-20) -- and a row the resource no
 * longer has is left where it is: nothing is ever deleted. A second start finds every key on record with its values,
 * and writes nothing.
 *
 * <p>Every statement here is one both databases accept (BR65), and every table and column name is written by the
 * seeders themselves: nothing a request or a resource says reaches the text of a statement.
 */
final class ListSync {

    private final JdbcTemplate jdbc;
    private final Runnable beforeWrite;

    ListSync(JdbcTemplate jdbc, Runnable beforeWrite) {
        this.jdbc = jdbc;
        this.beforeWrite = beforeWrite;
    }

    /** What one list's sync did. */
    record Outcome(int inserted, int updated, Map<Object, Long> ids) {
        boolean wrote() {
            return inserted + updated > 0;
        }
    }

    /**
     * Brings {@code table}'s rows up to {@code desired}, keyed by {@code key}.
     *
     * @param desired each row as {key, column values in the order of {@code columns}}
     * @param where   a fixed predicate narrowing the rows the list owns, or {@code null}; written by the caller
     * @param fixed   a fixed column and value written on every inserted row and part of {@code where}, or {@code null}
     */
    Outcome sync(String table, String key, List<String> columns, List<Object[]> desired, String where,
                 Object[] fixed) {
        Map<Object, Object[]> onRecord = new HashMap<>();
        Map<Object, Long> ids = new HashMap<>();
        String select = "SELECT id, " + key + (columns.isEmpty() ? "" : ", " + String.join(", ", columns))
                + " FROM " + table + (where == null ? "" : " WHERE " + where);
        jdbc.query(select, rs -> {
            Object keyValue = normal(rs.getObject(2));
            Object[] values = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                values[i] = normal(rs.getObject(i + 3));
            }
            onRecord.put(keyValue, values);
            ids.put(keyValue, rs.getLong(1));
        });
        beforeWrite.run();
        List<Object[]> inserts = new ArrayList<>();
        List<Object[]> updates = new ArrayList<>();
        for (Object[] row : desired) {
            Object keyValue = normal(row[0]);
            Object[] values = Arrays.copyOfRange(row, 1, row.length);
            Object[] existing = onRecord.get(keyValue);
            if (existing == null) {
                inserts.add(fixed == null ? row : concat(row, fixed[1]));
            } else if (!same(existing, values)) {
                Object[] update = new Object[values.length + 1];
                System.arraycopy(values, 0, update, 0, values.length);
                update[values.length] = row[0];
                updates.add(update);
            }
        }
        if (!inserts.isEmpty()) {
            List<String> inserted = new ArrayList<>();
            inserted.add(key);
            inserted.addAll(columns);
            if (fixed != null) {
                inserted.add((String) fixed[0]);
            }
            jdbc.batchUpdate("INSERT INTO " + table + " (" + String.join(", ", inserted) + ") VALUES ("
                    + String.join(", ", java.util.Collections.nCopies(inserted.size(), "?")) + ")", inserts);
        }
        if (!updates.isEmpty()) {
            jdbc.batchUpdate("UPDATE " + table + " SET "
                    + String.join(", ", columns.stream().map(c -> c + " = ?").toList())
                    + " WHERE " + key + " = ?" + (where == null ? "" : " AND " + where), updates);
        }
        if (!inserts.isEmpty()) {
            ids.clear();
            jdbc.query("SELECT id, " + key + " FROM " + table + (where == null ? "" : " WHERE " + where),
                    rs -> {
                        ids.put(normal(rs.getObject(2)), rs.getLong(1));
                    });
        }
        return new Outcome(inserts.size(), updates.size(), ids);
    }

    /** The ids of a table's rows by their key -- for a list whose rows this start did not write. */
    Map<Object, Long> ids(String table, String key, String where) {
        Map<Object, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, " + key + " FROM " + table + (where == null ? "" : " WHERE " + where),
                rs -> {
                    ids.put(normal(rs.getObject(2)), rs.getLong(1));
                });
        return ids;
    }

    /** Is every key of {@code desired} on record now -- written, perhaps, by another start at the same moment? */
    boolean allOnRecord(String table, String key, List<Object[]> desired, String where) {
        Map<Object, Long> ids = ids(table, key, where);
        return desired.stream().allMatch(row -> ids.containsKey(normal(row[0])));
    }

    /**
     * Records what happened to a list beside it (ME-18): {@code SEEDED} with how many rows, or {@code FAILED} with one
     * of four words -- never an exception's name and never a row's content (MR-SEC-21).
     */
    void record(String list, String report, LocalDate printedOn, String outcome, String failure, Integer rows,
                Instant at) {
        Object printed = printedOn == null ? null : java.sql.Date.valueOf(printedOn);
        int updated = jdbc.update("UPDATE reference_lists SET report = ?, printed_on = ?, outcome = ?, failure = ?, "
                + "row_count = ?, recorded_at = ? WHERE list_key = ?",
                report, printed, outcome, failure, rows, Timestamp.from(at), list);
        if (updated == 0) {
            jdbc.update("INSERT INTO reference_lists (list_key, report, printed_on, outcome, failure, row_count, "
                    + "recorded_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    list, report, printed, outcome, failure, rows, Timestamp.from(at));
        }
    }

    /** A value as it is compared: every whole number as a {@code Long}, a date as its ISO text, anything else as is. */
    static Object normal(Object value) {
        if (value instanceof Number number && !(value instanceof java.math.BigDecimal)) {
            return number.longValue();
        }
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal.stripTrailingZeros().scale() <= 0 ? decimal.longValue() : decimal;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        return value;
    }

    private static boolean same(Object[] onRecord, Object[] desired) {
        if (onRecord.length != desired.length) {
            return false;
        }
        for (int i = 0; i < onRecord.length; i++) {
            if (!Objects.equals(normal(onRecord[i]), normal(desired[i]))) {
                return false;
            }
        }
        return true;
    }

    private static Object[] concat(Object[] row, Object extra) {
        Object[] longer = Arrays.copyOf(row, row.length + 1);
        longer[row.length] = extra;
        return longer;
    }
}

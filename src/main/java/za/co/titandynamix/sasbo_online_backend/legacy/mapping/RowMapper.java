package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Applies a mapping to one row: finds each field's column, applies its transforms in order, gives a blank its default,
 * turns a code through its list and a date into ISO. Holds a cache of which key each column resolved to -- every row of
 * one file has the same columns -- so one instance serves one file, on one thread.
 *
 * <p>A column is found by its exact name, then by its name ignoring case and spacing, then -- written {@code #3} -- by
 * its position. A column the file does not have gives a blank, and the rules say what a blank means for that field.
 */
public final class RowMapper {

    private static final Pattern POSITION = Pattern.compile("#([1-9][0-9]{0,2})");

    private final MappingDefinition mapping;
    private final Map<String, String> resolved = new HashMap<>();

    public RowMapper(MappingDefinition mapping) {
        this.mapping = mapping;
    }

    public MappedRow map(Map<String, String> raw) {
        Map<MemberField, String> values = new EnumMap<>(MemberField.class);
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<MemberField, FieldMapping> entry : mapping.fields().entrySet()) {
            MemberField field = entry.getKey();
            FieldMapping spec = entry.getValue();
            String value = spec.column() == null ? null : column(raw, spec.column());
            for (Transform transform : spec.transforms()) {
                value = transform.apply(value);
            }
            if (value == null || value.isBlank()) {
                value = spec.defaultValue();
            }
            if (value == null || value.isBlank()) {
                continue;
            }
            value = value.strip();
            if (spec.codes() != null) {
                String ours = mapping.lookUp(spec.codes(), value);
                if (ours == null) {
                    problems.add(new Problem(field == MemberField.STATUS ? RuleCode.STATUS_UNKNOWN : RuleCode.CODE_UNKNOWN,
                            field.wire()));
                    continue;
                }
                value = ours;
            }
            if (field.isDate()) {
                LocalDate date = date(value, spec.dateFormats());
                if (date == null) {
                    problems.add(new Problem(RuleCode.DATE_UNREADABLE, field.wire()));
                    continue;
                }
                value = date.toString();
            }
            values.put(field, value);
        }
        return new MappedRow(values, problems);
    }

    /** The value of a column, by the rules above; {@code null} where the row has no such column. */
    private String column(Map<String, String> raw, String column) {
        String key = resolved.get(column);
        if (key != null && raw.containsKey(key)) {
            return raw.get(key);
        }
        key = resolve(raw, column);
        if (key != null) {
            resolved.put(column, key);
            return raw.get(key);
        }
        return null;
    }

    private static String resolve(Map<String, String> raw, String column) {
        if (raw.containsKey(column)) {
            return column;
        }
        String wanted = normalise(column);
        for (String key : raw.keySet()) {
            if (normalise(key).equals(wanted)) {
                return key;
            }
        }
        Matcher position = POSITION.matcher(column);
        if (position.matches()) {
            int index = Integer.parseInt(position.group(1));
            Iterator<String> keys = raw.keySet().iterator();
            for (int i = 1; keys.hasNext(); i++) {
                String key = keys.next();
                if (i == index) {
                    return key;
                }
            }
        }
        return null;
    }

    private static String normalise(String name) {
        return name.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static LocalDate date(String value, List<DateTimeFormatter> formats) {
        for (DateTimeFormatter format : formats) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException tryTheNext) {
                // the next format
            }
        }
        return null;
    }
}

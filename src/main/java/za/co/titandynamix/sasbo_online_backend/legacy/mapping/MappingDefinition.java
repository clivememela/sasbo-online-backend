package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import za.co.titandynamix.sasbo_online_backend.legacy.read.ReaderSettings;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.RuleCode;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.Severity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A mapping, parsed and checked: how a file is read ({@link #format()}), which column each member field comes from and
 * what is done to it, the lists that turn the old system's codes into ours, the severities SASBO has chosen, and how
 * membership numbers are kept. Built only by {@link MappingParser}; {@link #json()} is the definition as stored, which
 * a batch keeps as its snapshot so that its report reads the same later.
 *
 * @param codes each list's entries by the old value, upper-cased and trimmed -- a lookup ignores case and spacing
 */
public record MappingDefinition(String key, String name, String description, ReaderSettings format,
                                Map<MemberField, FieldMapping> fields, Map<String, Map<String, String>> codes,
                                Map<RuleCode, Severity> severity, MembershipNumberRule membershipNumber, String json) {

    /** The reserved list that says which leaving status each deletion code gives a member; {@code *} is the rest. */
    public static final String DELETION_STATUS_LIST = "deletionStatus";

    public MappingDefinition {
        EnumMap<MemberField, FieldMapping> byField = new EnumMap<>(MemberField.class);
        byField.putAll(fields);
        fields = Collections.unmodifiableMap(byField);
        Map<String, Map<String, String>> lists = new LinkedHashMap<>();
        codes.forEach((list, entries) -> lists.put(list, Map.copyOf(entries)));
        codes = Collections.unmodifiableMap(lists);
        EnumMap<RuleCode, Severity> chosen = new EnumMap<>(RuleCode.class);
        chosen.putAll(severity);
        severity = Collections.unmodifiableMap(chosen);
    }

    /** A code's severity under this mapping: SASBO's choice where the mapping makes one, the default otherwise. */
    public Severity severityOf(RuleCode code) {
        return severity.getOrDefault(code, code.severity());
    }

    /** The value a list gives an old value, ignoring case and spacing; {@code null} where the list has none. */
    public String lookUp(String list, String value) {
        Map<String, String> entries = codes.get(list);
        if (entries == null || value == null) {
            return null;
        }
        return entries.get(keyOf(value));
    }

    /** How a list's keys are held: trimmed, inner spacing collapsed, upper-cased. */
    public static String keyOf(String value) {
        return value.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return "MappingDefinition[" + key + ", " + format.format() + ", " + fields.size() + " fields]";
    }
}

package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import za.co.titandynamix.sasbo_online_backend.legacy.rules.Problem;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A row's member fields as its mapping reads them -- transformed, defaulted, each code through its list, each date as
 * ISO -- and the problems met doing so. A value a list does not know, or a date no format reads, is absent here and
 * named in the problems: never silently dropped, never guessed at. Its {@code toString} names no value.
 */
public record MappedRow(Map<MemberField, String> values, List<Problem> problems) {

    public MappedRow {
        EnumMap<MemberField, String> copied = new EnumMap<>(MemberField.class);
        copied.putAll(values);
        values = Collections.unmodifiableMap(copied);
        problems = List.copyOf(problems);
    }

    @Override
    public String toString() {
        return "MappedRow[" + values.size() + " values, " + problems.size() + " problems]";
    }
}

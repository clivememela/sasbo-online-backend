package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * How one member field is read from a row.
 *
 * @param column       the column it comes from: a header's name (matched without regard to case or spacing), a position
 *                     ({@code #3}), or a fixed-width layout's field; {@code null} where only a default is given
 * @param transforms   applied in order, before anything is checked
 * @param datePatterns a date field's formats, tried in order (each with a four-digit year)
 * @param dateFormats  the same, compiled strict: 31 February is unreadable, never 3 March
 * @param codes        the mapping's list that turns the old system's values into ours, or {@code null}
 * @param defaultValue what a blank value becomes, or {@code null}
 */
public record FieldMapping(String column, List<Transform> transforms, List<String> datePatterns,
                           List<DateTimeFormatter> dateFormats, String codes, String defaultValue) {

    public FieldMapping {
        transforms = transforms == null ? List.of() : List.copyOf(transforms);
        datePatterns = datePatterns == null ? List.of() : List.copyOf(datePatterns);
        dateFormats = dateFormats == null ? List.of() : List.copyOf(dateFormats);
    }
}

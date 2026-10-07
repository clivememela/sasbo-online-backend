package za.co.titandynamix.sasbo_online_backend.legacy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import za.co.titandynamix.sasbo_online_backend.legacy.mapping.MemberField;
import za.co.titandynamix.sasbo_online_backend.legacy.rules.CleanRow;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A staged row's two JSON forms: the RAW values as the file had them, by column, and the NORMALISED member a move would
 * write -- its fields by their names, and beside them what the move needs that is not a field (the status as the record
 * spells it, the identity type, the workplace's id, SASBO's codes' ids, the day a status changed). A move reads only
 * this; it never runs the rules again.
 */
public final class StagedJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<LinkedHashMap<String, String>> RAW = new TypeReference<>() {
    };

    /** The member a move writes, as read back from a row. Its {@code toString} names no value. */
    public record Normalised(Map<MemberField, String> fields, String memberStatus, String statusKind, String identityType,
                             Long workplaceId, Long deletionCodeId, Long suspensionCodeId, LocalDate statusChangedOn) {

        @Override
        public String toString() {
            return "Normalised[" + memberStatus + ", " + fields.size() + " fields]";
        }
    }

    /** The normalised member as stored: every field by name, and the facts beside them under names no field has. */
    record Stored(Map<String, String> fields, String memberStatus, String statusKind, String identityType,
                          Long workplaceId, Long deletionCodeId, Long suspensionCodeId, String statusChangedOn) {
    }

    private StagedJson() {
    }

    public static String raw(Map<String, String> values) {
        return write(values);
    }

    public static Map<String, String> raw(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, RAW);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("a staged row's values could not be read back");
        }
    }

    public static String normalised(CleanRow row) {
        Map<String, String> fields = new LinkedHashMap<>();
        row.values().forEach((field, value) -> fields.put(field.wire(), value));
        return write(new Stored(fields, row.memberStatus(), row.statusKind(), row.identityType(), row.workplaceId(),
                row.deletionCodeId(), row.suspensionCodeId(),
                row.statusChangedOn() == null ? null : row.statusChangedOn().toString()));
    }

    public static Normalised normalised(String json) {
        Stored stored;
        try {
            stored = JSON.readValue(json, Stored.class);
        } catch (JsonProcessingException unreadable) {
            throw new IllegalStateException("a staged row's normalised fields could not be read back");
        }
        Map<MemberField, String> fields = new EnumMap<>(MemberField.class);
        if (stored.fields() != null) {
            stored.fields().forEach((wire, value) -> MemberField.fromWire(wire).ifPresent(field -> fields.put(field, value)));
        }
        return new Normalised(fields, stored.memberStatus(), stored.statusKind(), stored.identityType(),
                stored.workplaceId(), stored.deletionCodeId(), stored.suspensionCodeId(),
                stored.statusChangedOn() == null ? null : LocalDate.parse(stored.statusChangedOn()));
    }

    private static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException unwritable) {
            throw new IllegalStateException("a staged row could not be written as JSON");
        }
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A recipient of a region's pack, as saved: {@code code} on a create (the path names it on an update, and the body's is
 * ignored); {@code version} on an update -- the version the screen read, so a save never overwrites one it has not seen.
 * Each field is checked by the service, which names the field it refuses (PACK_SET_INVALID).
 */
public record MonthEndPackSetRequest(String code, String label, String regionCode, List<String> areaLetters,
                                     String position, String email, Boolean active, Integer version) {
}

package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A recipient of a region's month-end pack (October build; Stephne MEN-02, MEN-08): a region, the areas of it the set
 * covers where the region is split -- empty for the whole region -- the POSITION it goes to, and that position's work
 * address, or none, when the set can only be downloaded for. Dates are ISO text (MD16).
 */
public record MonthEndPackSetDto(String code, String label, String regionCode, String regionName,
                                 List<String> areaLetters, String position, String email, boolean active, int version,
                                 String updatedBy, String updatedAt) {
}

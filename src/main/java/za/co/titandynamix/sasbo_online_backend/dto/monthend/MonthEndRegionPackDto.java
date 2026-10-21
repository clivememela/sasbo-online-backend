package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A region's pack, produced from a taken month (October build): the set it was made for as the set stood then, what it
 * holds, and its two files -- each fetched at {@code /api/month-end/region-packs/{id}/pdf} or {@code .../xlsx}. Dates are
 * ISO text (MD16).
 */
public record MonthEndRegionPackDto(long id, String month, int takeNumber, String setCode, String setLabel,
                                    String regionCode, List<String> areaLetters, String position, String producedAt,
                                    String producedBy, Counts counts, List<PackFile> files) {

    /** The members the pack names: the month's new, its deleted, and the suspended at its end. */
    public record Counts(int joined, int left, int suspended) {
    }

    /** One of the pack's two files. {@code format}: {@code PDF} or {@code XLSX}. */
    public record PackFile(String format, String name, long bytes, String sha256) {
    }
}

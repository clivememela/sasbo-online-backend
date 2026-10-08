package za.co.titandynamix.sasbo_online_backend.legacy.rules;

import java.util.Map;
import java.util.Set;

/**
 * SASBO's lists as the rules look them up, loaded once for a whole check: the workplaces by R/A number (5,507 on the
 * branch list -- small enough to hold), the table codes, the area letters, and SASBO's deletion and suspension codes by
 * their numbers, each to the id it has on record here.
 */
public record ReferenceIndex(Map<Integer, Workplace> workplaces, Set<Integer> tableCodes, Set<String> areaLetters,
                             Map<Integer, Long> deletionCodes, Map<Integer, Long> suspensionCodes) {

    /** A workplace as the rules need it: its id, its area's letter, its table code where the files prove one. */
    public record Workplace(long id, String areaLetter, Integer tableCode) {
    }

    public ReferenceIndex {
        workplaces = Map.copyOf(workplaces);
        tableCodes = Set.copyOf(tableCodes);
        areaLetters = Set.copyOf(areaLetters);
        deletionCodes = Map.copyOf(deletionCodes);
        suspensionCodes = Map.copyOf(suspensionCodes);
    }
}

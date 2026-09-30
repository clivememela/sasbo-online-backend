package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Why one of SASBO's lists -- or the workplaces -- has nothing to answer here, so that a screen says so instead of
 * showing an empty control (month end 01.1, ME-19). Published in {@code api-vocabulary.json}; the browser holds a
 * sentence for each.
 */
public enum ListEmptyReason {
    /** Where the production marker is set, a list read off a report waits for SASBO to confirm it (MD28). */
    NOT_CONFIRMED,
    /** Not loaded on this demonstration yet: never seeded, or -- for the workplaces -- the switch is off (MD45). */
    NOT_LOADED,
    /** It could not be loaded; the status read says how, in one of four words (MR-SEC-21). */
    FAILED,
    /** It was loaded, and nothing is on it. */
    EMPTY;

    /**
     * The one rule, for every list: nothing is said of a list that has rows; a failure is said before anything else;
     * then a production list SASBO has not confirmed; then a list the seeder loaded that holds nothing; else it was
     * never loaded here.
     */
    public static ListEmptyReason of(long rows, String outcome, boolean confirmed, boolean production) {
        if (rows > 0) {
            return null;
        }
        if ("FAILED".equals(outcome)) {
            return FAILED;
        }
        if (production && !confirmed) {
            return NOT_CONFIRMED;
        }
        return "SEEDED".equals(outcome) ? EMPTY : NOT_LOADED;
    }
}

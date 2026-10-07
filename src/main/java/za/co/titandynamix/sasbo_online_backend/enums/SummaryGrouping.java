package za.co.titandynamix.sasbo_online_backend.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * The groupings the figures take (02.2, MD31) -- each THROUGH THE MEMBER'S WORKPLACE (rule G): its table code, and
 * through the table code its employer and its employer's group, and the core tier the code stands in; its area, its
 * area's region, its committee. The wire name is what a request sends as {@code by}; anything else is
 * {@code SUMMARY_GROUPING_UNKNOWN}.
 */
public enum SummaryGrouping {
    GROUP("group", true, false),
    EMPLOYER("employer", true, true),
    TABLE_CODE("tableCode", true, true),
    AREA("area", false, false),
    REGION("region", false, false),
    COMMITTEE("committee", false, false),
    /**
     * October build, Stephne MEN-31: Core A, B and C, and all other institutions. Guarded as an employer is: a tier is
     * a handful of employers, and a tier of one small employer would print that employer's membership.
     */
    TIER("tier", true, true);

    private final String wire;
    private final boolean throughTableCode;
    private final boolean namesAnEmployer;

    SummaryGrouping(String wire, boolean throughTableCode, boolean namesAnEmployer) {
        this.wire = wire;
        this.throughTableCode = throughTableCode;
        this.namesAnEmployer = namesAnEmployer;
    }

    /** What a request sends, and the vocabulary publishes. */
    public String wire() {
        return wire;
    }

    /** Whether a member reaches its row through the workplace's table code -- so a workplace with none is a row. */
    public boolean throughTableCode() {
        return throughTableCode;
    }

    /**
     * Whether a row names an employer or a table code, so that a membership from one to four is answered without its
     * number (rule C, MD37). A group, an area, a region and a committee are not guarded: they are not an employer.
     */
    public boolean namesAnEmployer() {
        return namesAnEmployer;
    }

    /** The grouping a request names; empty for one the figures do not take. */
    public static Optional<SummaryGrouping> fromWire(String wire) {
        return Arrays.stream(values()).filter(g -> g.wire.equals(wire)).findFirst();
    }
}

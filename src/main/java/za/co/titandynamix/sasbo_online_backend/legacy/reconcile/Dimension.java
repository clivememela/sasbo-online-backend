package za.co.titandynamix.sasbo_online_backend.legacy.reconcile;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a month-end figure is cut by -- the shapes of SASBO's own reports (planning/branches/monthly-menu/legacy/
 * referenceData.md): the whole membership, then by table code ({@code mem_count}), employer and employer group
 * ({@code grlist}), area and region ({@code bank_master_summ}), suspension code ({@code mem_count} page 4) and deletion
 * code ({@code prt_del_summ}).
 */
public enum Dimension {
    TOTAL(EnumSet.allOf(Measure.class)),
    TABLE_CODE(EnumSet.allOf(Measure.class)),
    EMPLOYER(EnumSet.allOf(Measure.class)),
    EMPLOYER_GROUP(EnumSet.allOf(Measure.class)),
    AREA(EnumSet.allOf(Measure.class)),
    REGION(EnumSet.allOf(Measure.class)),
    SUSPENSION_CODE(EnumSet.of(Measure.SUSPENDED)),
    DELETION_CODE(EnumSet.of(Measure.LEFT));

    private final Set<Measure> measures;

    Dimension(Set<Measure> measures) {
        this.measures = measures;
    }

    /** The measures a figure of this dimension may count: a suspension code counts the suspended, and nothing else. */
    public Set<Measure> measures() {
        return measures;
    }

    /** Whether a key of this dimension is a number -- compared as one, so {@code 013} is table code 13. */
    public boolean numericKey() {
        return this == TABLE_CODE || this == SUSPENSION_CODE || this == DELETION_CODE;
    }
}

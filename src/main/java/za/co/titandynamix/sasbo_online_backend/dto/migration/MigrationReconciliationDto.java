package za.co.titandynamix.sasbo_online_backend.dto.migration;

import java.util.List;
import java.util.Map;

/**
 * A batch set beside the old system's figures for the month it represents: each line a figure -- the old system's, the
 * batch's (the rows that move), the rows of that key held back by errors, the difference -- and its status. Proved when
 * at least one figure is entered and every one is given exactly. Counts only.
 */
public record MigrationReconciliationDto(long batchId, String month, String batchStatus, int figuresEntered,
                                         long rowsHeldBack, boolean proved, Map<String, Long> summary,
                                         List<Line> lines) {

    /** One figure compared. {@code legacy} and {@code difference} are {@code null} where the pack printed no figure. */
    public record Line(String dimension, String key, String measure, Long legacy, long batch, long heldBack,
                       Long difference, String status) {
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;
import java.util.Map;

/**
 * A bank's debit day move: previewed, or done (DOD-19).
 *
 * @param toMove       debit-order members of the bank not on the new day -- the ones a move changes
 * @param alreadyOnDay those on it already, left as they are
 * @param byCurrentDay how many are on each day now, by day
 * @param liveRuns     runs already prepared and not yet sent: they keep the days they were prepared with, so the
 *                     screen says to cancel and prepare again if the new day must reach them
 * @param moved        how many the move changed; null on a preview
 */
public record AcbDebitDayMoveDto(Integer bankId, String bankName, int newDay, int toMove, int alreadyOnDay,
                                 Map<String, Integer> byCurrentDay, List<LiveRun> liveRuns, Integer moved) {

    /** A run not yet sent, with the days it was prepared with. */
    public record LiveRun(Long runId, String actionMonth, String status) {
    }
}

package za.co.titandynamix.sasbo_online_backend.enums;

import java.util.Map;
import java.util.Optional;

import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.APPROVED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.CANCELLED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.CLOSED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.FAILED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.PREPARED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.PREPARING;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.RETURNED;
import static za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus.SUBMITTED;

/**
 * Everything that can happen to a run, and the ONE table saying from which state to which.
 *
 * <h2>Why the transitions are a table and not a chain of ifs</h2>
 * A state machine spread over service methods answers "can I cancel an approved run?" differently
 * depending on which method you read. Here each action names the states it moves and where to;
 * anything not in its map is refused with {@code RUN_NOT_IN_STATE}. The test enumerates every
 * (state, action) pair from the two enums, so a new state or action with no decision fails it.
 */
public enum AcbRunAction {
    /** The preparation job finished and wrote every line. */
    COMPLETE_PREPARATION(Map.of(PREPARING, PREPARED)),
    /** The preparation job died, or was found dead by the sweep. */
    FAIL_PREPARATION(Map.of(PREPARING, FAILED)),
    /** Authorises the month's debits. Refused to every caller until the API authenticates. */
    APPROVE(Map.of(PREPARED, APPROVED)),
    /** Records that the CFO uploaded the file at the bank -- the system submits nothing. */
    RECORD_SUBMITTED(Map.of(APPROVED, SUBMITTED)),
    /** Records that the bank's returns for the run have been taken in. */
    RECORD_RETURNS(Map.of(SUBMITTED, RETURNED)),
    /** The month is finished. */
    CLOSE(Map.of(RETURNED, CLOSED)),
    /** The undo for a proposal. A submitted run is never cancelled: its unpaids are the record. */
    CANCEL(Map.of(PREPARED, CANCELLED, APPROVED, CANCELLED));

    private final Map<AcbRunStatus, AcbRunStatus> moves;

    AcbRunAction(Map<AcbRunStatus, AcbRunStatus> moves) {
        this.moves = moves;
    }

    /** Where this action takes a run in {@code from}, or empty when it may not act on it. */
    public Optional<AcbRunStatus> from(AcbRunStatus from) {
        return Optional.ofNullable(moves.get(from));
    }
}

package za.co.titandynamix.sasbo_online_backend.enums;

/**
 * Where a debit-order run is in its month.
 *
 * <pre>
 *   PREPARING ─▶ PREPARED ─▶ APPROVED ─▶ SUBMITTED ─▶ RETURNED ─▶ CLOSED
 *       │            │           │
 *       ▼            └───────────┴──▶ CANCELLED
 *     FAILED
 * </pre>
 *
 * <p>{@code SUBMITTED} is RECORDED, never performed: the CFO uploads the file at the collecting bank
 * herself, and the system is told she did. {@code FAILED} and {@code CANCELLED} are terminal and
 * release their month, so the month can be prepared again as a new run while the dead one stays on
 * record. A run is never deleted.
 *
 * <p>Which action may move which state is {@link AcbRunAction}'s table, not this enum's.
 */
public enum AcbRunStatus {
    PREPARING,
    PREPARED,
    APPROVED,
    SUBMITTED,
    RETURNED,
    CLOSED,
    FAILED,
    CANCELLED;

    /**
     * Whether this run holds its month. The same predicate as the database's partial unique index
     * ({@code WHERE status NOT IN ('FAILED', 'CANCELLED')}) -- the two must agree, and a test says so.
     */
    public boolean holdsItsMonth() {
        return this != FAILED && this != CANCELLED;
    }

    /** Whether the run's lines are the record of what was sent, and may never change again. */
    public boolean linesAreFinal() {
        return this == SUBMITTED || this == RETURNED || this == CLOSED;
    }
}

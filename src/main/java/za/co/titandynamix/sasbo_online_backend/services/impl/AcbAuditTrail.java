package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbAuditEvent;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunlessEvent;
import za.co.titandynamix.sasbo_online_backend.repository.AcbAuditEventRepository;

import java.time.Clock;

/**
 * Writes one row of a run's audit trail.
 *
 * <p>It joins whatever transaction is open, and every caller calls it inside the transaction that
 * makes the change it records -- so a change never commits without its row, and a row never
 * outlives a change that rolled back. It does not open a transaction of its own, deliberately.
 */
@Service
public class AcbAuditTrail {

    /** The column's width. A reason is written by ACB code, so it is short; this is the net. */
    private static final int REASON_MAX = 500;

    private final AcbAuditEventRepository events;
    private final Clock clock;

    public AcbAuditTrail(AcbAuditEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    /**
     * @param reason written by ACB for a person to read. 🔒 Never an exception's message, and never
     *               a member's account or identity number.
     */
    public void record(Long runId, Long lineId, String kind, AcbRunStatus from, AcbRunStatus to,
                       String reason, String actor) {
        AcbAuditEvent event = new AcbAuditEvent();
        event.setRunId(runId);
        event.setLineId(lineId);
        event.setOccurredAt(AcbTime.instant(clock));
        event.setActor(actor);
        event.setKind(kind);
        event.setFromState(from == null ? null : from.name());
        event.setToState(to == null ? null : to.name());
        event.setReason(reason == null || reason.length() <= REASON_MAX
                ? reason : reason.substring(0, REASON_MAX));
        events.save(event);
    }

    /**
     * A write that belongs to no run -- a rate, a holiday, the calendar's extent, the opening
     * position, the seed's completion (SD4-A). The same rule as {@link #record}: inside the
     * transaction that makes the change, so the change and its row commit or roll back together.
     *
     * @param reason what changed, for a person to read. 🔒 Never a member's number.
     */
    public void recordRunless(AcbRunlessEvent kind, String reason, String actor) {
        record(null, null, kind.name(), null, null, reason, actor);
    }
}

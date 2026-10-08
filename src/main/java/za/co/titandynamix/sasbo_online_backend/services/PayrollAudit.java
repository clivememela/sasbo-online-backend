package za.co.titandynamix.sasbo_online_backend.services;

import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEvent;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;
import za.co.titandynamix.sasbo_online_backend.repository.PayrollEventRepository;

import java.time.Clock;

/**
 * Writes one entry of the payroll module's record of what was done. It joins the caller's transaction, so an entry
 * never outlives a change that rolled back, and a change never commits without its entry.
 *
 * <p>🔒 A summary is written by the payroll code from counts, codes and months -- never a staff number, an ID number,
 * a name, a note's words or a password.
 */
@Service
public class PayrollAudit {

    private static final int SUMMARY_MAX = 500;

    private final PayrollEventRepository events;
    private final Clock clock;

    public PayrollAudit(PayrollEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    public void record(PayrollEventKind kind, Long fileId, Long discrepancyId, String summary, String actor) {
        PayrollEvent event = new PayrollEvent();
        event.setOccurredAt(clock.instant());
        event.setActor(actor);
        event.setKind(kind);
        event.setFileId(fileId);
        event.setDiscrepancyId(discrepancyId);
        event.setSummary(summary.length() <= SUMMARY_MAX ? summary : summary.substring(0, SUMMARY_MAX));
        events.save(event);
    }
}

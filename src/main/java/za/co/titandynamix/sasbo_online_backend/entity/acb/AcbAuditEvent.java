package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One thing that happened to a run or a line: who, when, from which state to which, and why.
 * Written in the same transaction as the change it records.
 */
@Entity
@Table(name = "acb_audit_events")
@Getter
@Setter
@NoArgsConstructor
public class AcbAuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** NULL only for an {@code AcbRunlessEvent} -- the table's own CHECK refuses any other kind without a run (SD4-A). */
    @Column(name = "run_id")
    private Long runId;

    @Column(name = "line_id")
    private Long lineId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** {@code system} until the API knows who is calling. */
    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    /** What happened -- an action's name, or a refusal worth keeping. */
    @Column(name = "kind", nullable = false, length = 40)
    private String kind;

    @Column(name = "from_state", length = 20)
    private String fromState;

    @Column(name = "to_state", length = 20)
    private String toState;

    @Column(name = "reason", length = 500)
    private String reason;
}

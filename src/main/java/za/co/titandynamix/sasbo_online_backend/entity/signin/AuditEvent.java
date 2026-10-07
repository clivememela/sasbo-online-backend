package za.co.titandynamix.sasbo_online_backend.entity.signin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/**
 * One line of the record of who did what (V67). Append-only: written once, never changed -- Postgres refuses an
 * update or a delete of a row ({@code audit_events_append_only}), and Hibernate is told the same. Who did it is kept as
 * they were then; the summary names fields and counts, never a member's values.
 */
@Entity
@Immutable
@Table(name = "audit_events")
@Getter
@NoArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", length = 60)
    private String username;

    @Column(name = "display_name", length = 80)
    private String displayName;

    @Column(name = "action", nullable = false, length = 60)
    private String action;

    @Column(name = "entity_type", length = 40)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    public AuditEvent(Instant at, Long userId, String username, String displayName, String action, String entityType,
                      String entityId, String summary) {
        this.at = at;
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "AuditEvent[" + id + ", " + action + "]";
    }
}

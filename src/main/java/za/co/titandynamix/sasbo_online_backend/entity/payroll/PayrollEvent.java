package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollEventKind;

import java.time.Instant;

/**
 * One entry of the payroll module's record of what was done -- written in the transaction that did it, append-only.
 * 🔒 {@code summary} carries counts and codes, never a person's details. V76.
 */
@Entity
@Table(name = "payroll_events")
@Getter
@Setter
@NoArgsConstructor
public class PayrollEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 100)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 30)
    private PayrollEventKind kind;

    @Column(name = "file_id")
    private Long fileId;

    @Column(name = "discrepancy_id")
    private Long discrepancyId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;
}

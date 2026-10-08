package za.co.titandynamix.sasbo_online_backend.entity.payroll;

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

/** A note on a payroll discrepancy: what an administrator did or learnt, kept as written (PAY-20). Append-only. V75. */
@Entity
@Table(name = "payroll_discrepancy_notes")
@Getter
@Setter
@NoArgsConstructor
public class PayrollDiscrepancyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "discrepancy_id", nullable = false)
    private Long discrepancyId;

    @Column(name = "note", nullable = false, length = 1000)
    private String note;

    @Column(name = "written_by", nullable = false, length = 100)
    private String writtenBy;

    @Column(name = "written_at", nullable = false)
    private Instant writtenAt;
}

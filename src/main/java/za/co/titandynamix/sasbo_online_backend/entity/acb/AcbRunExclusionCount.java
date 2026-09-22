package za.co.titandynamix.sasbo_online_backend.entity.acb;

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
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

/**
 * How many members one raw value kept out of a run's scope -- "Payroll Deduction: 48",
 * "Suspended: 27" -- for the reasons that are counted rather than listed.
 */
@Entity
@Table(name = "acb_run_exclusion_counts")
@Getter
@Setter
@NoArgsConstructor
public class AcbRunExclusionCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private AcbExclusionReason reason;

    /** The value as captured, or {@code (blank)}. */
    @Column(name = "detail", nullable = false)
    private String detail;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount;
}

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
 * A member a run could not debit, for a reason an administrator can fix -- stored with the run so
 * the report is the one the run was prepared from, not a fresh guess every time it is read.
 */
@Entity
@Table(name = "acb_run_exclusions")
@Getter
@Setter
@NoArgsConstructor
public class AcbRunExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private AcbExclusionReason reason;

    /** What made the rule fail -- the bank name as captured, the day, the account's length. */
    @Column(name = "detail")
    private String detail;
}

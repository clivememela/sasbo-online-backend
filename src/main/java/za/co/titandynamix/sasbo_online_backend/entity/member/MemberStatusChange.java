package za.co.titandynamix.sasbo_online_backend.entity.member;

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
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;

import java.time.Instant;

/**
 * One status change {@code MemberService.changeStatus} made, and why (ACB {@code 03.5}, V50). The member's
 * record keeps only its status; this is the history a count is taken from (Q25).
 */
@Entity
@Table(name = "member_status_changes")
@Getter
@Setter
@NoArgsConstructor
public class MemberStatusChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Column(name = "from_status")
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private MemberStatusReason reason;

    /** The run and line a suspension came from; {@code null} for a termination, which a report raises. */
    @Column(name = "run_id")
    private Long runId;

    @Column(name = "line_id")
    private Long lineId;

    @Column(name = "changed_by", nullable = false, length = 100)
    private String changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}

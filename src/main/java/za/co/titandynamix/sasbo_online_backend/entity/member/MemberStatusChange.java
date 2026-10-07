package za.co.titandynamix.sasbo_online_backend.entity.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;

import java.time.Instant;

/**
 * One status change {@code MemberService.changeStatus} made, and why (ACB {@code 03.5}, V50). The member's
 * record keeps only its status; this is the history a count is taken from (Q25).
 *
 * <p>The two indexes are V50's and V56's, named as there. Flyway builds them on every real database; they are written
 * here for the fast lane, whose H2 schema is built from the entities: without them H2 reads every change for every
 * member in the figures' statement (rule S's first change after an instant), and over the October demonstration
 * population that passed the statement's four-second bound (MV-16, 7 Oct). Hibernate validates no index, so nothing
 * changes where Flyway owns the schema.
 */
@Entity
@Table(name = "member_status_changes", indexes = {
        @Index(name = "member_status_changes_member", columnList = "member_id, changed_at"),
        @Index(name = "member_status_changes_changed_at", columnList = "changed_at")})
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

    /**
     * Month end 03.2 (V56): the deletion code a leaving carries -- the row of {@code deletion_codes} whose SASBO
     * number was named; set exactly when {@code reason} is {@code DELETION_CODE}, which the database checks.
     */
    @Column(name = "deletion_code_id")
    private Long deletionCodeId;

    /** Month end 03.2 (V56): the suspension code a suspension carries; set exactly when the reason is SUSPENSION_CODE. */
    @Column(name = "suspension_code_id")
    private Long suspensionCodeId;

    /** Month end 03.2 (V56): the administrator's words, at most 500 characters -- never logged (MR-SEC-6). */
    @Column(name = "note", length = 500)
    private String note;
}

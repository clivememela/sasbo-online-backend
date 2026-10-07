package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Something the comparison of a payroll file found, open until an administrator resolves it with an outcome and a note
 * (Stephne, PAY-20). V75.
 *
 * <p>{@code subjectKey} is what it is about -- a member, a line, the file -- so a re-run finds it again and its notes
 * and status survive. {@code detail} is written by the system from amounts and months, never from a person's details.
 */
@Entity
@Table(name = "payroll_discrepancies", uniqueConstraints = @UniqueConstraint(
        name = "payroll_discrepancies_once_per_subject", columnNames = {"file_id", "type", "subject_key"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollDiscrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "payroll_employer_id", nullable = false)
    private Long payrollEmployerId;

    @Column(name = "period", nullable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private PayrollDiscrepancyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private PayrollDiscrepancyStatus status;

    @Column(name = "member_id")
    private Integer memberId;

    /** The line it is about; set {@code null} when the lines are deleted after a year. */
    @Column(name = "row_id")
    private Long rowId;

    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "subject_key", nullable = false, length = 40)
    private String subjectKey;

    @Column(name = "amount_on_file", precision = 14, scale = 2)
    private BigDecimal amountOnFile;

    @Column(name = "expected_amount", precision = 14, scale = 2)
    private BigDecimal expectedAmount;

    @Column(name = "difference", precision = 14, scale = 2)
    private BigDecimal difference;

    @Column(name = "months")
    private Integer months;

    @Column(name = "detail", nullable = false, length = 300)
    private String detail;

    @Column(name = "query_id")
    private Long queryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 30)
    private PayrollOutcome outcome;

    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}

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
import java.time.LocalDate;

/**
 * A list of queries raised with an employer for a file: the discrepancies to add, delete or correct (Stephne, PAY-21:
 * "We send them the discrepancy queries - To add or delete."). Its lines are the discrepancies that name it. V75.
 */
@Entity
@Table(name = "payroll_queries")
@Getter
@Setter
@NoArgsConstructor
public class PayrollQuery {

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

    @Column(name = "raised_by", nullable = false, length = 100)
    private String raisedBy;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "add_count", nullable = false)
    private Integer addCount;

    @Column(name = "delete_count", nullable = false)
    private Integer deleteCount;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;
}

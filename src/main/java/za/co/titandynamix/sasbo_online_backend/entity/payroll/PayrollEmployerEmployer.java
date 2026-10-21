package za.co.titandynamix.sasbo_online_backend.entity.payroll;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One of SASBO's employers (V53's list) whose members a payroll employer's file covers -- FNB &amp; Wesbank send one
 * file for two (PAY-01). A member is the payroll employer's when their workplace's table code belongs to one of these.
 */
@Entity
@Table(name = "payroll_employer_employers", uniqueConstraints = @UniqueConstraint(
        name = "payroll_employer_employers_once", columnNames = {"payroll_employer_id", "employer_id"}))
@Getter
@Setter
@NoArgsConstructor
public class PayrollEmployerEmployer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payroll_employer_id", nullable = false)
    private Long payrollEmployerId;

    @Column(name = "employer_id", nullable = false)
    private Long employerId;
}

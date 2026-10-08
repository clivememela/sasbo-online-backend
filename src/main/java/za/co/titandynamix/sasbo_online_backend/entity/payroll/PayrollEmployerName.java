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

/**
 * An employer text a member's record may carry -- {@code members.bank_name}, "Bank Where Employed" -- that places them
 * with a payroll employer, upper-cased and trimmed. For the member who has no workplace on record yet.
 */
@Entity
@Table(name = "payroll_employer_names")
@Getter
@Setter
@NoArgsConstructor
public class PayrollEmployerName {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payroll_employer_id", nullable = false)
    private Long payrollEmployerId;

    @Column(name = "name_key", nullable = false, unique = true, length = 100)
    private String nameKey;
}

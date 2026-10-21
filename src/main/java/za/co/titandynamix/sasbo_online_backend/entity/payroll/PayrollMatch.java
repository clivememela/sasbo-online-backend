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
import za.co.titandynamix.sasbo_online_backend.enums.PayrollAmountStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollConfidence;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollMatchRule;

import java.math.BigDecimal;
import java.time.LocalDate;

/** What the comparison made of one line: the member it fits, by which rule, how surely, and its amount. V75. */
@Entity
@Table(name = "payroll_matches")
@Getter
@Setter
@NoArgsConstructor
public class PayrollMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "row_id", nullable = false, unique = true)
    private Long rowId;

    @Column(name = "member_id")
    private Integer memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_rule", nullable = false, length = 20)
    private PayrollMatchRule matchRule;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 10)
    private PayrollConfidence confidence;

    @Column(name = "reason", nullable = false, length = 200)
    private String reason;

    @Column(name = "expected_amount", precision = 12, scale = 2)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_status", length = 20)
    private PayrollAmountStatus amountStatus;

    @Column(name = "months")
    private Integer months;

    @Column(name = "rate_unknown_for")
    private LocalDate rateUnknownFor;
}

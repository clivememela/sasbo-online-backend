package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The flat debit-order subscription, from the first day of a month until the next row.
 *
 * <p>Everyone pays the same amount and it changes about once a year, so the rate is a dated row
 * and not a constant. A run charges the row in force for its action month; a line keeps the amount
 * it was charged, so a later change never reprices a month already prepared.
 */
@Entity
@Table(name = "acb_rates")
@Getter
@Setter
@NoArgsConstructor
public class AcbRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** The first day of the first month this amount is charged for. */
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /** Where the amount came from, in words -- this row is the answer to "why R174?". */
    @Column(name = "source", nullable = false)
    private String source;
}

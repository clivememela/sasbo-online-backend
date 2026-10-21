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

import java.time.Instant;
import java.time.LocalDate;

/**
 * A member's unpaid count, captured by hand (V72): "Unpaids for re-debits are captured manually a day before we run
 * ACB" (Stephne, DOD-28). The count as at {@code countedOn}, 0 to 3: it replaces the member's debits before that day,
 * and later ones count on from it. One row per member, replaced by the next capture.
 */
@Entity
@Table(name = "acb_unpaid_counts")
@Getter
@Setter
@NoArgsConstructor
public class AcbUnpaidCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false, unique = true)
    private Integer memberId;

    @Column(name = "membership_number", nullable = false, length = 20)
    private String membershipNumber;

    @Column(name = "unpaid_count", nullable = false)
    private Integer unpaidCount;

    @Column(name = "counted_on", nullable = false)
    private LocalDate countedOn;

    /** Where the count was read from -- "Re-debit list, October 2026". */
    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "entered_by", nullable = false, length = 100)
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;
}

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
 * One member's arrears as the old system left them, captured by hand at switch-over (V48, D24): how
 * many unpaids stand against them, and the last unpaid month. The termination rule counts from it.
 */
@Entity
@Table(name = "acb_opening_position")
@Getter
@Setter
@NoArgsConstructor
public class AcbOpeningPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Integer memberId;

    @Column(name = "membership_number", nullable = false, length = 20)
    private String membershipNumber;

    /** One or two: three would already have terminated the member. */
    @Column(name = "unpaids_standing", nullable = false)
    private Integer unpaidsStanding;

    /** The first day of the last unpaid month. */
    @Column(name = "last_unpaid_month", nullable = false)
    private LocalDate lastUnpaidMonth;

    @Column(name = "source", nullable = false, length = 100)
    private String source;

    @Column(name = "entered_by", nullable = false, length = 100)
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;
}

package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * What a taken month holds at a grain of its own (month end 04.2): the month's leavers by SASBO's deletion code, the
 * suspended by suspension code, the recruits by recruiter and channel, and the statuses nobody knows -- a count a
 * workplace, written inside the take's one snapshot beside {@link MonthEndWorkplaceFact}, so the two cannot disagree.
 *
 * <p>Written only by {@code MonthEndFactsRepository}'s statements; this entity exists so that H2's schema has the
 * table. Append-only on PostgreSQL by trigger (MD46).
 */
@Entity
@Table(name = "month_end_counts")
@Getter
@NoArgsConstructor
public class MonthEndCount {

    /** What a count counts. */
    public enum Measure {
        /** The month's leavers, by SASBO's deletion code -- and the reason under it, where one was given. */
        LEFT_BY_CODE,
        /** The suspended at the month's end, by the suspension code of the change that suspended them. */
        SUSPENDED_BY_CODE,
        /** The month's recruits, by recruiter and channel. */
        RECRUITED_BY,
        /** Members whose status nobody knows (rule X), by the status as stored. */
        UNKNOWN_STATUS
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "workplace_key", nullable = false)
    private Long workplaceKey;

    @Column(name = "measure", nullable = false, length = 20)
    private String measure;

    @Column(name = "code")
    private Integer code;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "members", nullable = false)
    private Integer members;

    @Override
    public String toString() {
        return "MonthEndCount[" + monthStart + ", " + measure + "]";
    }
}

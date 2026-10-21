package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A month's record (month end 04.2): which month, who took it, when, and the earliest status change on record then.
 * Written once, by {@code MonthEndFactsRepository}'s statement, inside the take's one snapshot -- never saved through
 * this entity, which exists so that H2's schema, built from the entities, has the table the statement writes.
 *
 * <p>🔒 A month's first take once, and each take replaced once (04.11, MM51, MD70). On PostgreSQL by two PARTIAL
 * unique indexes: {@code month_end_months_taken_once} over the first takes (V60), {@code month_end_months_replaced_once}
 * over the takes that replace one (V64), and a take replaces only a take of its own month, by foreign key. H2 has no
 * partial index, so the constraint below stands in for both: a month's take numbers are unique -- the first is 1, and
 * two re-takes of one take would both be the next. Append-only on PostgreSQL by trigger (MD46).
 */
@Entity
@Table(name = "month_end_months",
        uniqueConstraints = @UniqueConstraint(name = "month_end_months_one_a_take",
                columnNames = {"month_start", "take_number"}))
@Getter
@NoArgsConstructor
public class MonthEndMonth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "taken_by", nullable = false, length = 100)
    private String takenBy;

    @Column(name = "taken_at", nullable = false)
    private Instant takenAt;

    @Column(name = "changes_from")
    private Instant changesFrom;

    @Column(name = "replaces_id")
    private Long replacesId;

    /** 1 for a month's first take, one more for each take that replaces it (V64). */
    @Column(name = "take_number", nullable = false)
    private Integer takeNumber;

    /** V79, Stephne MEN-11: why a take again replaced the one before -- required of every take after the first. */
    @Column(name = "reason", length = 500)
    private String reason;

    /**
     * V79, Stephne MEN-14: the window the take counted -- from the cut-off of the month before, up to its own. Empty on
     * a take made before V79, whose window was the calendar month up to the moment it was taken.
     */
    @Column(name = "counts_from")
    private Instant countsFrom;

    @Column(name = "counts_until")
    private Instant countsUntil;

    @Override
    public String toString() {
        return "MonthEndMonth[" + id + ", " + monthStart + "]";
    }
}

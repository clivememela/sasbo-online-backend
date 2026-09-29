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
 * <p>🔒 Taken once. On PostgreSQL by a PARTIAL unique index, {@code month_end_months_taken_once}, over the months no
 * later take replaces (V59); on H2 the constraint below stands in for it, whole, because nothing replaces a take yet
 * (MM51). Append-only on PostgreSQL by trigger (MD46).
 */
@Entity
@Table(name = "month_end_months",
        uniqueConstraints = @UniqueConstraint(name = "month_end_months_taken_once", columnNames = "month_start"))
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

    @Override
    public String toString() {
        return "MonthEndMonth[" + id + ", " + monthStart + "]";
    }
}

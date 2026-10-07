package za.co.titandynamix.sasbo_online_backend.entity.migration;

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
 * One of the old system's month-end figures, entered for the month an export represents (V81): counts of members, by
 * a dimension and its key. Written by {@code MigrationTotalsRepository}'s statements; this entity exists so that H2's
 * schema has the table.
 */
@Entity
@Table(name = "migration_month_totals", uniqueConstraints = @UniqueConstraint(name = "migration_month_totals_once",
        columnNames = {"month_start", "dimension", "item_key", "measure"}))
@Getter
@NoArgsConstructor
public class MigrationMonthTotal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "dimension", nullable = false, length = 20)
    private String dimension;

    @Column(name = "item_key", nullable = false, length = 60)
    private String itemKey;

    @Column(name = "measure", nullable = false, length = 12)
    private String measure;

    @Column(name = "legacy_value", nullable = false)
    private Long legacyValue;

    @Column(name = "source", length = 60)
    private String source;

    @Column(name = "entered_by", nullable = false, length = 100)
    private String enteredBy;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Override
    public String toString() {
        return "MigrationMonthTotal[" + monthStart + ", " + dimension + ", " + measure + "]";
    }
}

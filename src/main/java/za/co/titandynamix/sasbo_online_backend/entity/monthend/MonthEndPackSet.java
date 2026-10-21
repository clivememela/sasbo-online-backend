package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A recipient of a region's month-end pack (October build; Stephne MEN-02, MEN-08): a region, the areas of it the set
 * covers where the region is split (Johannesburg's two sets), the POSITION it goes to and that position's work address.
 * Written by {@code MonthEndSendSettingsRepository}'s statements; this entity exists so that H2's schema has the table.
 */
@Entity
@Table(name = "month_end_pack_sets")
@Getter
@NoArgsConstructor
public class MonthEndPackSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;

    @Column(name = "label", nullable = false, length = 60)
    private String label;

    @Column(name = "region_code", nullable = false, length = 8)
    private String regionCode;

    /** The areas the set covers, comma-separated; none for the whole region. */
    @Column(name = "area_letters", length = 60)
    private String areaLetters;

    @Column(name = "position", nullable = false, length = 60)
    private String position;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "MonthEndPackSet[" + code + "]";
    }
}

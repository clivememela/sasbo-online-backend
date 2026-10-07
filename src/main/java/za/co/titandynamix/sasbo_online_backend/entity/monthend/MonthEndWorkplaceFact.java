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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One workplace of a taken month (month end 04.2, MD43): its figures, and its lineage AS IT STOOD THAT DAY -- one row
 * a workplace, and one more for the members with no workplace ({@code workplaceKey} 0). No sheet is stored; every sheet
 * is a {@code GROUP BY} over these rows of one month.
 *
 * <p>Written only by {@code MonthEndFactsRepository}'s one statement, inside the take's snapshot; this entity exists
 * so that H2's schema has the table. {@code workplaceId} is not a foreign key: nothing done to a workplace afterwards
 * reaches back into a month already taken (MV-30). Append-only on PostgreSQL by trigger (MD46).
 */
@Entity
@Table(name = "month_end_workplaces", uniqueConstraints = @UniqueConstraint(
        name = "month_end_workplaces_one_a_workplace", columnNames = {"month_id", "workplace_key"}))
@Getter
@NoArgsConstructor
public class MonthEndWorkplaceFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "month_id", nullable = false)
    private Long monthId;

    @Column(name = "month_start", nullable = false)
    private LocalDate monthStart;

    @Column(name = "workplace_key", nullable = false)
    private Long workplaceKey;

    @Column(name = "workplace_id")
    private Long workplaceId;

    @Column(name = "ra_number")
    private Integer raNumber;

    @Column(name = "table_code")
    private Integer tableCode;

    @Column(name = "table_code_name", length = 100)
    private String tableCodeName;

    @Column(name = "employer_ordinal")
    private Integer employerOrdinal;

    @Column(name = "employer_name", length = 100)
    private String employerName;

    @Column(name = "group_name", length = 100)
    private String groupName;

    @Column(name = "group_ordinal")
    private Integer groupOrdinal;

    @Column(name = "group_core")
    private Boolean groupCore;

    /**
     * V79, Stephne MEN-31: the core tier the workplace's table code stood in when the month was taken -- {@code A},
     * {@code B} or {@code C}, or empty for "all other". Empty, too, on every month taken before tiers existed.
     */
    @Column(name = "core_tier", length = 1)
    private String coreTier;

    @Column(name = "area_letter", length = 4)
    private String areaLetter;

    @Column(name = "region_code", length = 8)
    private String regionCode;

    @Column(name = "region_name", length = 60)
    private String regionName;

    @Column(name = "committee_code", length = 3)
    private String committeeCode;

    @Column(name = "committee_name", length = 60)
    private String committeeName;

    @Column(name = "complement")
    private Integer complement;

    @Column(name = "on_record", nullable = false)
    private Integer onRecord;

    @Column(name = "suspended", nullable = false)
    private Integer suspended;

    @Column(name = "paying", nullable = false)
    private Integer paying;

    @Column(name = "opening", nullable = false)
    private Integer opening;

    @Column(name = "joined", nullable = false)
    private Integer joined;

    @Column(name = "left_count", nullable = false)
    private Integer leftCount;

    @Column(name = "reinstated", nullable = false)
    private Integer reinstated;

    @Column(name = "by_debit_order", nullable = false)
    private Integer byDebitOrder;

    @Column(name = "by_deduction", nullable = false)
    private Integer byDeduction;

    @Column(name = "by_other", nullable = false)
    private Integer byOther;

    @Column(name = "debit_order_rand", nullable = false, precision = 14, scale = 2)
    private BigDecimal debitOrderRand;

    @Column(name = "deduction_rand", nullable = false, precision = 14, scale = 2)
    private BigDecimal deductionRand;

    @Column(name = "other_rand", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherRand;

    @Column(name = "representatives", nullable = false)
    private Integer representatives;

    @Column(name = "committee_members", nullable = false)
    private Integer committeeMembers;

    /** 04.8 (V63, MM80): the members on record marked as Memnet members. A month taken before the mark counts none. */
    @Column(name = "memnet_members", nullable = false)
    private Integer memnetMembers;

    @Column(name = "seeded", nullable = false)
    private Integer seeded;

    @Column(name = "members_from")
    private LocalDate membersFrom;

    @Override
    public String toString() {
        return "MonthEndWorkplaceFact[" + monthStart + ", workplace " + workplaceKey + "]";
    }
}

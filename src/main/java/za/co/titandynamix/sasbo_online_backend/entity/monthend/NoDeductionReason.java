package za.co.titandynamix.sasbo_online_backend.entity.monthend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A reason for no payroll deduction (V79) -- Stephne, MEN-17: "Can we have a dropdown sayin a reason for no payroll
 * deduction. Also have the opporenity to type a reason". Kept as data, edited by an administrator who maintains
 * SASBO's lists ({@code REFERENCE_MAINTAIN}); never deleted, since a status change names it -- set inactive instead.
 *
 * <p>Each reason stands under one of SASBO's suspension codes, BY ITS NUMBER: her own codes are 11 (not paid on
 * payroll), 12 (maternity leave) and 21 (sick leave), and a reason whose code she has not given -- unpaid leave,
 * paternity leave, insufficient funds -- stands under 11 and is {@code provisional} until she says (MEN-17's
 * clarification). The name is unique whatever its case: on PostgreSQL by the index {@code no_deduction_reasons_name_unique}
 * over {@code lower(btrim(name))}, which H2, built from this entity, stands in for with the exact name.
 */
@Entity
@Table(name = "no_deduction_reasons")
@Getter
@Setter
@NoArgsConstructor
public class NoDeductionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 60)
    private String name;

    /** SASBO's number for the suspension code the reason stands under -- 11, 12, 21 -- never a table's id (RC7). */
    @Column(name = "suspension_code", nullable = false)
    private Integer suspensionCode;

    /** Where the reason stands on the drop-down. */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    /** An inactive reason is kept for the changes that name it, and offered to nobody. */
    @Column(name = "active", nullable = false)
    private boolean active;

    /** Whether the code it stands under is ours, until SASBO confirms it. */
    @Column(name = "provisional", nullable = false)
    private boolean provisional;

    /** Two administrators editing one reason: the second is told, never silently overwrites (optimistic locking). */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Override
    public String toString() {
        return "NoDeductionReason[" + id + "]";
    }
}

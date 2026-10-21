package za.co.titandynamix.sasbo_online_backend.entity.acb;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.time.Instant;

/**
 * What one of the bank's numeric unpaid codes means. Data, filled from what the bank's reports have
 * shown and, when SASBO obtains it, from Standard Bank's own list.
 */
@Entity
@Table(name = "acb_unpaid_codes")
@Getter
@Setter
@NoArgsConstructor
public class AcbUnpaidCode {

    /** The code as the bank's report prints it: {@code 2}, {@code 4}, {@code 30}, {@code 34}. */
    @Id
    @Column(name = "code", length = 10)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private UnpaidReason reason;

    @Column(name = "wording", nullable = false, length = 100)
    private String wording;

    /** True when the wording is inferred from the bureau list rather than seen on a union letter. */
    @Column(name = "inferred", nullable = false)
    private Boolean inferred;

    @Column(name = "source", nullable = false)
    private String source;

    /** Whether the code allows a re-debit: true, false, or null for unknown (Q40, Q44). */
    @Column(name = "re_debit_allowed")
    private Boolean reDebitAllowed;

    /** True while that flag is inferred from SASBO's answers rather than read from the bank's list (Q39). */
    @Column(name = "re_debit_inferred")
    private Boolean reDebitInferred;

    /** SASBO's suspension code a return under this code records -- her sheet's mapping (DOD-01); null when unknown. */
    @Column(name = "suspension_code")
    private Integer suspensionCode;

    /** Who said whether it may be re-debited, in words -- "Stephne, DOD-03: ..." (V70). */
    @Column(name = "re_debit_source")
    private String reDebitSource;

    /**
     * Whether the row is still provisional -- something about it SASBO has not settled, said in {@link #note}.
     * Nullable here only because the fast lane builds the table from this entity; V70 makes it NOT NULL.
     */
    @Column(name = "provisional")
    private Boolean provisional;

    /** What is still open about the code, for the screen. */
    @Column(name = "note")
    private String note;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Two administrators editing one code: the second is told (CODE_VERSION_CONFLICT). Written by JDBC. */
    @Column(name = "version")
    private Integer version;

    /** The re-debit rule as data: yes, no, or unknown (Stephne, DOD-03, DOD-04; Friday item 9). */
    public ReDebit reDebit() {
        return ReDebit.of(reDebitAllowed);
    }

    /**
     * The decision recording SUGGESTS for this code -- the administrator's own choice wins wherever the rules allow
     * it. A code that may not be re-debited suggests SUSPEND; one that may suggests RE_PRESENT; an unknown one
     * suggests nothing, and cannot be re-debited until it is set.
     */
    public UnpaidDecision suggestedDecision() {
        return switch (reDebit()) {
            case YES -> UnpaidDecision.RE_PRESENT;
            case NO -> UnpaidDecision.SUSPEND;
            case UNKNOWN -> null;
        };
    }

    /** Whether the row is provisional, reading the fast lane's missing value as settled. */
    public boolean isProvisional() {
        return Boolean.TRUE.equals(provisional);
    }
}

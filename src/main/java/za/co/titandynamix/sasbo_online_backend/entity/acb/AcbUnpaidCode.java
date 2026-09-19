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
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;

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
}

package za.co.titandynamix.sasbo_online_backend.entity.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One of SASBO's core tiers -- Core A, Core B or Core C (V79). Stephne, MEN-31: "Decisions are made by Senior
 * Secretariat. | Core A - Absa, FNB, Nedbank, Standard, Capitec, oldmutual. | Core B - African, Landbank, SBV, SA
 * Reserve, Santam, Ubank. | Core C - Access, Bankserve, National Housing | All other are captured under individual
 * institutions". Which table code stands in which tier is kept on the table code; a code in none is one of "all
 * other". {@code confirmedOn} is empty while the mapping is the one read from her Core List, provisional.
 */
@Entity
@Table(name = "core_tiers")
@Getter
@Setter
@NoArgsConstructor
public class CoreTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code A}, {@code B} or {@code C}: the database refuses any other. */
    @Column(name = "code", nullable = false, unique = true, length = 1)
    private String code;

    @Column(name = "name", nullable = false, length = 40)
    private String name;

    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;

    @Column(name = "confirmed_on")
    private LocalDate confirmedOn;
}

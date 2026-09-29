package za.co.titandynamix.sasbo_online_backend.entity.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An employer -- the level above the table code, {@code FIRSTRAND BANK LIMITED (FNB)} -- in its group (01.1, MD31).
 *
 * <p>Keyed by where it stands on {@code grlist}, never by its name: two of SASBO's employers print the same name
 * ({@code CAPITEC BUSINESS}, codes 8 and 9). A member reaches their employer through their workplace's table code,
 * every employer with a member being one table code or the sum of two, as the files prove (MD42).
 */
@Entity
@Table(name = "employers")
@Getter
@Setter
@NoArgsConstructor
public class Employer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ordinal", nullable = false, unique = true)
    private Integer ordinal;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private EmployerGroup group;
}

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

/**
 * SASBO's letter for a province -- the first character of every committee code (01.1). Which province each letter
 * stands for is not printed, so {@code name} is empty until SASBO says (MM62).
 */
@Entity
@Table(name = "committee_provinces")
@Getter
@Setter
@NoArgsConstructor
public class CommitteeProvince {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "letter", nullable = false, unique = true, length = 1)
    private String letter;

    @Column(name = "name", length = 60)
    private String name;
}

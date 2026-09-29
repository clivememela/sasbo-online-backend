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
 * One of SASBO's areas -- a letter, {@code A} to {@code X}, and {@code Y}, {@code Z} and {@code XX}, for which the
 * reports print no region (01.1). Not the application's {@code areas} table, which holds suburbs (BR20).
 */
@Entity
@Table(name = "sasbo_areas")
@Getter
@Setter
@NoArgsConstructor
public class SasboArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "letter", nullable = false, unique = true, length = 4)
    private String letter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private SasboRegion region;
}

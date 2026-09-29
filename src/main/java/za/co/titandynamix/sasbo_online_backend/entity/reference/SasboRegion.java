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
 * One of SASBO's regions -- {@code JHB}, {@code CPT} ... {@code MISC} -- as {@code prt_mem_area} heads them (01.1).
 * A proposal until SASBO confirms the list (MD28). Written by {@code ReferenceListSeeder}, by JDBC batch.
 */
@Entity
@Table(name = "sasbo_regions")
@Getter
@Setter
@NoArgsConstructor
public class SasboRegion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 8)
    private String code;

    @Column(name = "name", nullable = false, length = 60)
    private String name;
}

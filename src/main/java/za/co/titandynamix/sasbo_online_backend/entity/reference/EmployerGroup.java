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

/** One of the nine employer groups {@code grlist} prints, and whether it is one of SASBO's core groups (01.1). */
@Entity
@Table(name = "employer_groups")
@Getter
@Setter
@NoArgsConstructor
public class EmployerGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "core", nullable = false)
    private boolean core;

    /** Where the group stands on {@code grlist}: the order SASBO reads them in. */
    @Column(name = "ordinal", nullable = false)
    private Integer ordinal;
}

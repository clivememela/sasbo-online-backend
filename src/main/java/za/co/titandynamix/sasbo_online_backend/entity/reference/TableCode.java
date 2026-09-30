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
 * SASBO's number for an employer, or a part of one -- its branches, its departments, its cash payers (01.1). The
 * key every one of SASBO's reports groups by. {@code employer} is empty where no member stood on the code in August,
 * because the tie is proved by sums (MD42).
 */
@Entity
@Table(name = "table_codes")
@Getter
@Setter
@NoArgsConstructor
public class TableCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private Integer code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employer_id")
    private Employer employer;

    /** {@code DEPARTMENTS} or {@code CASH} where the name says it is that part of its employer; else empty. */
    @Column(name = "part", length = 20)
    private String part;
}

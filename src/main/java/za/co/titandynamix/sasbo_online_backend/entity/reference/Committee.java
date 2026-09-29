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
 * A committee -- {@code G02}, a Branch Executive Committee or a line that is not one, as {@code prt_comm_prov}
 * prints it (01.1). A member belongs to one through their workplace, whose code it carries. The recruiting number
 * is the statistics workbook's second numbering of the same committees, where it gives one.
 */
@Entity
@Table(name = "committees")
@Getter
@Setter
@NoArgsConstructor
public class Committee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 3)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    private CommitteeProvince province;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "recruiting_number")
    private Integer recruitingNumber;
}

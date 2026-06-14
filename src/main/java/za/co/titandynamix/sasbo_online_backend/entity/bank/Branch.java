package za.co.titandynamix.sasbo_online_backend.entity.bank;

import jakarta.persistence.*;
import lombok.*;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;

@Entity
@Getter @Setter @AllArgsConstructor @NoArgsConstructor
@Table(name = "branches")
public class Branch {

    @Id @GeneratedValue @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bank_id", nullable = false)
    private Bank bank;

    @Column(name = "branch_name", nullable = false, length = 100)
    private String branchName;

    @Column(name = "branch_code", nullable = false, length = 6)
    private String branchCode;

    @Embedded
    private PhysicalAddress physicalAddress;

    // Custom getter safely handles lazy loading or null parents
    public String getBankName() {
        return this.bank != null ? this.bank.getName() : null;
    }
}

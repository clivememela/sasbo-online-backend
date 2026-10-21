package za.co.titandynamix.sasbo_online_backend.entity.signin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One of SASBO's positions (V65), with the role a new user in it is given. Provisional until SASBO's list arrives,
 * which replaces the names as data, with no change of code (permissions.md).
 */
@Entity
@Table(name = "staff_positions", uniqueConstraints = @UniqueConstraint(
        name = "staff_positions_code_unique", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class StaffPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "default_role_id", nullable = false)
    private StaffRole defaultRole;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "ordinal", nullable = false)
    private int ordinal;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, length = 100)
    private String updatedBy;

    @Override
    public String toString() {
        return "StaffPosition[" + id + ", " + code + "]";
    }
}

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
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * One permission a role holds (V65). The name is kept as text, never as a database enum: the catalogue is code and
 * grows, and a row naming a permission the code has retired is skipped on reading ({@link StaffRole#permissions()}).
 */
@Entity
@Table(name = "staff_role_permissions", uniqueConstraints = @UniqueConstraint(
        name = "staff_role_permissions_once", columnNames = {"role_id", "permission"}))
@Getter
@NoArgsConstructor
public class StaffRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private StaffRole role;

    @Column(name = "permission", nullable = false, length = 40)
    private String permission;

    StaffRolePermission(StaffRole role, String permission) {
        this.role = role;
        this.permission = permission;
    }

    /** Within one role a permission is held once: equal by name, which is all a role's set compares. */
    @Override
    public boolean equals(Object other) {
        return other instanceof StaffRolePermission row && Objects.equals(permission, row.permission);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(permission);
    }

    @Override
    public String toString() {
        return "StaffRolePermission[" + permission + "]";
    }
}

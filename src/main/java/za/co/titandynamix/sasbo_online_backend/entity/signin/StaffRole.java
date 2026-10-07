package za.co.titandynamix.sasbo_online_backend.entity.signin;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.security.Permission;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A role: a set of the catalogue's permissions, kept as data (V65). A position names the role its new users are given;
 * a user holds one role. The System Administrator's role is FIXED -- it holds every permission, and its permissions are
 * not edited, so a permission added to the catalogue is always held by somebody who can grant it.
 */
@Entity
@Table(name = "staff_roles", uniqueConstraints = @UniqueConstraint(
        name = "staff_roles_code_unique", columnNames = "code"))
@Getter
@Setter
@NoArgsConstructor
public class StaffRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "fixed", nullable = false)
    private boolean fixed;

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

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StaffRolePermission> permissionRows = new HashSet<>();

    /**
     * The permissions this role holds. A name the catalogue no longer has is left out rather than refused, so a
     * permission retired from the code can never stop a role -- and every user holding it -- from loading.
     */
    public Set<Permission> permissions() {
        Set<String> known = Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());
        EnumSet<Permission> held = EnumSet.noneOf(Permission.class);
        for (StaffRolePermission row : permissionRows) {
            if (known.contains(row.getPermission())) {
                held.add(Permission.valueOf(row.getPermission()));
            }
        }
        return held;
    }

    /** Makes this role hold exactly {@code wanted}: missing rows added, the rest removed. */
    public void holdExactly(Collection<Permission> wanted) {
        Set<String> names = wanted.stream().map(Enum::name).collect(Collectors.toSet());
        permissionRows.removeIf(row -> !names.contains(row.getPermission()));
        Set<String> present = permissionRows.stream().map(StaffRolePermission::getPermission)
                .collect(Collectors.toSet());
        for (String name : names) {
            if (!present.contains(name)) {
                permissionRows.add(new StaffRolePermission(this, name));
            }
        }
    }

    /** Adds what is missing, removing nothing -- the fixed role's top-up on every start. */
    public boolean holdAtLeast(Collection<Permission> wanted) {
        Set<String> present = permissionRows.stream().map(StaffRolePermission::getPermission)
                .collect(Collectors.toSet());
        boolean added = false;
        for (Permission permission : wanted) {
            if (!present.contains(permission.name())) {
                permissionRows.add(new StaffRolePermission(this, permission.name()));
                added = true;
            }
        }
        return added;
    }

    @Override
    public String toString() {
        return "StaffRole[" + id + ", " + code + "]";
    }
}

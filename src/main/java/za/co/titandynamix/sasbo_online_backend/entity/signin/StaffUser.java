package za.co.titandynamix.sasbo_online_backend.entity.signin;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A member of SASBO's staff who signs in (V65) -- never a member of the union: the legacy {@code users} table is the
 * members' side, and the two sign-ins are kept apart (USR-36).
 *
 * <p>The username is kept in lower case, so it is unique case-insensitively everywhere. The password is a BCrypt hash
 * and nothing else ({@code staff_users_password_hash_bcrypt}); it is never printed, here or anywhere.
 */
@Entity
@Table(name = "staff_users", uniqueConstraints = @UniqueConstraint(
        name = "staff_users_username_unique", columnNames = "username"))
@Getter
@Setter
@NoArgsConstructor
public class StaffUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, length = 60)
    private String username;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_id", nullable = false)
    private StaffPosition position;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private StaffRole role;

    @Column(name = "scope_type", nullable = false, length = 6)
    private String scopeType;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = true;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_sign_in_at")
    private Instant lastSignInAt;

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

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<StaffUserScopeCode> scopeRows = new HashSet<>();

    /** How far this user may see: everything, some regions or some areas. */
    public CallerScope scope() {
        CallerScope.Type type = CallerScope.Type.valueOf(scopeType);
        Set<String> codes = scopeRows.stream().map(StaffUserScopeCode::getCode).collect(Collectors.toSet());
        return new CallerScope(type, type == CallerScope.Type.ALL ? Set.of() : codes);
    }

    /** The codes, in their order, for an answer. */
    public Set<String> scopeCodes() {
        return scopeRows.stream().map(StaffUserScopeCode::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Makes this user's scope exactly {@code type} over {@code codes}: missing rows added, the rest removed. */
    public void scopeExactly(CallerScope.Type type, Collection<String> codes) {
        this.scopeType = type.name();
        Set<String> wanted = type == CallerScope.Type.ALL ? Set.of() : Set.copyOf(codes);
        scopeRows.removeIf(row -> !wanted.contains(row.getCode()));
        Set<String> present = scopeRows.stream().map(StaffUserScopeCode::getCode).collect(Collectors.toSet());
        for (String code : wanted) {
            if (!present.contains(code)) {
                scopeRows.add(new StaffUserScopeCode(this, code));
            }
        }
    }

    /** Locked against signing in at {@code now}. */
    public boolean lockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    @Override
    public String toString() {
        return "StaffUser[" + id + ", " + username + "]";
    }
}

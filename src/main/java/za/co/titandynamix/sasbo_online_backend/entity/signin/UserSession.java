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
import lombok.Setter;

import java.time.Instant;

/**
 * A signed-in user's session (V66). Only the token's SHA-256 is kept -- the token itself is handed to the browser once,
 * at sign-in, and never stored or logged.
 */
@Entity
@Table(name = "user_sessions", uniqueConstraints = @UniqueConstraint(
        name = "user_sessions_token_hash_unique", columnNames = "token_hash"))
@Getter
@Setter
@NoArgsConstructor
public class UserSession {

    /** Why a session ended before its time -- the values {@code user_sessions_revoked_reason_known} admits. */
    public static final String SIGNED_OUT = "SIGNED_OUT";
    public static final String DEACTIVATED = "DEACTIVATED";
    public static final String PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private StaffUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason", length = 20)
    private String revokedReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Override
    public String toString() {
        return "UserSession[" + id + "]";
    }
}

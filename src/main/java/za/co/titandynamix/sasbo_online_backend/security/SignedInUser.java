package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.Objects;
import java.util.Set;

/**
 * The caller behind a valid session, as the token filter puts it into the security context: who, how far they may see,
 * what they hold. {@code AcbActor} names them on every history row; {@link CallerScope#current()} reads their scope.
 *
 * <p>A user whose password is temporary ({@code mustChangePassword}) is signed in but holds NOTHING until they change
 * it -- the temporary password was seen by the administrator who made it.
 *
 * @param userId             {@code staff_users.id}
 * @param sessionId          {@code user_sessions.id} -- the session this request came with
 * @param permissions        what their role holds; the authorities are these, or none while the password is temporary
 */
public record SignedInUser(long userId, long sessionId, String username, String displayName, CallerScope scope,
                           Set<Permission> permissions, boolean mustChangePassword)
        implements ScopedPrincipal, AuthenticatedPrincipal {

    public SignedInUser {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(scope, "scope");
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** What the user may do now: their role's permissions, or nothing while their password is temporary. */
    public Set<Permission> effectivePermissions() {
        return mustChangePassword ? Set.of() : permissions;
    }

    @Override
    public String getName() {
        return username;
    }
}

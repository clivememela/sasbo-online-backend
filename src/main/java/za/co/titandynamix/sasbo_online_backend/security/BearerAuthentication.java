package za.co.titandynamix.sasbo_online_backend.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Objects;

/**
 * A request authenticated by its session token. The authorities are the user's effective permissions by name -- what
 * {@code @PreAuthorize("hasAuthority('...')")} reads. The token itself is not kept: {@link #getCredentials()} is
 * {@code null}, so nothing downstream can print it.
 */
public final class BearerAuthentication extends AbstractAuthenticationToken {

    private static final long serialVersionUID = 1L;

    private final transient SignedInUser user;

    public BearerAuthentication(SignedInUser user) {
        super(Objects.requireNonNull(user, "user").effectivePermissions().stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList());
        this.user = user;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public SignedInUser getPrincipal() {
        return user;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BearerAuthentication that && super.equals(that) && Objects.equals(user, that.user);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), user);
    }
}

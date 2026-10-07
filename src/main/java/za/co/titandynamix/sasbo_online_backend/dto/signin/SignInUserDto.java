package za.co.titandynamix.sasbo_online_backend.dto.signin;

import java.util.List;

/**
 * A user as the contract names one (signin.md: {@code user}), plus what the administrator's screen needs to edit and
 * explain a row: {@code active}, {@code version} (sent back on an edit), and whether the user is locked out and until
 * when. Every instant is an ISO-8601 string.
 */
public record SignInUserDto(
        long id,
        String username,
        String displayName,
        String email,
        CodeNameDto position,
        CodeNameDto role,
        List<String> permissions,
        ScopeDto scope,
        boolean mustChangePassword,
        String lastSignInAt,
        boolean active,
        long version,
        boolean locked,
        String lockedUntil) {
}

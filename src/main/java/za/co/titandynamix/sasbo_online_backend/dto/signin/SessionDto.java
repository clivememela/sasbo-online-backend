package za.co.titandynamix.sasbo_online_backend.dto.signin;

/**
 * The answer to a sign-in: the token -- shown here once and never again, the server keeps only its hash -- when the
 * session ends at the latest (12 hours on; 30 minutes without a request ends it sooner), and the user.
 */
public record SessionDto(String token, String expiresAt, SignInUserDto user) {

    /** Never prints the token. */
    @Override
    public String toString() {
        return "SessionDto[expiresAt=" + expiresAt + "]";
    }
}

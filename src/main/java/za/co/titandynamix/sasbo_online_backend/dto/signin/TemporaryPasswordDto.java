package za.co.titandynamix.sasbo_online_backend.dto.signin;

/** The answer to a password reset: the new temporary password, shown this once. */
public record TemporaryPasswordDto(String temporaryPassword) {

    /** Never prints the temporary password. */
    @Override
    public String toString() {
        return "TemporaryPasswordDto[]";
    }
}

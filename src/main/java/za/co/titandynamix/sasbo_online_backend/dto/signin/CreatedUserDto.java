package za.co.titandynamix.sasbo_online_backend.dto.signin;

/**
 * The answer to creating a user: the user, and their temporary password -- shown this once, to be handed to them; they
 * change it when they first sign in.
 */
public record CreatedUserDto(SignInUserDto user, String temporaryPassword) {

    /** Never prints the temporary password. */
    @Override
    public String toString() {
        return "CreatedUserDto[" + (user == null ? null : user.username()) + "]";
    }
}

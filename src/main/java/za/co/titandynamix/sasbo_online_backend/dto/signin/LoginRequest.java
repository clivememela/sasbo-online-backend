package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/login}. The username is matched in lower case and trimmed; the password is used exactly as
 * typed. The ceilings stop an absurd body early -- a username past them signs nobody in.
 */
public record LoginRequest(
        @NotBlank(message = "Enter your username.")
        @Size(max = 254, message = "A username is at most 60 characters.")
        String username,
        @NotBlank(message = "Enter your password.")
        @Size(max = 1000, message = "A password is at most 72 characters.")
        String password) {

    /** Never prints the password: a record would print every component. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}

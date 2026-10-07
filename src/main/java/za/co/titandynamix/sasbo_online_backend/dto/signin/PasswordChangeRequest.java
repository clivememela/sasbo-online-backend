package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /api/auth/password}: the password in use, and the new one. */
public record PasswordChangeRequest(
        @NotBlank(message = "Enter the password you use now.")
        @Size(max = 1000, message = "A password is at most 72 characters.")
        String currentPassword,
        @NotBlank(message = "Enter a new password.")
        @Size(max = 1000, message = "A password is at most 72 characters.")
        String newPassword) {

    /** Never prints either password. */
    @Override
    public String toString() {
        return "PasswordChangeRequest[]";
    }
}

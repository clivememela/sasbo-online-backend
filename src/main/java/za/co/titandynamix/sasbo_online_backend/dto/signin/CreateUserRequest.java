package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/admin/users}: a new user with a position and a scope -- the 5 Oct ask, "an administrator creating
 * users with a position and an area". The role is the position's default unless one is named.
 */
public record CreateUserRequest(
        @NotBlank(message = "Enter a username.")
        @Pattern(regexp = "^\\s*[A-Za-z0-9][A-Za-z0-9._@-]{2,59}\\s*$", message = "A username is 3 to 60 letters, "
                + "digits, dots, dashes, underscores or @, starting with a letter or a digit.")
        String username,
        @NotBlank(message = "Enter the person's name.")
        @Size(max = 80, message = "A name is at most 80 characters.")
        String displayName,
        @Size(max = 254, message = "An e-mail address is at most 254 characters.")
        @Email(message = "Enter an e-mail address like name@sasbo.org.za, or leave it empty.")
        String email,
        @NotBlank(message = "Choose a position.")
        @Size(max = 40, message = "Choose a position from the list.")
        String positionCode,
        @Size(max = 40, message = "Choose a role from the list.")
        String roleCode,
        @NotNull(message = "Choose how far the user may see.")
        @Valid
        ScopeDto scope) {
}

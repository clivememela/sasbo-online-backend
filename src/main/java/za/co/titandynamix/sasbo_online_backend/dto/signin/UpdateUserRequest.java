package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/admin/users/{id}}: everything an administrator changes about a user, with the {@code version} the
 * screen read -- a user changed since is {@code STALE_VERSION}. The username never changes. A role left out keeps the
 * user's role.
 */
public record UpdateUserRequest(
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
        ScopeDto scope,
        @NotNull(message = "Say whether the user may sign in.")
        Boolean active,
        @NotNull(message = "The screen's copy of this user has no version. Open the user again.")
        Long version) {
}

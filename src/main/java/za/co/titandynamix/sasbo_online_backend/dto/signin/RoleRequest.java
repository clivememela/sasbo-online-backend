package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * {@code POST /api/admin/roles} and {@code PUT /api/admin/roles/{code}}. On an edit the address names the role and the
 * body's code is not read. {@code version}, when sent, must be the one on record.
 */
public record RoleRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "A code is 2 to 40 capital letters, digits or "
                + "underscores, starting with a letter -- REGIONAL_ORGANISER, say.")
        String code,
        @NotBlank(message = "Enter the role's name.")
        @Size(max = 80, message = "A name is at most 80 characters.")
        String name,
        @NotNull(message = "Choose the role's permissions, or none.")
        @Size(max = 100, message = "A role holds at most the whole catalogue.")
        List<@NotBlank(message = "A permission cannot be blank.")
             @Size(max = 40, message = "Choose permissions from the list.") String> permissions,
        Long version) {
}

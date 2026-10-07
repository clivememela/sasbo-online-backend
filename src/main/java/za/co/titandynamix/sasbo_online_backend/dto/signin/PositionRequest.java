package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/admin/positions} and {@code PUT /api/admin/positions/{code}}. The code is the position's for good:
 * on an edit the address names it and the body's is not read. {@code version}, when sent, must be the one on record;
 * {@code active} and {@code ordinal} left out keep what is there (a new position: in use, at the end).
 */
public record PositionRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "A code is 2 to 40 capital letters, digits or "
                + "underscores, starting with a letter -- REGIONAL_ORGANISER, say.")
        String code,
        @NotBlank(message = "Enter the position's name.")
        @Size(max = 80, message = "A name is at most 80 characters.")
        String name,
        @NotBlank(message = "Choose the role a new user in this position is given.")
        @Size(max = 40, message = "Choose a role from the list.")
        String defaultRoleCode,
        Boolean active,
        @Min(value = 0, message = "The order is a number from 0 to 9999.")
        @Max(value = 9999, message = "The order is a number from 0 to 9999.")
        Integer ordinal,
        Long version) {
}

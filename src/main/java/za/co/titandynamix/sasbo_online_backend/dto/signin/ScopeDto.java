package za.co.titandynamix.sasbo_online_backend.dto.signin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * How far a user may see: {@code ALL} with no codes, {@code REGION} with region codes ({@code sasbo_regions.code}), or
 * {@code AREA} with area letters ({@code sasbo_areas.letter}).
 */
public record ScopeDto(
        @NotBlank(message = "Choose how far the user may see: everything, some regions or some areas.")
        @Pattern(regexp = "ALL|REGION|AREA", message = "A scope is everything (ALL), some regions (REGION) or some "
                + "areas (AREA).")
        String type,
        @NotNull(message = "Name the regions or areas, or none for everything.")
        @Size(max = 30, message = "A user can be given at most 30 regions or areas.")
        List<@NotBlank(message = "A region or area cannot be blank.")
             @Size(max = 8, message = "A region or area code is at most 8 characters.") String> codes) {
}

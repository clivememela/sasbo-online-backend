package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotBlank;

/** The last date the non-working days list is complete for, moved later (D30). YYYY-MM-DD. */
public record KnownUntilRequest(@NotBlank String knownUntil) {
}

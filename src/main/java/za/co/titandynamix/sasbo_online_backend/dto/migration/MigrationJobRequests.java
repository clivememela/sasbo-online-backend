package za.co.titandynamix.sasbo_online_backend.dto.migration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** What a check, a move and an undo of a batch are asked with. */
public final class MigrationJobRequests {

    private MigrationJobRequests() {
    }

    /**
     * A check -- by the batch's own mapping, or by another of the same format, named here (a mapping corrected after the
     * first check). Optional.
     */
    public record Validate(@Size(max = 60) @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,59}") String mappingKey) {
    }

    /**
     * A move. {@code holdBackErrors} says the rows with errors stay behind, listed, while the rest move -- an informed
     * choice the request has to make; without it a batch with errors is refused, COMMIT_HAS_ERRORS.
     */
    public record Commit(boolean holdBackErrors) {
    }

    /** An undo, and why: a sentence kept with the batch. */
    public record Rollback(@NotBlank @Size(max = 300) String reason) {
    }
}

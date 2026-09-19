package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Move a run on -- approve it, cancel it -- from the version the screen last saw.
 *
 * @param version the run's version as read. If someone changed the run since, the answer is
 *                {@code RUN_VERSION_CONFLICT} with its current state, never a silent overwrite
 * @param reason  why, in the administrator's words; recorded on the audit trail
 */
public record RunTransitionRequest(
        @NotNull Integer version,
        @Size(max = 400) String reason) {
}

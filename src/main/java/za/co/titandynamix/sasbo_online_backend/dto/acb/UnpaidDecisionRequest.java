package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotNull;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

/** The decision on an unpaid recorded without one: present it again, or suspend. */
public record UnpaidDecisionRequest(@NotNull UnpaidDecision decision) {
}

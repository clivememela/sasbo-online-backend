package za.co.titandynamix.sasbo_online_backend.dto.acb;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import za.co.titandynamix.sasbo_online_backend.enums.AcbDeclineReason;

/**
 * The bank declined a run's file (D29; Stephne, DOD-08: "Correct it and upload again the same day").
 *
 * @param version   the run's version as the screen read it
 * @param reason    why, from the short list
 * @param bankWords the bank's own words, at most 300 characters -- kept on the run alone (SD3-D)
 */
public record DeclineRequest(@NotNull Integer version, @NotNull AcbDeclineReason reason,
                             @Size(max = 300) String bankWords) {
}

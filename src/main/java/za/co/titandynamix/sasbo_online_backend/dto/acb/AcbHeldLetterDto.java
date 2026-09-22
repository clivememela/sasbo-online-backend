package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

/**
 * A letter that could not go, on the run's worklist until someone deals with it (Q46, EH-E): HELD because the
 * member has no address, or none that can be sent to -- or FAILED, a send that did not go.
 *
 * @param status  {@code QUEUED} (held) or {@code FAILED}
 * @param reason  for a held letter {@code NO_EMAIL} or {@code INVALID_ADDRESS}; for a failed one the failure's
 *                class -- never its message
 * @param canSend the member now has an address it could go to -- the send itself waits on B1
 */
public record AcbHeldLetterDto(
        Long letterId,
        Long lineId,
        Integer memberId,
        String membershipNumber,
        String surname,
        LetterTemplate template,
        LetterStatus status,
        String reason,
        boolean canSend) {
}

package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

/**
 * A letter the system is holding (Q46): the member has no address, or none that can be sent to -- on
 * the run's worklist until someone deals with it by hand.
 *
 * @param reason  {@code NO_EMAIL} or {@code INVALID_ADDRESS}
 * @param canSend the member now has an address it could go to -- the send itself waits on B1
 */
public record AcbHeldLetterDto(
        Long letterId,
        Long lineId,
        Integer memberId,
        String membershipNumber,
        String surname,
        LetterTemplate template,
        String reason,
        boolean canSend) {
}

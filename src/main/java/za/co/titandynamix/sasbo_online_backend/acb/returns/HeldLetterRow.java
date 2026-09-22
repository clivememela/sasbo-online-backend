package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

/**
 * A letter that could not go, as the worklist query reads it, with the member's addresses as they stand now: its
 * {@code reason} is the held reason for a QUEUED letter and the failure's class for a FAILED one.
 */
public record HeldLetterRow(Long letterId, Long lineId, Integer memberId, String membershipNumber, String surname,
                            LetterTemplate template, LetterStatus status, String reason, String personalEmail,
                            String workEmail, String preferredEmail) {

    @Override
    public String toString() {
        return "HeldLetterRow[letterId=" + letterId + "]";
    }
}

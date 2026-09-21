package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;

/** A held letter as the worklist query reads it, with the member's addresses as they stand now. */
public record HeldLetterRow(Long letterId, Long lineId, Integer memberId, String membershipNumber, String surname,
                            LetterTemplate template, String reason, String personalEmail, String workEmail,
                            String preferredEmail) {

    @Override
    public String toString() {
        return "HeldLetterRow[letterId=" + letterId + "]";
    }
}

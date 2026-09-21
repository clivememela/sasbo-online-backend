package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;

/** One debit of a member's from a sent run, as the three-unpaids rule reads it. */
public record PresentationRow(Integer memberId, String membershipNumber, String surname, Long lineId,
                              LocalDate actionDate, AcbLineOutcome outcome) {

    public TerminationRule.Presentation presentation() {
        return new TerminationRule.Presentation(lineId, actionDate, outcome);
    }
}

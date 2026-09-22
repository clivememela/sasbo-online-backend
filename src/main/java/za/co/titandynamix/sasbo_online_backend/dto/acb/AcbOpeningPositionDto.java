package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * The opening arrears position (D24): how many members it holds, who entered it and when, and
 * whether it can still be replaced.
 *
 * @param members      how many members the capture holds; 0 when nothing was captured
 * @param enteredAt    ISO-8601, the capture's moment; null when nothing was captured
 * @param locked       true once the first run's returns are in -- it is never replaced after
 * @param lockedSince  ISO-8601, when the first returns were taken in; null while it is open
 * @param disagreements members whose stated arrears balance does not match their unpaids at the
 *                     flat rate -- reported, never resolved silently; bounded by the capture's own
 *                     size, at most five hundred
 */
public record AcbOpeningPositionDto(
        int members,
        String enteredBy,
        String enteredAt,
        boolean locked,
        String lockedSince,
        List<Disagreement> disagreements) {

    /** A member whose legacy arrears balance and captured unpaids disagree. */
    public record Disagreement(String membershipNumber, int unpaidsStanding, String arrearsBalance) {
    }
}

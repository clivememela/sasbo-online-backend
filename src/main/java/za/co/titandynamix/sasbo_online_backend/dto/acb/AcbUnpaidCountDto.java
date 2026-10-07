package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * A member's unpaid count as captured by hand (DOD-28): as at {@code countedOn}, 0 to 3.
 *
 * @param countedOn ISO-8601 date; the count replaces the member's debits before it
 * @param enteredAt ISO-8601 instant
 */
public record AcbUnpaidCountDto(Integer memberId, String membershipNumber, String surname, int unpaidCount,
                                String countedOn, String source, String enteredBy, String enteredAt) {
}

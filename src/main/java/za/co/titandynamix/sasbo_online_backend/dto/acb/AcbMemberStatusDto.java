package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * A member's status after the returns loop moved it (ACB {@code 03.5}; Stephne's answers of 6 October 2026).
 *
 * @param status            the member's status now -- "Terminated", "Suspended", "Active"
 * @param reason            why, as SASBO counts it -- {@code SUBS_IN_ARREARS}, {@code SUSPENSION_CODE},
 *                          {@code REINSTATED}
 * @param suspensionCode    SASBO's suspension code a suspension carries; null otherwise
 * @param letterId          the letter an ending queued (DOD-24); null when none could be queued
 * @param letterStatus      where that letter is
 * @param thirdUnpaidOn     the day the member's count reached three; the ending is dated the day it is confirmed --
 *                          which date SASBO wants is asked (DOD-23, Friday item 6)
 * @param joinedOn          a rejoined member's new join date (DOD-27)
 * @param withdrawnReDebits re-debits that will never be presented now the member is back (DOD-26, DOD-27)
 */
public record AcbMemberStatusDto(Integer memberId, String membershipNumber, String status, String reason,
                                 Integer suspensionCode, Long letterId, String letterStatus, String thirdUnpaidOn,
                                 String joinedOn, Integer withdrawnReDebits) {

    /** A status change with nothing beside it. */
    public AcbMemberStatusDto(Integer memberId, String membershipNumber, String status, String reason) {
        this(memberId, membershipNumber, status, reason, null, null, null, null, null, null);
    }
}

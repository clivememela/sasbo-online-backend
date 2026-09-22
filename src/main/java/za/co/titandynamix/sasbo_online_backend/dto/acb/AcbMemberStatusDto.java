package za.co.titandynamix.sasbo_online_backend.dto.acb;

/**
 * A member's status after the returns loop moved it (ACB {@code 03.5}).
 *
 * @param status the member's status now -- "Terminated", "Suspended"
 * @param reason why, as SASBO counts it -- {@code SUBS_IN_ARREARS}, {@code UNPAID_SUSPENDED}
 */
public record AcbMemberStatusDto(Integer memberId, String membershipNumber, String status, String reason) {
}

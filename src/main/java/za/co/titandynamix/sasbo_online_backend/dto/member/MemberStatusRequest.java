package za.co.titandynamix.sasbo_online_backend.dto.member;

/**
 * The body of {@code POST /api/members/{id}/status} (month end 03.1).
 *
 * @param toStatus        the status the member moves to, as the record spells it -- {@code Resigned}
 * @param expectedStatus  the status the screen showed, so a change asked on a stale view changes nothing (MV-5)
 * @param reason          {@code DELETION_CODE}, {@code SUSPENSION_CODE}, {@code REINSTATED} or {@code CAPTURED}
 * @param deletionCode    SASBO's number for the deletion code -- 1 to 10 -- never the table's id (RC7)
 * @param suspensionCode  SASBO's suspension code -- 11, 13, 19
 * @param note            the administrator's words, at most 500 characters; never logged
 * @param reasonUnderCode the reason SASBO keeps under the deletion code, as its list names it -- the reason a member
 *                        gave for resigning, under 2 -- or {@code null}. Optional, and only with a deletion code: it is
 *                        what SASBO's {@code resigned} report counts (04.5)
 */
public record MemberStatusRequest(String toStatus, String expectedStatus, String reason, Integer deletionCode,
                                  Integer suspensionCode, String note, String reasonUnderCode) {
}

package za.co.titandynamix.sasbo_online_backend.dto.member;

/**
 * The body of {@code POST /api/members/{id}/status} (month end 03.1).
 *
 * @param toStatus        the status the member moves to, as the record spells it -- {@code Resigned}
 * @param expectedStatus  the status the screen showed, so a change asked on a stale view changes nothing (MV-5)
 * @param reason          {@code DELETION_CODE}, {@code SUSPENSION_CODE}, {@code REINSTATED} or {@code CAPTURED}
 * @param deletionCode    SASBO's number for the deletion code -- 1 to 10 -- never the table's id (RC7)
 * @param suspensionCode  SASBO's suspension code -- 11, 13, 19; may be left out where {@code noDeductionReasonId} names a
 *                        reason, whose code is used
 * @param note            the administrator's words, at most 500 characters, line breaks allowed; never logged. Under a
 *                        code the reasons for no payroll deduction stand under, the typed reason (MEN-17)
 * @param reasonUnderCode the reason SASBO keeps under the deletion code, as its list names it -- the reason a member
 *                        gave for resigning, under 2 -- or {@code null}. Optional, and only with a deletion code: it is
 *                        what SASBO's {@code resigned} report counts (04.5)
 * @param noDeductionReasonId October build, Stephne MEN-17: a reason for no payroll deduction from the list, with a
 *                        suspension only; {@code null} for none
 */
public record MemberStatusRequest(String toStatus, String expectedStatus, String reason, Integer deletionCode,
                                  Integer suspensionCode, String note, String reasonUnderCode,
                                  Long noDeductionReasonId) {

    /** The body binds through every component, whatever other constructor stands beside this one. */
    @com.fasterxml.jackson.annotation.JsonCreator
    public MemberStatusRequest {
    }

    /** A request naming no reason for no deduction -- as every caller before the October build sent one. */
    public MemberStatusRequest(String toStatus, String expectedStatus, String reason, Integer deletionCode,
                               Integer suspensionCode, String note, String reasonUnderCode) {
        this(toStatus, expectedStatus, reason, deletionCode, suspensionCode, note, reasonUnderCode, null);
    }
}

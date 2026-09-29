package za.co.titandynamix.sasbo_online_backend.dto.member;

/**
 * What {@code POST /api/members/{id}/status} answers (month end 03.1): the member's status as it now is, and the
 * change written -- empty where the member was already in the status asked for, which writes nothing and is what
 * makes a retry after a timeout safe (ME-3). Dates and instants are ISO strings (MD16).
 */
public record MemberStatusAnswer(int memberId, String membershipNumber, String status, Change change) {

    /**
     * One change as it was written: the reason, and SASBO's number and words for the code it carries -- never the
     * table's id (RC7).
     */
    public record Change(long id, String fromStatus, String toStatus, String reason, Integer deletionCode,
                         String deletionReason, Integer suspensionCode, String suspensionReason, String note,
                         String changedBy, String changedAt) {
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.workplace;

/**
 * A visit draft as its owner reads it back (October build): the workplace it is to, everything it keeps, who saved it
 * and when, and -- once submitted -- the visit it became, when and by whom. Answered only to the person who saved it.
 * Dates and instants are ISO strings (MD16).
 *
 * @param status           {@code DRAFT}, changed until it is submitted; {@code SUBMITTED}, read-only
 * @param complementBefore once submitted: the workplace's complement before this visit wrote another; {@code null}
 *                         where it was the same, or none was taken
 * @param visitId          once submitted: the visit on record ({@code GET /api/workplaces/{id}/visits})
 * @param version          what the next save or the submission names
 */
public record VisitDraftDto(
        long id,
        String status,
        long workplaceId,
        int raNumber,
        String workplaceName,
        String visitedOn,
        String timeIn,
        String timeOut,
        String organiser,
        Integer complementFound,
        Integer complementBefore,
        Integer membershipFound,
        Integer nonMembers,
        Integer recruited,
        Integer nonMembersInterviewed,
        String createdBy,
        String createdAt,
        String updatedAt,
        String submittedBy,
        String submittedAt,
        Long visitId,
        int version) {
}

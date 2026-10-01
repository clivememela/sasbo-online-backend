package za.co.titandynamix.sasbo_online_backend.dto.workplace;

/**
 * A visit captured from the Location Visit form (month end A.1): the day, who visited, when they came and went, and
 * what the organiser found. Only the day is required. The day and the times are ISO text, as every date of the arc's
 * is (MD16): {@code 2026-09-10}, {@code 09:30}.
 *
 * <p>The form's other fields -- the manager, the representatives, their telephones and e-mails, the signatures -- are
 * a person's details, printed with the form and not sent: every read of them would refuse until sign-in (B1), so
 * stored they would be data nobody could read back.
 */
public record WorkplaceVisitRequest(
        String visitedOn,
        String timeIn,
        String timeOut,
        String organiser,
        Integer complementFound,
        Integer membershipFound,
        Integer nonMembers,
        Integer recruited,
        Integer nonMembersInterviewed) {
}

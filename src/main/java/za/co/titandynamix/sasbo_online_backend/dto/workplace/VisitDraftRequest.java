package za.co.titandynamix.sasbo_online_backend.dto.workplace;

/**
 * A visit draft as the Location Visit form saves it (October build, 5 Oct Show &amp; Tell): the workplace by its R/A
 * number, and what a visit keeps -- the day, who visited, when they came and went, and what the organiser found. Only
 * the R/A number is needed to save a draft; the day is needed to submit it. The day and the times are ISO text, as
 * every date of the arc's is (MD16): {@code 2026-09-10}, {@code 09:30}.
 *
 * <p>{@code version} is the version of the draft the save was made from -- needed to save over a draft, and ignored
 * when one is first saved. The form's other fields -- people's details and the signatures -- are printed with the
 * form and never sent, as they are for a visit ({@link WorkplaceVisitRequest}).
 */
public record VisitDraftRequest(
        Integer raNumber,
        String visitedOn,
        String timeIn,
        String timeOut,
        String organiser,
        Integer complementFound,
        Integer membershipFound,
        Integer nonMembers,
        Integer recruited,
        Integer nonMembersInterviewed,
        Integer version) {

    /** The visit this draft would record, field for field -- what a visit's rules are checked against. */
    public WorkplaceVisitRequest asVisit() {
        return new WorkplaceVisitRequest(visitedOn, timeIn, timeOut, organiser, complementFound, membershipFound,
                nonMembers, recruited, nonMembersInterviewed);
    }
}

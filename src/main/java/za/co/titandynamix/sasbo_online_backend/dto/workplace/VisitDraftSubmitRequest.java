package za.co.titandynamix.sasbo_online_backend.dto.workplace;

/**
 * Submitting a visit draft (October build): the version of the draft as the organiser last saw it, so a draft saved
 * since -- in another tab -- is never submitted unseen.
 */
public record VisitDraftSubmitRequest(Integer version) {
}

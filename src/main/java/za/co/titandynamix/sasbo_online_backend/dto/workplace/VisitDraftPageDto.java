package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.util.List;

/**
 * A person's own visit drafts, a page at a time, the most recently saved first (October build) -- the drafts still
 * to submit, or those submitted, as {@code status} asks.
 */
public record VisitDraftPageDto(
        List<VisitDraftDto> drafts,
        String status,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}

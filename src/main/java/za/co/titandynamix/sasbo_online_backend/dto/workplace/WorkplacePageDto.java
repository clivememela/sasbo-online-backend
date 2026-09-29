package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.util.List;

/**
 * A page of the workplaces (01.2) -- the shape of ACB's {@code AcbPage}, with the one thing a page of SASBO's
 * workplaces must also be able to say: WHY there are none, where none is on record here ({@code NOT_LOADED},
 * {@code NOT_CONFIRMED}, {@code FAILED}, {@code EMPTY} -- ME-19). {@code reason} is empty whenever any workplace is on
 * record, including on a page a filter left empty.
 */
public record WorkplacePageDto(
        List<WorkplaceDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String reason) {
}

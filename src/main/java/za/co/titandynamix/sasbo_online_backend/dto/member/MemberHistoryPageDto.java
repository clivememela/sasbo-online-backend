package za.co.titandynamix.sasbo_online_backend.dto.member;

import java.util.List;

/**
 * A page of one member's history (month end 03.6), newest first and then by its own number: the fields its saves
 * changed and the changes of its status, merged by time. Dates are ISO strings (MD16).
 */
public record MemberHistoryPageDto(
        List<Entry> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * One change. {@code kind} is {@code FIELD} -- a field a save changed, {@code what} naming it, the rows of one save
     * sharing a {@code saveId} -- or {@code STATUS}: a change of status, with its {@code reason}, SASBO's {@code code}
     * for it where it carries one (a deletion or a suspension code, by SASBO's number -- RC7) and the note the person
     * wrote. Identity, passport and account numbers are their last four (03.5).
     */
    public record Entry(
            String kind,
            long id,
            String changedAt,
            String changedBy,
            String what,
            String from,
            String to,
            String reason,
            Integer code,
            String saveId,
            String note) {
    }
}

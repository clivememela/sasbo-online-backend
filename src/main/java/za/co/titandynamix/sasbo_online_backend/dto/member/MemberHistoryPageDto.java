package za.co.titandynamix.sasbo_online_backend.dto.member;

import java.util.List;

/**
 * A page of one member's history (month end 03.6), newest first and then by its own number: the fields its saves
 * changed and the changes of its status, merged by time. Dates are ISO strings (MD16).
 *
 * <p>{@code historyFrom} is the day, in South Africa, of the first change this database recorded -- nothing before it
 * was kept, so a member older than it has a history that begins there. It is answered on every page, an empty one
 * above all (ME-7): an empty history says since when it is empty. Null only where no change was ever recorded.
 */
public record MemberHistoryPageDto(
        List<Entry> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String historyFrom) {

    /**
     * One change. {@code kind} is {@code FIELD} -- a field a save changed, {@code what} naming it, the rows of one save
     * sharing a {@code saveId} -- or {@code STATUS}: a change of status, with its {@code reason}, SASBO's {@code code}
     * for it where it carries one (a deletion or a suspension code, by SASBO's number -- RC7), that code's
     * {@code codeName}, the reason the member gave under it where one was chosen ({@code reasonUnderCode}, 04.5 --
     * <i>Resigned Society</i>, then its reason) and the note the person wrote. Identity, passport and account numbers
     * are their last four (03.5).
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
            String codeName,
            String reasonUnderCode,
            String saveId,
            String note) {
    }
}

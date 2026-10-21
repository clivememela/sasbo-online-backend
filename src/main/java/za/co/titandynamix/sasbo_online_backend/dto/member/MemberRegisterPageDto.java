package za.co.titandynamix.sasbo_online_backend.dto.member;

import java.util.List;

/**
 * A page of the member register (register row A10) -- the shape of ACB's {@code AcbPage} and the workplaces' page:
 * the rows, which page this is (from 0), its size, how many members the question matched and how many pages that is.
 *
 * <p>{@code page} is the page ANSWERED, which is the page asked for unless that page is past the last: a list that
 * shrank under the pager -- a save, a change of status, a filter -- is answered with its last page, never with an empty
 * one that reads as the end of the register.
 *
 * <p>{@code sort} is the order the rows are in, as {@code key,direction} -- the one asked for, or the register's own.
 */
public record MemberRegisterPageDto(
        List<Row> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {

    /**
     * One member as the register lists them: what its six columns show, and the id that opens the record. 🔒 Nothing
     * else -- no identity, passport or account number, no contact detail, no balance. The whole record is read for ONE
     * member, when it is opened ({@code GET /api/members/{id}}).
     */
    public record Row(
            Integer id,
            String membershipNumber,
            String employeeNumber,
            String title,
            String initials,
            String surname,
            String firstNames,
            String bankName,
            String branch,
            String status) {

        /** 🔒 The id only: a record's own {@code toString} prints every value, and these are a member's name. */
        @Override
        public String toString() {
            return "Row[id=" + id + "]";
        }
    }
}

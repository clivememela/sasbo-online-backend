package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.math.BigDecimal;
import java.util.List;

/**
 * The members at one workplace, a page at a time -- what a location listing prints under its branch (5 Oct Show &amp;
 * Tell). Answered only to a caller holding {@code MEMBERS_VIEW}, and only for a workplace in the caller's region or
 * area. In the listing's order: surname, initials, membership number.
 */
public record WorkplaceMemberPageDto(
        long workplaceId,
        List<Member> members,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    /**
     * One member, slim, as the legacy location listing printed them: the number, the name, the status (and whether it
     * is a suspension -- its {@code Susp} column), the month they joined, the subscription, and whether an e-mail
     * address, a cell number and an identity number are on record ({@code Email · Cell · ID}, printed {@code Y}).
     *
     * @param employeeNumber masked to its last four digits ({@code ****1234}), or {@code ****} where it is shorter;
     *                       {@code null} where there is none
     * @param joinedOn       the day they joined, ISO; {@code null} where none is on record
     */
    public record Member(int id, String membershipNumber, String title, String initials, String surname, String status,
                         boolean suspended, String joinedOn, String employeeNumber, BigDecimal monthlySubscription,
                         boolean email, boolean cell, boolean identity) {

        @Override
        public String toString() {
            return "WorkplaceMemberPageDto.Member[id=" + id + "]";
        }
    }
}

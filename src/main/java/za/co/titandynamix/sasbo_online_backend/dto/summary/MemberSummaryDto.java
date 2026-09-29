package za.co.titandynamix.sasbo_online_backend.dto.summary;

import java.math.BigDecimal;
import java.util.List;

/**
 * The figures of one month, as {@code GET /api/members/summary} answers them (02.2) -- "the eye of SASBO".
 *
 * <p>{@code month} is the month the figures are for: the one the request named, or the server's own where it named
 * none, and {@code monthFromTheServer} says which (MD55). The totals are the sum of the rows, from one statement
 * (rule A). {@code groupedAsAt} is the day the members were placed by where they work -- today, for every month not
 * yet taken (rule F, MR-ROB-19). {@code workplacesServed} is the deployment's switch (MD62): below {@code ALL} the
 * figures are the totals and one row, and no penetration. {@code seededMembers} is how many of the members on record a
 * seed file lists -- so that a screen can say whose figures these are; nought where the production marker is set.
 * {@code membersFrom} and {@code leaversFrom} say how far back each figure can see. Dates are ISO strings (MD16).
 */
public record MemberSummaryDto(
        String month,
        boolean monthFromTheServer,
        String by,
        String area,
        String region,
        String workplacesServed,
        String groupedAsAt,
        Figures totals,
        List<Row> rows,
        Unplaced unplaced,
        long seededMembers,
        String membersFrom,
        String leaversFrom) {

    /**
     * The figures of the whole, or of one row. <em>On record</em>, <em>suspended</em> and <em>paying</em> are the
     * three, always together (MD24). The movement closes on its own leftover, never hidden (rule S): {@code closing =
     * opening + joined - left + reinstated + unexplained}, {@code closing} being {@code onRecord}. {@code penetration}
     * is paying over the staff complement, where a complement is on record and served (rule P); above one, it is
     * answered as it is, with {@code complementOutOfDate}.
     */
    public record Figures(
            long onRecord,
            long suspended,
            long paying,
            long joined,
            long left,
            long reinstated,
            long opening,
            long unexplained,
            long joinedYearToDate,
            long leftYearToDate,
            Long complement,
            BigDecimal penetration,
            boolean complementOutOfDate) {
    }

    /**
     * One row of the grouping. A row that names an employer or a table code with a membership from one to four is
     * {@code fewerThanFive}, and one withheld beside it so that no answer gives it back is {@code withheld}: either
     * way it carries no figures at all -- not its members, not its parts, not its penetration (rule C).
     */
    public record Row(
            String key,
            String label,
            Boolean core,
            boolean fewerThanFive,
            boolean withheld,
            Figures figures) {
    }

    /**
     * What the figures could not place, and why -- said, never guessed (rule X, ME-17): members whose status the
     * vocabulary does not know, by the value and counted; members on record with no workplace (where workplaces are
     * served); members on record at a workplace with no table code.
     */
    public record Unplaced(List<UnknownStatus> statuses, Long noWorkplace, long noTableCode) {
    }

    /** A status nobody knows, and how many members stand in it. */
    public record UnknownStatus(String status, long members) {
    }
}

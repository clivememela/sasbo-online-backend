package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.util.List;

/**
 * A workplace's visits, a page at a time, newest first (month end A.1) -- as open as every workplace read.
 *
 * <p>What a visit answers here is the day and the complement the organiser found, with what it was before. Who
 * visited, and the counts the organiser took of members, non-members and recruits, are kept and not answered: the
 * workplaces carry no count of members (MD61) -- a non-member count gives the members back by subtraction -- and a
 * person's name is answered once sign-in exists (B1). {@code withheld} names them, so the screen can say why.
 *
 * @param visits   the page's visits, newest first, then by their own number
 * @param withheld what every visit keeps and this read does not answer
 */
public record WorkplaceVisitPageDto(
        long workplaceId,
        List<Visit> visits,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<String> withheld) {

    /**
     * One visit.
     *
     * @param visitedOn        the day, or the first of the month where only the month is known
     * @param dayKnown         false for SASBO's own history, which prints a month and no day
     * @param complementFound  the staff complement the organiser found, if they took it
     * @param complementBefore the workplace's complement before this visit wrote another; null where it was the same
     * @param seeded           SASBO's own history, from the branch list -- not a visit captured here
     */
    public record Visit(long id, String visitedOn, boolean dayKnown, Integer complementFound, Integer complementBefore,
                        boolean seeded) {
    }
}

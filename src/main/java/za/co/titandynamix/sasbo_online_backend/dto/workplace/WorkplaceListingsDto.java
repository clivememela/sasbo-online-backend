package za.co.titandynamix.sasbo_online_backend.dto.workplace;

import java.util.List;

/**
 * The organisers' batch print (5 Oct Show &amp; Tell: "put all the R/A numbers, print them one time"): the workplaces
 * whose R/A numbers were asked for, each with its lineage and the dates it was last visited -- as open as every read
 * of the workplaces, because it names organisations and no member. The members at each are a read of their own,
 * {@code GET /api/workplaces/{id}/members}, which needs {@code MEMBERS_VIEW}.
 *
 * <p>Answered as the deployment's switch allows (MD62): at {@code NONE} nothing, and {@code reason} says why; at
 * {@code NAMES} the workplaces without their staff complement or their visits -- nothing made from a staff number;
 * at {@code ALL} all of it.
 *
 * @param asked            the numbers asked for, each once, in the order they were sent
 * @param listings         the workplaces found, in the order their numbers were asked for
 * @param unknown          the numbers asked for that are no workplace on record here, in the order asked
 * @param workplacesServed what the deployment's switch says: {@code NONE}, {@code NAMES} or {@code ALL}
 * @param reason           why none was found where none could be ({@code NOT_LOADED}, {@code NOT_CONFIRMED},
 *                         {@code FAILED}, {@code EMPTY} -- ME-19); {@code null} whenever any workplace is on record
 */
public record WorkplaceListingsDto(
        List<Integer> asked,
        List<Listing> listings,
        List<Integer> unknown,
        String workplacesServed,
        String reason) {

    /**
     * One workplace's listing.
     *
     * @param lastVisited its newest visits, ten at most, newest first; empty below {@code ALL}
     */
    public record Listing(WorkplaceDto workplace, List<LastVisit> lastVisited) {
    }

    /**
     * A visit, as a listing prints it.
     *
     * @param visitedOn the day -- or the first of the month, where only the month is known (SASBO's own history)
     * @param dayKnown  false where only the month is known
     */
    public record LastVisit(String visitedOn, boolean dayKnown) {
    }
}

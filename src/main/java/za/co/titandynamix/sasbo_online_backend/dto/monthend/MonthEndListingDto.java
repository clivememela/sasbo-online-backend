package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A month's LISTING (month end 04.3, 04.7, 04.8): the members it names, a page at a time -- the month's new members,
 * its deleted members, the members a recruiter signed up, the elected representatives. Held by {@code MEMBERS_VIEW},
 * and only ever the members within the caller's region or area (a member with no workplace only for everything).
 * Dates are ISO text (MD16). No identity, account or contact number is in a listing.
 *
 * @param listing          the listing's key, as its route spells it
 * @param title            its plain name
 * @param legacy           the legacy report it answers
 * @param month            the month, {@code 2026-09}
 * @param state            {@code TAKEN} or {@code OPEN}
 * @param scope            how far the caller sees -- {@code ALL}, {@code REGION} or {@code AREA}
 * @param area             the area asked for, or {@code null}
 * @param region           the region asked for, or {@code null}
 * @param content          the page's members, in the listing's order
 * @param notes            what a reader must know to read it -- one sentence each
 */
public record MonthEndListingDto(String listing, String title, String legacy, String month, String state, String scope,
                                 String area, String region, List<Row> content, int page, int size, long totalElements,
                                 int totalPages, List<String> notes) {

    /**
     * One member a listing names.
     *
     * @param date      the day that put them on it: joined, or left (in SASBO's zone)
     * @param code      SASBO's deletion code of a leaving, or {@code null}
     * @param reason    the code's words, or the reason as stored where there is no code
     * @param raNumber  their workplace's R/A number, or {@code null} with no workplace on record
     * @param recruiter who signed them up, on the recruited listing
     * @param role      on the representatives listing: shop steward, BEC member, or both
     */
    public record Row(String membershipNumber, String initials, String surname, String date, Integer code,
                      String reason, Integer raNumber, String workplace, String area, String region, String recruiter,
                      String role) {
    }
}

package za.co.titandynamix.sasbo_online_backend.dto.monthend;

import java.util.List;

/**
 * A page of the members suspended now, in the caller's region or area (Stephne, MEN-18) -- and, beside it, how many of
 * the members the filters match have lapsed (three months) and how many have no date on record. Dates are ISO text.
 *
 * @param asAt        the day, in SASBO's zone, the lapses are judged on
 * @param lapseMonths how many months a suspension lasts
 * @param page        the page, from 0
 * @param size        the page's size, 1 to 100
 * @param total       how many members the filters match
 * @param totalPages  how many pages that is
 * @param lapsed      how many of them have lapsed by {@code asAt}
 * @param undated     how many of them have no change into Suspended on record, and so no date
 * @param items       the page
 */
public record SuspendedMembersPageDto(String asAt, int lapseMonths, int page, int size, long total, int totalPages,
                                      long lapsed, long undated, List<SuspendedMemberDto> items) {
}

package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;

/**
 * The figures of a month -- "the eye of SASBO" (02.2). An open read of COUNTS, under rule C (MD4).
 */
public interface MemberSummaryService {

    /**
     * The figures of {@code month}, grouped {@code by} one of the seven groupings, within an {@code area} or a
     * {@code region} where one is named.
     *
     * @param month  {@code YYYY-MM}; the server's own month where none is named (MD55)
     * @param by     {@code group}, {@code employer}, {@code tableCode}, {@code area}, {@code region},
     *               {@code committee} or {@code tier} (MEN-31); {@code group} where none is named
     * @throws za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal {@code SUMMARY_GROUPING_UNKNOWN},
     *         {@code SUMMARY_AREA_UNKNOWN}, {@code SUMMARY_MONTH_INVALID}, {@code SUMMARY_MONTH_IN_FUTURE},
     *         {@code SUMMARY_UNAVAILABLE}, or {@code REQUEST_MALFORMED} for an area and a region together, or a code
     *         over its ceiling
     */
    MemberSummaryDto summary(String month, String by, String area, String region);
}

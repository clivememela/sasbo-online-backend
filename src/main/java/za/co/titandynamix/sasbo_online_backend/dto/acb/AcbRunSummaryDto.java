package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.math.BigDecimal;

/**
 * A run as the runs list shows it: its month, its state and its control total.
 *
 * <p>The totals are counted from the lines for the whole page in one query -- never one query per
 * run, and never the totals stored at preparation taken on trust.
 */
public record AcbRunSummaryDto(
        Long id,
        String actionMonth,
        AcbRunStatus status,
        Integer version,
        String setupDate,
        int lineCount,
        BigDecimal totalAmount,
        boolean totalsAgree,
        boolean holidaysKnown,
        String failureReason) {
}

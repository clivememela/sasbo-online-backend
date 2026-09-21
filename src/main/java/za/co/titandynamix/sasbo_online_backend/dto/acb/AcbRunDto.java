package za.co.titandynamix.sasbo_online_backend.dto.acb;

import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.math.BigDecimal;
import java.util.List;

/**
 * A run as the screen reads it.
 *
 * <p>Every date and instant is an ISO-8601 STRING by construction -- {@code 2026-10},
 * {@code 2026-10-20}, {@code 2026-09-18T23:30:00+02:00} -- so this module is right whatever the
 * application's Jackson configuration does with {@code java.time} types. A guard asserts no ACB DTO
 * declares a {@code java.time} field.
 *
 * @param creationDate    the day the file was MADE -- stamped when it is first rendered (D31); null until then
 * @param makeBy          the latest day to make the file: three days before the first debit
 * @param makeByPassed    that day has passed and the file is still to be made -- flagged, never refused
 * @param lineCount       counted from the run's lines on this read, never taken on trust
 * @param totalAmount     summed from the run's lines on this read -- the control total
 * @param totalsAgree     whether the totals stored when the run was prepared still equal the lines;
 *                        {@code false} is a finding the screen must show, not a number to display
 * @param calendarAgrees  whether the holiday calendar still says of the run's month what it said when
 *                        the lines were computed; {@code false} means prepare it again -- it is
 *                        neither approved nor made into a file (SD3-F)
 * @param holidaysKnown   whether the action dates considered public holidays at all; {@code false}
 *                        means weekends only, and the screen says so beside every date
 * @param exclusions      how many members each reason kept out, listed reasons and counted values
 */
public record AcbRunDto(
        Long id,
        String actionMonth,
        AcbRunStatus status,
        Integer version,
        String setupDate,
        String creationDate,
        String purgeDate,
        String firstDueDate,
        String lastDueDate,
        String makeBy,
        boolean makeByPassed,
        int lineCount,
        BigDecimal totalAmount,
        boolean totalsAgree,
        boolean calendarAgrees,
        BigDecimal rateAmount,
        String rateEffectiveFrom,
        String holidaySet,
        boolean holidaysKnown,
        String createdBy,
        String approvedBy,
        String startedAt,
        String heartbeatAt,
        String failureReason,
        List<AcbExclusionSummaryDto> exclusions) {
}

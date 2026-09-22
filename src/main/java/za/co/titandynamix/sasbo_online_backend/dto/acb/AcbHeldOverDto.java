package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.math.BigDecimal;

/**
 * An unpaid decided present-again that a run did NOT present, because it would have been the member's
 * second re-debit in the run and the member's permission is not on record (D32). Derived on read from
 * the unclaimed candidates of members who are in the run -- never stored, never an exclusion (F46) --
 * and offered again the month after.
 *
 * @param lineId the unpaid line, in the run it came back from
 * @param runId  that run
 * @param month  the month the unpaid was for, {@code YYYY-MM}
 * @param permitted the member's permission is on record: the run takes it when it is prepared again, and
 *                  otherwise next month's does. 🔒 Whether, never what the note says (SD3-D)
 */
public record AcbHeldOverDto(
        Long lineId,
        Long runId,
        Integer memberId,
        String membershipNumber,
        String surname,
        String month,
        BigDecimal amount,
        boolean permitted) {
}

package za.co.titandynamix.sasbo_online_backend.acb.returns;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A held-over re-debit as the query reads it (D32): the run's month is stored as its first day, and
 * {@code permitted} is whether the member's permission is on record -- never what it says.
 */
public record HeldOverRow(Long lineId, Long runId, Integer memberId, String membershipNumber, String surname,
                          LocalDate monthStart, BigDecimal amount, boolean permitted) {
}

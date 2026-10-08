package za.co.titandynamix.sasbo_online_backend.repository;

import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A payroll file's state without its bytes: what a status check, the payment check or an answer naming the file needs.
 * The file's content is up to 10 MB, and an entity read carries it; a status never needs it.
 */
public record PayrollFileState(Long id, Long payrollEmployerId, LocalDate period, PayrollFileStatus status,
                               Long supersededBy, BigDecimal fileTotal, BigDecimal adminFeePercent, Instant uploadedAt,
                               String uploadedBy) {
}

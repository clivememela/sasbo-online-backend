package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * A payroll file as a list shows it: whose, which month, how it came in, what its last comparison found, and where its
 * payment stands (PAY-28). Dates and instants are ISO strings; amounts are decimal strings.
 */
public record PayrollFileDto(
        long id,
        PayrollEmployerRef employer,
        String period,
        String originalName,
        String entry,
        String format,
        int sizeBytes,
        String sha256,
        String status,
        String uploadedBy,
        String uploadedAt,
        String retainUntil,
        int rows,
        int matched,
        int discrepancies,
        int openDiscrepancies,
        String fileTotal,
        String paymentStatus,
        String lastRunAt,
        int version) {
}

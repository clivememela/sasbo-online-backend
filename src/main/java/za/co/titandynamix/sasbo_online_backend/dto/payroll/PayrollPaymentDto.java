package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * Money an employer paid over for a month (PAY-28). {@code alreadyRecorded} is {@code true} when the same payment --
 * employer, month, amount, date and reference -- was on record already, and nothing was recorded twice.
 */
public record PayrollPaymentDto(
        long id,
        PayrollEmployerRef employer,
        String period,
        String amount,
        String receivedOn,
        String reference,
        String recordedBy,
        String recordedAt,
        boolean alreadyRecorded) {
}

package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * Produce an employer's query list as a protected file: {@code xlsx} or {@code pdf}. No password is sent -- the system
 * generates one for the file and shows it once in the answer.
 */
public record PayrollQueryFileRequest(String format) {
}

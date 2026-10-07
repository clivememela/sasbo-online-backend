package za.co.titandynamix.sasbo_online_backend.dto.payroll;

import java.util.List;

/**
 * A list of queries raised with an employer for a file -- the members to add, delete or correct (Stephne, PAY-21). The
 * ID number is masked. The list leaves SASBO as a protected file ({@code files}), each under a password the system
 * generated for it and showed once -- kept nowhere.
 */
public record PayrollQueryDto(
        long id,
        long fileId,
        PayrollEmployerRef employer,
        String period,
        String raisedBy,
        String raisedAt,
        Counts counts,
        List<Line> lines,
        List<PayrollQueryFileDto> files) {

    /** How many lines of each kind. */
    public record Counts(int add, int delete, int correct) {
    }

    /** One line: what the employer is asked to do, about whom, and why. */
    public record Line(String action, long discrepancyId, String type, String staffNumber, String idNumber,
                       String name, String membershipNumber, String amountOnFile, String expectedAmount,
                       String reason) {
    }
}

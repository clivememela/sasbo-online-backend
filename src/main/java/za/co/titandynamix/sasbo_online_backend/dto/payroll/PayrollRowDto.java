package za.co.titandynamix.sasbo_online_backend.dto.payroll;

/**
 * One line of a payroll file and what the comparison made of it. The ID number is masked to its last four; the staff
 * number and name are shown as printed to holders of the payroll permissions. {@code match} is {@code null} where no
 * member fits the line.
 */
public record PayrollRowDto(
        long id,
        int rowNumber,
        String staffNumber,
        String idNumber,
        String initials,
        String surname,
        String amount,
        boolean valid,
        String problem,
        Match match) {

    /** The member the line was matched to, how, how surely, and what its amount says. */
    public record Match(long memberId, String membershipNumber, String memberName, String rule, String confidence,
                        String reason, String expectedAmount, String amountStatus, Integer months,
                        String rateUnknownFor) {
    }
}

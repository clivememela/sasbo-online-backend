package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * Who three unpaid presentations would terminate -- every unpaid counts, a re-debit included
 * (question 27) -- and nobody is terminated by it.
 *
 * @param countingFrom the first month this system sent a file for: nothing before it is counted
 * @param blindness    said on the face of the report: members already in arrears in the old system
 *                     are invisible to it until the opening position is captured
 */
public record AcbTerminationReportDto(
        String countingFrom,
        String blindness,
        List<Candidate> members) {

    /**
     * One member the rule would terminate.
     *
     * @param unpaidCount  unpaid presentations since the last paid one -- three or more
     * @param unpaidMonths the months of the unpaid presentations counted, oldest first
     */
    public record Candidate(
            Integer memberId,
            String membershipNumber,
            String surname,
            int unpaidCount,
            List<String> unpaidMonths) {
    }
}

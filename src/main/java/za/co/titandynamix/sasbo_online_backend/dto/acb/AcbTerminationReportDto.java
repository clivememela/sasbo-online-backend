package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * Who three unpaid presentations would terminate -- every unpaid counts, a re-debit included
 * (question 27) -- and who is at two, with the count of all; nobody is terminated by it.
 *
 * @param countingFrom the first month this system sent a file for
 * @param blindness    said on the face of the report: what it can and cannot see of the old system
 * @param members      the members at two unpaids or more in the last thirteen months, a page at a time,
 *                     those the rule would terminate first
 */
public record AcbTerminationReportDto(
        String countingFrom,
        String blindness,
        AcbPage<Candidate> members) {

    /**
     * One member at two unpaid presentations or more.
     *
     * @param unpaidCount      unpaid presentations since the last paid one -- the reset is provisional (Q49)
     * @param terminate        three or more: the rule would terminate the member
     * @param twelveMonthTotal every unpaid presentation in the last twelve months, reset or not -- beside the
     *                         streak while Q49 is unanswered, so either reading is in front of whoever decides
     * @param unpaidMonths     the months of the streak's unpaids, oldest first
     * @param openingUnpaids   unpaids brought from the old system by the opening position (D24)
     */
    public record Candidate(
            Integer memberId,
            String membershipNumber,
            String surname,
            int unpaidCount,
            boolean terminate,
            int twelveMonthTotal,
            List<String> unpaidMonths,
            int openingUnpaids) {
    }
}

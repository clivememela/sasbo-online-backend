package za.co.titandynamix.sasbo_online_backend.dto.acb;

import java.util.List;

/**
 * Who "three unsuccessful retries" would terminate -- under each reading SASBO has been asked to
 * choose between -- and nobody is terminated by it.
 *
 * @param countingFrom the first month this system sent a file for: nothing before it is counted
 * @param blindness    said on the face of the report: members already in arrears in the old system
 *                     are invisible to it until the opening position is imported
 */
public record AcbTerminationReportDto(
        String countingFrom,
        String blindness,
        List<Candidate> members) {

    /**
     * One member either reading would terminate.
     *
     * @param unpaidMonths the months of the unpaid presentations counted, oldest first
     */
    public record Candidate(
            Integer memberId,
            String membershipNumber,
            String surname,
            boolean everyUnpaidCounts,
            int everyUnpaidCount,
            boolean retriesOnly,
            int retriesOnlyCount,
            List<String> unpaidMonths) {
    }
}

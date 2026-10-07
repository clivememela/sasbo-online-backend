package za.co.titandynamix.sasbo_online_backend.utils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * {@code data/demonstration_population.json} as the file states it (the October build's demonstration data): the
 * invented members who stand in every region's areas, so that no figure of month end reads nought -- members in each
 * pack, joiners and leavers in each of the three past months, the suspended under every one of SASBO's suspension codes
 * and leavers under every deletion code.
 *
 * <p>A SEED DTO, not the entity, for the reason {@link SeedDebitOrderMember} gives: its components ARE the file's keys
 * and the reader refuses a key it does not know (F65). Written by {@code DemonstrationPopulationGenerator} in the test
 * sources and never by hand.
 *
 * @param generator        who wrote the file, and how to write it again
 * @param seed             the generator's fixed random seed
 * @param count            how many members the file holds -- the file states its size, and no test types it (MV-37)
 * @param block            the membership numbers the file may use, and the direction they are used in
 * @param generatedFrom    what the file stands on: proportions and SASBO's lists, never a count of SASBO's members
 * @param suspensionCodes  the suspension codes the members are suspended under that SASBO's August printouts lack
 * @param summary          the population counted, so the contract and the file can be compared without a database
 * @param members          the members
 * @param past             what happened to them in the three months before the clock's
 */
public record DemonstrationPopulation(String generator, long seed, int count, Map<String, String> block,
                                      Map<String, Object> generatedFrom, List<Code> suspensionCodes,
                                      Map<String, Object> summary, List<Member> members, List<Change> past) {

    /**
     * A suspension code from Stephne's DOD "Suspension codes" sheet (handover section B) that the reference lists do not
     * hold yet: the seeder puts it on record when absent, by code, never where the production marker is set.
     */
    public record Code(int code, String name, String source) {
    }

    /**
     * One invented member. A joiner of the three past months names the month ({@code joinedMonthsBack}, 1 to 3) and
     * the day, and the seeder dates them by the clock; everyone else carries a fixed {@code joinDate} long before.
     * {@code recruiter} is a POSITION among the recruiters on record (1 the first by id), never a name: the recruiters
     * are SASBO's own staff, and no name of theirs belongs in this file.
     */
    public record Member(String membershipNumber, String pack, String area, Integer workplaceRaNumber,
                         String employeeNumber, String title, String initials, String firstNames, String surname,
                         String identityType, String identityNumber, LocalDate dateOfBirth, String gender, String race,
                         LocalDate joinDate, Integer joinedMonthsBack, Integer joinDay, String personalEmail,
                         String preferredEmail, String phone, String address, String branch, String bankName,
                         String paymentType, String monthlySubscription, String subscriptionFrequency,
                         LocalDate dateLastChanged, boolean shopSteward, LocalDate shopStewardElectionDate,
                         String becCommitteeCode, boolean memnet, Integer recruiter) {

        /** Whether the member joined in one of the three past months, and is dated by the clock. */
        public boolean joinsInThePast() {
            return joinedMonthsBack != null;
        }

        @Override
        public String toString() {
            return "DemonstrationPopulation.Member[" + membershipNumber + "]";
        }
    }

    /**
     * One change of a member's status in the month {@code monthsBack} before the clock's, on {@code day}: a leaving
     * with SASBO's deletion code (and, under code 2, the reason the member gave), a suspension with its code, or a
     * return to Active. Written through the row {@code MemberService.changeStatus} writes, by the actor {@code seed}.
     */
    public record Change(String membershipNumber, int monthsBack, int day, String toStatus, String reason,
                         Integer deletionCode, String reasonUnderCode, Integer suspensionCode) {
    }
}

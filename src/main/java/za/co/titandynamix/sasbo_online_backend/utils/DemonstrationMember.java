package za.co.titandynamix.sasbo_online_backend.utils;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One invented member of {@code data/demonstration_members.json} (month end 01.4), as the file states it -- a SEED
 * DTO, not the entity, for the reason {@link SeedDebitOrderMember} gives: its components ARE the file's keys, the
 * reader refuses a key it does not know, and {@link #toMember()} names every field it sets.
 *
 * <p>Invented, every one: a name from fixed lists, an identity number that passes the capture screen's check and
 * is distinct across the three seed files, an address at {@code example.org} and a telephone number in the range
 * the seed's other members use -- contact details that reach nobody (MR-SEC-14). Active, paying by PAYROLL
 * DEDUCTION, so no line, control total or listed exclusion of an A.C.B. run moves (MD34, BR36). Each stands at one
 * of SASBO's workplaces, named by its R/A number; the workplace is an organisation, the member is not SASBO's.
 *
 * <p>🔒 {@code toString()} names the membership number only.
 */
public record DemonstrationMember(
        String membershipNumber,
        Integer workplaceRaNumber,
        String employeeNumber,
        String title,
        String initials,
        String firstNames,
        String surname,
        String identityType,
        String identityNumber,
        LocalDate dateOfBirth,
        String gender,
        String race,
        String status,
        LocalDate joinDate,
        String personalEmail,
        String preferredEmail,
        String phone,
        String cellTel,
        String address,
        String branch,
        String bankName,
        String paymentType,
        BigDecimal monthlySubscription,
        String subscriptionFrequency,
        BigDecimal currentBalance,
        BigDecimal arrearsBalance,
        BigDecimal lastMonthBalance,
        LocalDate dateLastChanged) {

    /**
     * The file: who generated it and from what, how many members it holds -- the file states its size, and no test
     * types it (MV-37) -- the members, and where each of the seed's other 310 members stands (MR-ROB-32).
     */
    public record SeedFile(String generator, long seed, int count, Map<String, Object> generatedFrom,
                           List<DemonstrationMember> members, List<Placement> placements) {
    }

    /** Where one of the seed's other members -- {@code members.json}'s or the debit-order file's -- stands. */
    public record Placement(String membershipNumber, Integer workplaceRaNumber) {
    }

    /** A new member carrying every field the file states -- each one named, none by reflection; no workplace yet. */
    public Member toMember() {
        Member member = new Member();
        member.setMembershipNumber(membershipNumber);
        member.setEmployeeNumber(employeeNumber);
        member.setTitle(title);
        member.setInitials(initials);
        member.setFirstNames(firstNames);
        member.setSurname(surname);
        member.setIdentityType(identityType);
        member.setIdentityNumber(identityNumber);
        member.setDateOfBirth(dateOfBirth);
        member.setGender(gender);
        member.setRace(race);
        member.setStatus(status);
        member.setJoinDate(joinDate);
        member.setPersonalEmail(personalEmail);
        member.setPreferredEmail(preferredEmail);
        member.setPhone(phone);
        member.setCellTel(cellTel);
        member.setAddress(address);
        member.setBranch(branch);
        member.setBankName(bankName);
        member.setPaymentType(paymentType);
        member.setMonthlySubscription(monthlySubscription);
        member.setSubscriptionFrequency(subscriptionFrequency);
        member.setCurrentBalance(currentBalance);
        member.setArrearsBalance(arrearsBalance);
        member.setLastMonthBalance(lastMonthBalance);
        member.setDateLastChanged(dateLastChanged);
        member.setIsRepresentative(Boolean.FALSE);
        return member;
    }

    @Override
    public String toString() {
        return "DemonstrationMember[" + membershipNumber + "]";
    }
}

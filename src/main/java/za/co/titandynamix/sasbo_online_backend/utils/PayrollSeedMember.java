package za.co.titandynamix.sasbo_online_backend.utils;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One invented member of {@code data/payroll_members.json}, as the file states it -- a seed DTO, never the entity
 * (the debit-order seed's lesson, F65: the components ARE the file's keys and the reader refuses one it does not
 * know). {@code employer} is the payroll employer's code and {@code role} what the demonstration does with the member
 * on that employer's files ({@code PayrollSeeder}); neither is stored on the member. {@code workplaceRaNumber} is the
 * workplace of that employer the member stands at, where SASBO's workplaces are on record (the switch at all).
 *
 * <p>🔒 Invented people only; {@code toString()} names the membership number alone.
 */
public record PayrollSeedMember(
        String membershipNumber,
        String employer,
        Integer workplaceRaNumber,
        String role,
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
        String bankName,
        String paymentType,
        BigDecimal monthlySubscription,
        String subscriptionFrequency,
        BigDecimal arrearsBalance,
        LocalDate dateLastChanged) {

    /** Someone on an employer's file who is no member -- the demonstration's "not a member" (PAY-12). Never seeded. */
    public record Stranger(String employer, String role, String employeeNumber, String title, String initials,
                           String firstNames, String surname, String identityNumber) {

        @Override
        public String toString() {
            return "Stranger[" + employer + ", " + role + "]";
        }
    }

    /** The file's own wrapper. */
    public record SeedFile(String generator, long seed, List<PayrollSeedMember> members, List<Stranger> strangers) {
    }

    /** A new member carrying every field the file states, each one named. */
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
        member.setBankName(bankName);
        member.setPaymentType(paymentType);
        member.setMonthlySubscription(monthlySubscription);
        member.setSubscriptionFrequency(subscriptionFrequency);
        member.setCurrentBalance(BigDecimal.ZERO.setScale(2));
        member.setArrearsBalance(arrearsBalance);
        member.setLastMonthBalance(BigDecimal.ZERO.setScale(2));
        member.setDateLastChanged(dateLastChanged);
        return member;
    }

    @Override
    public String toString() {
        return "PayrollSeedMember[" + membershipNumber + "]";
    }
}

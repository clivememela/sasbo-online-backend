package za.co.titandynamix.sasbo_online_backend.utils;

import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One member of {@code data/debit_order_members.json}, as the file states it -- a SEED DTO, not the
 * entity.
 *
 * <h2>Why not read the file straight into {@code Member}</h2>
 * {@code members.json} is read into the entity, through the request-body rules, and it has cost two
 * defects: A61 (READ_ONLY discarded every membership number) and F65 (the file says {@code email},
 * the entity reads {@code personalEmail}, unknown keys are ignored -- so not one seeded member stores
 * an address). This record is the seed DTO {@link DataSeeder} says is the real answer: its components
 * ARE the file's keys, the reader refuses a key it does not know, and {@link #toMember()} names every
 * field it sets. A misspelt key in the generator fails the start, loudly, instead of losing a field.
 *
 * <p>🔒 Carries an account number, so {@code toString()} names the membership number only.
 *
 * @param expectedOutcome what the population rule must make of this member: {@code QUALIFIES}, or the
 *                        one exclusion reason it must give. Read by the tests, never by the seeder.
 */
public record SeedDebitOrderMember(
        String membershipNumber,
        String expectedOutcome,
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
        String bankAccountNumber,
        String ibtNo,
        String paymentType,
        Integer debitDay,
        BigDecimal monthlySubscription,
        String subscriptionFrequency,
        BigDecimal currentBalance,
        BigDecimal arrearsBalance,
        BigDecimal lastMonthBalance,
        LocalDate dateLastChanged) {

    /** The file's own wrapper: who generated it, from which seed, and the members. */
    public record SeedFile(String generator, long seed, List<SeedDebitOrderMember> members) {
    }

    /** A new member carrying every field the file states -- each one named, none by reflection. */
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
        member.setBankAccountNumber(bankAccountNumber);
        member.setIbtNo(ibtNo);
        member.setPaymentType(paymentType);
        member.setDebitDay(debitDay);
        member.setMonthlySubscription(monthlySubscription);
        member.setSubscriptionFrequency(subscriptionFrequency);
        member.setCurrentBalance(currentBalance);
        member.setArrearsBalance(arrearsBalance);
        member.setLastMonthBalance(lastMonthBalance);
        member.setDateLastChanged(dateLastChanged);
        return member;
    }

    @Override
    public String toString() {
        return "SeedDebitOrderMember[" + membershipNumber + "]";
    }
}

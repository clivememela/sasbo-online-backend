package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.utils.SaIdParser;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final RepresentativeRepository representativeRepository;
    private final SubscriptionsRepository subscriptionsRepository;
    private final BankBranchRepository bankBranchRepository;
    private final BankRepository bankRepository;
    private final RepresentativeRoleRepository representativeRoleRepository;
    private final UnionStructureRepository unionStructureRepository;

    @Override
    public List<Member> findAll() {
        return this.memberRepository.findAll();
    }

    @Override
    public Optional<Member> findById(Integer id) {
        return this.memberRepository.findById(id);
    }

    @Override
    public Member create(Member member) {
        member.setId(null);
        resolveStructureAndRole(member);
        autoPopulateIdDerivedFields(member);
        autoPopulateBankDerivedFields(member);
        MemberValidator.validatePaymentType(member);
        return this.memberRepository.save(member);
    }

    @Override
    public Optional<Member> update(Integer id, Member updated) {
        return this.memberRepository.findById(id).map(existing -> {
            resolveStructureAndRole(updated);
            this.memberMapper.updateFromMember(updated, existing);
            resolveAreasRep(updated.getAreasRep(), existing);
            autoPopulateIdDerivedFields(existing);
            autoPopulateBankDerivedFields(existing);
            MemberValidator.validatePaymentType(existing);
            return this.memberRepository.save(existing);
        });
    }

    @Override
    public void resolveStructureAndRole(Member member) {
        if (Boolean.TRUE.equals(member.getIsRepresentative())) {
            if (member.getStructureId() != null) {
                this.unionStructureRepository.findById(member.getStructureId())
                        .ifPresentOrElse(member::setStructure, () -> member.setStructure(null));
            } else {
                member.setStructure(null);
            }

            if (member.getRoleId() != null) {
                this.representativeRoleRepository.findById(member.getRoleId())
                        .ifPresentOrElse(member::setRole, () -> member.setRole(null));
            } else {
                member.setRole(null);
            }
        } else {
            // Not a representative, clear these fields
            member.setStructure(null);
            member.setRole(null);
            member.setElectionDate(null);
        }
    }

    /**
     * Resolves the transient areasRep display name to a Representative entity.
     */
    private void resolveAreasRep(String areasRep, Member existing) {
        if (areasRep == null) {
            return;
        }
        if (areasRep.isBlank()) {
            existing.setRecruitmentRepresentative(null);
            return;
        }
        existing.setRecruitmentRepresentative(
                this.representativeRepository.findByFullName(areasRep.trim()).orElse(null));
    }

    @Override
    public Optional<Member> updateBanking(Integer id, Member banking) {
        return this.memberRepository.findById(id).map(existing -> {
            existing.setBankName(banking.getBankName());
            existing.setBankAccountNumber(banking.getBankAccountNumber());
            existing.setBranch(banking.getBranch());
            existing.setIbtNo(banking.getIbtNo());
            existing.setWorkIbtNo(banking.getWorkIbtNo());
            existing.setBankAddress(banking.getBankAddress());
            existing.setWorkBankAddress(banking.getWorkBankAddress());
            existing.setPaymentType(banking.getPaymentType());
            existing.setDebitDay(banking.getDebitDay());
            // Auto-populate bank address and debit day from reference data
            // (monthly subscription is NOT auto-set here — only through maintenance)
            autoPopulateBankAddress(existing);
            autoPopulateDebitDay(existing);
            MemberValidator.validatePaymentType(existing);
            return this.memberRepository.save(existing);
        });
    }

    @Override
    public Optional<Member> updateMonthlySubscription(Integer id, String monthlySubscription) {
        return this.memberRepository.findById(id).map(existing -> {
            existing.setMonthlySubscription(monthlySubscription);
            return this.memberRepository.save(existing);
        });
    }

    // ============================================================
    // Auto-population helpers for ID-derived fields
    // ============================================================

    /**
     * When identityType is "ID" (South African ID), extracts date of birth and gender
     * from the identityNumber and sets them on the member. These fields are read-only
     * on the frontend when an SA ID is used.
     * <p>
     * When identityType is "Passport", the frontend sends dateOfBirth and gender
     * directly (user-selected via date picker and dropdown), so no override occurs.
     */
    private void autoPopulateIdDerivedFields(Member member) {
        if ("ID".equalsIgnoreCase(member.getIdentityType())
                && member.getIdentityNumber() != null
                && !member.getIdentityNumber().isBlank()) {
            SaIdParser.extractDateOfBirth(member.getIdentityNumber())
                    .ifPresent(member::setDateOfBirth);
            SaIdParser.extractGender(member.getIdentityNumber())
                    .ifPresent(member::setGender);
        }
    }

    // ============================================================
    // Auto-population helpers for bank-derived fields
    // ============================================================

    /**
     * Auto-populates all bank-derived fields on a member:
     * - monthlySubscription (read-only — overwrites user value)
     * - debitDay (pre-selected by bank)
     * - bankAddress (auto-generated from BankBranch)
     */
    private void autoPopulateBankDerivedFields(Member member) {
        if (member.getBankName() == null || member.getBankName().isBlank()) {
            return;
        }
        // Single lookup for Subscriptions by bank name
        Optional<Subscriptions> sub = resolveSubscriptionByBankName(member.getBankName());
        sub.ifPresent(s -> {
            member.setMonthlySubscription(s.getSubscriptionAmount().toPlainString());
            member.setDebitDay(s.getAcbDebitDay());
        });
        autoPopulateBankAddress(member);
    }

    /**
     * Resolves the Subscriptions reference record for a given bank name.
     * Returns the first match (a bank typically has one active subscription).
     */
    private Optional<Subscriptions> resolveSubscriptionByBankName(String bankName) {
        return this.bankRepository.findByNameIgnoreCase(bankName)
                .flatMap(bank -> this.subscriptionsRepository
                        .findByBankIdWithBank(bank.getId().longValue())
                        .stream()
                        .findFirst());
    }

    /**
     * Auto-populates debitDay from the Subscriptions reference table
     * based on the member's bank. This is the default value; it can be
     * overridden if the member requests a change.
     */
    private void autoPopulateDebitDay(Member member) {
        if (member.getBankName() == null || member.getBankName().isBlank()) {
            return;
        }
        resolveSubscriptionByBankName(member.getBankName())
                .ifPresent(s -> member.setDebitDay(s.getAcbDebitDay()));
    }

    /**
     * Auto-populates bankAddress from the BankBranch's physical address
     * based on the member's bank name and branch.
     */
    private void autoPopulateBankAddress(Member member) {
        if (member.getBankName() == null || member.getBankName().isBlank()
                || member.getBranch() == null || member.getBranch().isBlank()) {
            return;
        }
        this.bankBranchRepository
                .findByBank_NameIgnoreCaseAndBranchNameIgnoreCase(
                        member.getBankName(), member.getBranch())
                .ifPresent(branch -> {
                    String formatted = formatPhysicalAddress(branch.getPhysicalAddress());
                    if (formatted != null && !formatted.isBlank()) {
                        member.setBankAddress(formatted);
                    }
                });
    }

    /**
     * Formats a PhysicalAddress into a single-line string.
     * Skips null/blank parts, joins with commas.
     */
    private String formatPhysicalAddress(PhysicalAddress addr) {
        if (addr == null) {
            return null;
        }
        return Stream.of(
                        addr.getStreetNumber(),
                        addr.getStreetName(),
                        getSuburbName(addr),
                        getCityName(addr),
                        getProvinceName(addr),
                        addr.getPostalCode())
                .filter(part -> part != null && !part.isBlank())
                .map(String::trim)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

    }

    private String getSuburbName(PhysicalAddress addr) {
        return addr.getSuburb() != null ? addr.getSuburb().getName() : null;
    }

    private String getCityName(PhysicalAddress addr) {
        return addr.getCity() != null ? addr.getCity().getName() : null;
    }

    private String getProvinceName(PhysicalAddress addr) {
        return addr.getProvince() != null ? addr.getProvince().getName() : null;
    }

}

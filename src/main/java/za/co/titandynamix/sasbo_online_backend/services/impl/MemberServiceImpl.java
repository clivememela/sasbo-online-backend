package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;
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
    private final JobGradeRepository jobGradeRepository;
    private final JobGradeService jobGradeService;

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
        resolveJobGrade(member);
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
            resolveJobGrade(existing);
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
     * Resolves the client-supplied job grade, and SAYS WHAT IT DID (ux.md 6c, clause 2).
     *
     * <p>The API accepts the grade as a plain string -- the value shown in the drop-down,
     * sourced from the job_grades table for the selected bank -- deserialized into a
     * transient JobGrade. Grades are bank-specific, so the member's bankName is the
     * lookup key.
     *
     * <h2>What changed in 02.5, and why</h2>
     * This method used to end {@code .orElse(null)}: a grade the master list did not
     * contain was discarded in silence, the save returned success, and the field came
     * back empty. That was tolerable only while the field was a closed drop-down. It is
     * not tolerable now: the seeded list covers 3 banks of 68, the stakeholder asked for
     * free text precisely because of the other 65, and "type a grade, be told it saved,
     * find it gone" is the A31 class of defect in the field we were asked to open up.
     *
     * <p>So the three outcomes are named, and the outcome travels back on the member:
     * MATCHED (resolved to a master row, the user's spelling quietly corrected),
     * CAPTURED (genuinely new, stored against this bank and flagged unverified), and
     * CLEARED (blank or unusable -- said out loud rather than being a silent null).
     *
     * <p>🔑 The comparison is {@link JobGradeMatcher}'s, not a local {@code equalsIgnoreCase}.
     * The client mirrors the same rule against the same fixture. A matching rule living
     * in two files is how A6, A7, A17 and A9 all happened.
     */
    private void resolveJobGrade(Member member) {
        JobGrade submitted = member.getJobGrade();
        if (submitted == null) {
            // Nothing was supplied. That is not the same as clearing a value, so no
            // outcome is reported and whatever the member already had is left alone.
            return;
        }
        if (submitted.getId() != null) {
            // Flat rather than two two-statement lambdas: it sets the same pair of
            // fields either way, and this now reads like the string branch below.
            JobGrade byId = this.jobGradeRepository.findById(submitted.getId()).orElse(null);
            member.setJobGrade(byId);
            member.setJobGradeOutcome(byId != null ? JobGradeOutcome.MATCHED : JobGradeOutcome.CLEARED);
            return;
        }

        String grade = submitted.getGrade();
        if (JobGradeMatcher.isBlank(grade)) {
            member.setJobGrade(null);
            member.setJobGradeOutcome(JobGradeOutcome.CLEARED);
            return;
        }

        JobGrade matched = this.jobGradeService.findByBankIdentifier(member.getBankName()).stream()
                .filter(jg -> JobGradeMatcher.matches(grade, jg.getGrade()))
                .findFirst()
                .orElse(null);
        if (matched != null) {
            member.setJobGrade(matched);
            // A grade that resolves to a row somebody typed earlier is still CAPTURED
            // from the user's point of view -- it is not yet part of the master list,
            // and telling them "matched" would imply a confirmation that has not happened.
            member.setJobGradeOutcome(Boolean.TRUE.equals(matched.getVerified())
                    ? JobGradeOutcome.MATCHED
                    : JobGradeOutcome.CAPTURED);
            return;
        }

        JobGrade captured = this.jobGradeService.capture(member.getBankName(), grade);
        member.setJobGrade(captured);
        member.setJobGradeOutcome(captured != null ? JobGradeOutcome.CAPTURED : JobGradeOutcome.CLEARED);
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
            // Grades are bank-specific: re-resolve the current grade against the (possibly changed) bank
            JobGrade currentGrade = existing.getJobGrade();
            if (currentGrade != null) {
                existing.setJobGrade(new JobGrade(currentGrade.getGrade()));
                resolveJobGrade(existing);
            }
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
     * A17. This compared against {@code "ID"} while the browser sends
     * {@code "I.D. Number"}, so the derivation below had never run for a single
     * real capture. Both spellings are accepted: the browser's value is the one
     * that matters, and the short form is kept because older rows carry it.
     */
    private static boolean isSouthAfricanId(String identityType) {
        return "I.D. Number".equalsIgnoreCase(identityType)
                || "ID".equalsIgnoreCase(identityType);
    }

    /**
     * For a South African ID, extracts date of birth and gender from the
     * identity number and sets them on the member. Those fields are read-only on
     * the frontend when an SA ID is used.
     * <p>
     * For a passport the frontend sends {@code dateOfBirth} and {@code gender}
     * directly -- a passport number carries no date to derive from -- so no
     * override occurs.
     */
    private void autoPopulateIdDerivedFields(Member member) {
        if (isSouthAfricanId(member.getIdentityType())
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

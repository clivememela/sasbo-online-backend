package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

@Service
@Slf4j
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
    private final AreaRepository areaRepository;
    private final RecruitmentChannelRepository recruitmentChannelRepository;

    @Override
    public List<Member> findAll() {
        return this.memberRepository.findAll();
    }

    @Override
    public Optional<Member> findById(Integer id) {
        return this.memberRepository.findById(id);
    }

    /**
     * 03.6 / A15 -- the member write path is a MULTI-ENTITY write and can half-land.
     *
     * <p>Capture is not one insert. It resolves relations, and since 02.5 it can also
     * INSERT a job grade: an administrator who types a grade the master list does not
     * have causes `job_grades` to gain a row before `members` gains one. Without a
     * boundary, a failure between those two writes -- a validation error, a constraint,
     * a dropped connection -- leaves the captured grade behind with no member attached
     * to it, and nothing logged, nothing failed and nothing to notice it.
     *
     * <p>🔑 02.5 made this worse and this repays it. Before that unit the capture path
     * issued one write; adding `capture()` gave it two, which is exactly the shape A15
     * describes.
     *
     * <p>⚠️ SCOPED TO THIS PATH, deliberately, and A15 stays open. The register row
     * covers all nineteen services; this fixes the one the arc owns and can prove. The
     * pin on `everyMultiWriteMethodIsTransactional` therefore stays exactly where it
     * is -- removing it would claim a repair that has not happened, and
     * `theExistingBoundariesAreStillThere` is written with `contains` rather than
     * `containsExactly` precisely so that adding a third boundary is not itself a
     * failure.
     */
    @Override
    @Transactional
    public Member create(Member member) {
        member.setId(null);
        resolveStructureAndRole(member);
        resolveRecruiter(member.getRecruitmentRep(), member);
        resolveAreaRecruited(member.getAreaRecruited(), member);
        resolveRecruitedHow(member.getRecruitedHow(), member);
        autoPopulateIdDerivedFields(member);
        autoPopulateBankDerivedFields(member);
        resolveJobGrade(member);
        assignMembershipNumber(member);
        MemberValidator.validatePaymentType(member);
        return this.memberRepository.save(member);
    }

    /**
     * Gives a new member its membership number (03.4).
     *
     * <p>🔴 NOTHING DID THIS BEFORE. `membershipNumber` is on the entity,
     * `existsByMembershipNumber` is on the repository, and nothing ever called it --
     * so every member captured through the API was stored with a NULL membership
     * number, while all 160 seeded members have one. The field looked implemented
     * from every angle except the one that mattered.
     *
     * <p>The number comes from a SEQUENCE, not from "highest + 1, check it is free":
     * see {@code MemberRepository.nextMembershipNumber} for why that check-then-act
     * shape is a race whose damage cannot be undone on this API.
     *
     * <p>A number already supplied is kept. That is not a courtesy to the client --
     * `MemberMapper` ignores `membershipNumber` on write and `SR-1` blocks it on the
     * request body, so the only caller that can set one is a migration or an import,
     * and overwriting it there would be worse than useless.
     */
    private void assignMembershipNumber(Member member) {
        if (member.getMembershipNumber() != null && !member.getMembershipNumber().isBlank()) {
            return;
        }
        member.setMembershipNumber("M-" + this.memberRepository.nextMembershipNumber());
    }

    @Override
    @Transactional
    public Optional<Member> update(Integer id, Member updated) {
        return this.memberRepository.findById(id).map(existing -> {
            resolveStructureAndRole(updated);
            this.memberMapper.updateFromMember(updated, existing);
            resolveAreasRep(updated.getAreasRep(), existing);
            // ♻️ Each of these was two statements before P3's refactor pass: copy the
            // transient name from `updated` onto `existing`, then resolve `existing`.
            // The copy was already redundant -- `updateFromMember` maps all four
            // transients by name one line above -- and reading the name straight off
            // `updated` removes both the copy and the need to know that.
            resolveRecruiter(updated.getRecruitmentRep(), existing);
            resolveAreaRecruited(updated.getAreaRecruited(), existing);
            resolveRecruitedHow(updated.getRecruitedHow(), existing);
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
     * ♻️ THE ONE SHAPE EVERY REFERENCE RESOLVER ON THIS PATH HAS: a NAME on the wire,
     * an ENTITY in the column.
     *
     * <p>P3's refactor pass extracted this. The phase wrote three copies of it in one
     * commit range -- recruiter, area recruited, recruitment channel -- and a FOURTH
     * had been sitting beside them since before the arc opened ({@code areasRep}).
     * Four hand-written copies of eight lines, all doing exactly this:
     *
     * <pre>
     *   name == null   ->  leave the column alone   (the field was not mentioned)
     *   name is blank  ->  set the column to null   (the field was cleared)
     *   otherwise      ->  look it up, trimmed; an unmatched name stores null
     * </pre>
     *
     * <p>🔑 THE NULL / BLANK DISTINCTION IS THE WHOLE REASON THIS IS SHARED. "Absent"
     * and "cleared" are different requests, and conflating them is how a PUT that
     * never mentioned a field ends up wiping it. Written out four times it is four
     * chances to get that backwards; written once it is a property of the mechanism.
     *
     * <p>⚠️ A name that matches nothing stores {@code null}, silently, in all four --
     * that behaviour is preserved here exactly as it was, not endorsed. It is recorded
     * as a finding for {@code RF}: a resolver that cannot find the row the user picked
     * from a drop-down it was given should probably say so rather than drop it.
     */
    private <T> void resolveByName(String name, Function<String, Optional<T>> lookup, Consumer<T> assign) {
        if (name == null) {
            return;
        }
        assign.accept(name.isBlank() ? null : lookup.apply(name.trim()).orElse(null));
    }

    /**
     * Resolves the RECRUITER -- the person who signed the member up (R1, R5).
     *
     * <p>🔴 Nothing on this entity was called {@code recruitmentRep} before 03.3, so
     * the name the capture form had always sent was ignored as an unknown property and
     * the recruiter was dropped on every capture, with the save reporting success.
     */
    private void resolveRecruiter(String name, Member target) {
        resolveByName(name, this.representativeRepository::findByFullName,
                target::setRecruitmentRepresentative);
    }

    /**
     * Resolves the AREA the member was recruited in (R2, RE-3).
     *
     * <p>🔑 The old field captured a PERSON under an area-shaped name. This one
     * resolves to the {@code areas} table, through {@code members.recruited_area_id}.
     *
     * <p>🔴 It briefly wrote to {@code area_id} instead, and V24 corrected that:
     * {@code Member.area} means "the area this member BELONGS to", which is a different
     * fact with a different lifetime. The areas themselves are seeded from the distinct
     * areas of the bank branches; see {@code DataSeeder}.
     */
    private void resolveAreaRecruited(String name, Member target) {
        resolveByName(name, this.areaRepository::findByNameIgnoreCase, target::setRecruitedArea);
    }

    /**
     * Resolves HOW the member was recruited to a {@code RecruitmentChannel} (03.8).
     *
     * <p>The stakeholder's item (1) for the Recruitment Channel tab.
     */
    private void resolveRecruitedHow(String name, Member target) {
        resolveByName(name, this.recruitmentChannelRepository::findByNameIgnoreCase,
                target::setRecruitmentChannel);
    }

    /**
     * Resolves the transient areasRep display name to a Representative entity.
     *
     * <p>⚠️ It writes {@link Member#setRecruitmentRepresentative}, the SAME column
     * {@link #resolveRecruiter} writes, from a different wire field. On the update
     * path both run, so whichever is non-null last wins. That is not a defect today
     * -- the register's edit screen sends {@code areasRep} and capture sends
     * {@code recruitmentRep}, never both -- but two names for one column is the
     * finding {@code 03.10} is already open on, and this is a third face of it.
     * Recorded, not repaired: a refactor pass does not resolve a modelling question.
     */
    private void resolveAreasRep(String areasRep, Member existing) {
        resolveRecruiter(areasRep, existing);
    }

    @Override
    @Transactional
    public Optional<Member> updateBanking(Integer id, Member banking) {
        return this.memberRepository.findById(id).map(existing -> {
            existing.setBankName(banking.getBankName());
            existing.setBankAccountNumber(banking.getBankAccountNumber());
            existing.setBranch(banking.getBranch());
            existing.setIbtNo(banking.getIbtNo());
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
    /**
     * Fills the three fields the bank decides: subscription, debit day, address.
     *
     * <h2>🔴 The debit day is conditional now, and that is the whole point</h2>
     * This method used to assign {@code s.getAcbDebitDay()} unconditionally, and
     * {@link MemberValidator} then rejected the result against {@link DebitDay}'s
     * permitted range — <b>for eleven of the fifteen seeded subscription rows</b>
     * (Absa 5, FNB 10, Capitec 1, Standard Bank 15…). So the service overwrote a
     * value the administrator had chosen, which WAS valid, with one that was not,
     * and then threw a message blaming a debit day they never entered and could not
     * change. A debit-order member could not be captured for most banks at all.
     *
     * <p>🗣️ <b>Settled by the owner, 2026-09-09: the 20–31 range is right and the
     * banks' seeded ACB days are placeholder data.</b> So an out-of-range day is not
     * applied, and the administrator's own choice stands.
     *
     * <p>🔑 <b>The invariant, stated because it is the thing worth keeping:</b> this
     * service never assigns a debit day that its own validator would refuse.
     * Whichever way the reference data is corrected later, assigning-then-rejecting
     * is incoherent, and {@code DebitDayOverwriteConflictTest} now guards that
     * property rather than pinning the defect.
     *
     * <p>⚠️ It is NOT silent. When the bank's day cannot be used the fact is logged,
     * because a derivation that quietly declines to derive is the shape
     * {@code autoPopulateBankAddress} spent this whole arc being caught by.
     */
    private void autoPopulateBankDerivedFields(Member member) {
        if (member.getBankName() == null || member.getBankName().isBlank()) {
            return;
        }
        // Single lookup for Subscriptions by bank name
        Optional<Subscriptions> sub = resolveSubscriptionByBankName(member.getBankName());
        sub.ifPresent(s -> {
            member.setMonthlySubscription(s.getSubscriptionAmount().toPlainString());
            Integer bankDay = s.getAcbDebitDay();
            if (bankDay != null && DebitDay.isInRange(bankDay)) {
                member.setDebitDay(bankDay);
            } else {
                log.warn("{} has an ACB debit day of {}, outside {} - the member's own "
                                + "choice of {} stands. The bank's subscription record and the "
                                + "permitted range disagree; the range is authoritative.",
                        member.getBankName(), bankDay, DebitDay.message(), member.getDebitDay());
            }
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

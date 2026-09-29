package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusChangeRequest;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruiterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberStatusChangeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;
import za.co.titandynamix.sasbo_online_backend.services.MemberService;
import za.co.titandynamix.sasbo_online_backend.services.MemberTrail;
import za.co.titandynamix.sasbo_online_backend.services.MemberWorkplaces;
import za.co.titandynamix.sasbo_online_backend.utils.SaIdParser;
import za.co.titandynamix.sasbo_online_backend.validator.MemberValidator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
    private final RecruiterRepository recruiterRepository;
    private final MemberStatusChangeRepository statusChanges;
    /** Month end 01.3: the workplace a save names, looked for before anything is written. */
    private final MemberWorkplaces workplaces;
    /** Month end 03.5: the member's history, written inside each of the four saves. */
    private final MemberTrail trail;

    @Override
    @Transactional
    public Optional<Member> changeStatus(Integer memberId, MemberStatusChangeRequest change) {
        return this.memberRepository.findById(memberId).map(member -> {
            if (change.toStatus().equals(member.getStatus())) {
                return member;
            }
            MemberStatusChange row = new MemberStatusChange();
            row.setMemberId(member.getId());
            row.setFromStatus(member.getStatus());
            row.setToStatus(change.toStatus());
            row.setReason(change.reason());
            row.setRunId(change.runId());
            row.setLineId(change.lineId());
            row.setChangedBy(change.actor());
            row.setChangedAt(change.at());
            this.statusChanges.save(row);
            // The status and nothing else: no validator runs here, by design (R-ROB-I).
            member.setStatus(change.toStatus());
            return this.memberRepository.save(member);
        });
    }

    /**
     * Month end 03.1: the same narrow change as ACB's -- the status and nothing else, no validator (R-ROB-I) -- with
     * SASBO's code and a note, on the view the caller had. The member's row is locked for the change, so of two
     * changes on one view the second finds the first's status: the same status is a no-op that writes nothing (a
     * retry is safe, ME-3), any other is stale and writes nothing either (MV-5). The history row is written in the
     * same transaction as the status it records.
     */
    @Override
    @Transactional
    public Optional<StatusChangeOutcome> changeStatus(Integer memberId, StatusChange change) {
        return this.memberRepository.findByIdForStatusChange(memberId).map(member -> {
            if (change.toStatus().equals(member.getStatus())) {
                return new StatusChangeOutcome(member, null, false);
            }
            if (change.expectedStatus() != null && !change.expectedStatus().equals(member.getStatus())) {
                return new StatusChangeOutcome(member, null, true);
            }
            MemberStatusChange row = new MemberStatusChange();
            row.setMemberId(member.getId());
            row.setFromStatus(member.getStatus());
            row.setToStatus(change.toStatus());
            row.setReason(change.reason());
            row.setDeletionCodeId(change.deletionCodeId());
            row.setSuspensionCodeId(change.suspensionCodeId());
            row.setNote(change.note());
            row.setChangedBy(change.actor());
            row.setChangedAt(change.at());
            MemberStatusChange written = this.statusChanges.save(row);
            member.setStatus(change.toStatus());
            return new StatusChangeOutcome(this.memberRepository.save(member), written, false);
        });
    }

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
        // Month end 01.3, FIRST: a workplace not on record is refused before anything is resolved or written (ME-12).
        this.workplaces.resolve(member.getWorkplaceId(), member);
        resolveStructureAndRole(member);
        resolveRecruiter(member.getRecruitmentRep(), member);
        resolveAreaRecruited(member.getAreaRecruited(), member);
        resolveRecruitedHow(member.getRecruitedHow(), member);
        autoPopulateIdDerivedFields(member);
        autoPopulateBankDerivedFields(member);
        resolveJobGrade(member);
        assignMembershipNumber(member);
        // No previous day: on create, every out-of-range day is a new choice.
        MemberValidator.validatePaymentType(member, null);
        MemberValidator.validateAccountType(member, true);
        MemberValidator.validateStructures(member, null);
        Member saved = this.memberRepository.save(member);
        // 03.5: one row, "captured" -- inside the capture's transaction.
        this.trail.captured(saved);
        return saved;
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
            // 03.5, before ANYTHING changes the member: the history's picture of them as they were.
            Map<String, String> before = this.trail.snapshot(existing);
            // Month end 01.3, FIRST: a workplace not on record is refused before anything is merged or written (ME-12);
            // onto `existing`, because the merge ignores `workplace`. None named leaves it as it was (MD58).
            this.workplaces.resolve(updated.getWorkplaceId(), existing);
            // 🗣️ Decision ⑥. Read BEFORE the merge overwrites it: the range is
            // enforced on a day the caller changed, not on one it sent back unchanged.
            Integer previousDebitDay = existing.getDebitDay();
            String previousIsCosatu = existing.getIsCosatu();
            this.memberMapper.updateFromMember(updated, existing);
            // 🔴 R1-F1. This resolved onto `updated` -- the TRANSIENT request object --
            // one line ABOVE the merge that deliberately ignores `structure` and
            // `role`. So the resolved associations were thrown away with the object
            // that carried them: a PUT promoting a member to a role, or demoting one,
            // answered 200 and changed nothing. It has to run on `existing`, AFTER the
            // merge has carried `isRepresentative`, `roleId` and `structureId` across.
            resolveStructureAndRole(existing);
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
            MemberValidator.validatePaymentType(existing, previousDebitDay);
            // Not required here: an edit of any other field must not be refused for a type the
            // member was captured without (R-ROB-I). A null in the request keeps the stored one.
            MemberValidator.validateAccountType(existing, false);
            MemberValidator.validateStructures(existing, previousIsCosatu);
            Member saved = this.memberRepository.save(existing);
            this.trail.changed(saved.getId(), before, this.trail.snapshot(saved));
            return saved;
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
     *
     * <p>🗣️ 🔴 AND IT RESOLVED AGAINST THE WRONG TABLE UNTIL 03.10 WAS MEASURED. It
     * looked names up in {@code representatives}, which is seeded by nothing and has
     * never held a row -- so every capture resolved to {@code null} and the recruiter
     * was dropped again, quietly, by a different route. Meanwhile the union's 33 real
     * recruiter names sat in {@code recruiters}, referenced by no foreign key and
     * served by no controller.
     *
     * <p>🔑 The owner settled it on 2026-09-09: a RECRUITER signed the member up and
     * lives in {@code recruiters}; a REPRESENTATIVE sits on a structure and stays
     * Edit Member's business. Two concepts that one column had been serving.
     */
    private void resolveRecruiter(String name, Member target) {
        resolveByName(name, this::selectableRecruiterNamed, target::setRecruiter);
    }

    /**
     * A recruiter a member may actually be attributed to, or nothing.
     *
     * <p>🗣️ 🔴 <b>THE VACANCY RULE HAS TO LIVE HERE TOO, and the drop-down is not
     * where it holds.</b> The owner decided on 2026-09-10 that an unfilled post is
     * not selectable, and {@code RecruiterServiceImpl.findSelectable()} takes the
     * three {@code VACANT} rows out of {@code GET /api/recruiters}. That is a
     * SUGGESTION. {@code POST /api/members} accepts a name, not a chosen option, so
     * a filter that exists only in the browser is one {@code curl} away from being
     * no rule at all -- and this API is {@code permitAll()}.
     *
     * <p>One predicate, {@link Recruiter#isVacancy}, shared by both sides rather
     * than written twice. This arc has now found the same shape three times
     * ({@code area_id}, {@code job_grades.description}, recruiter/representative):
     * a rule stated in two places drifts, and the copy nobody is looking at is the
     * one that ends up wrong.
     *
     * <p>⚠️ An excluded name resolves to EMPTY, so {@code resolveByName} stores
     * {@code null} -- the member is captured with no recruiter rather than rejected.
     * Deliberate, and narrower than it looks: the recruiter is optional by the
     * stakeholder's own instruction ("where applicable"), so a member with none is
     * an ordinary member, whereas a 400 would fail a save over a field that was
     * never required. The silent-null behaviour of unmatched names is unchanged and
     * still recorded as an {@code RF} finding.
     */
    private Optional<Recruiter> selectableRecruiterNamed(String name) {
        return this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .filter(recruiter -> !Recruiter.isVacancy(recruiter));
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
     * <p>✅ 03.10 CLOSED THE COLLISION. This used to write the same column
     * {@link #resolveRecruiter} writes, from a different wire field, so on the update
     * path whichever ran last won. They now write different columns because they mean
     * different things: {@code areasRep} is the register's REPRESENTATIVE link and
     * stays pointed at {@code representatives}; the capture form's recruiter goes to
     * {@link Member#setRecruiter}. The modelling question is answered, so the
     * mechanism no longer has to be shared to hide it.
     */
    private void resolveAreasRep(String areasRep, Member existing) {
        resolveByName(areasRep, this.representativeRepository::findByFullName,
                existing::setRecruitmentRepresentative);
    }

    @Override
    @Transactional
    public Optional<Member> updateBanking(Integer id, Member banking) {
        return this.memberRepository.findById(id).map(existing -> {
            // 03.5, before anything changes the member.
            Map<String, String> before = this.trail.snapshot(existing);
            // 🗣️ Decision ⑥, as in `update`: read the stored day before it is replaced.
            Integer previousDebitDay = existing.getDebitDay();
            existing.setBankName(banking.getBankName());
            existing.setBankAccountNumber(banking.getBankAccountNumber());
            existing.setAccountType(banking.getAccountType());
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
            applyBankDebitDayAndAddress(existing);
            MemberValidator.validatePaymentType(existing, previousDebitDay);
            // Banking details saved as a debit order carry the account's type: this is the one screen
            // where the type is the thing being edited (00.5).
            MemberValidator.validateAccountType(existing, true);
            Member saved = this.memberRepository.save(existing);
            this.trail.changed(saved.getId(), before, this.trail.snapshot(saved));
            return saved;
        });
    }

    /**
     * 03.5: a TRANSACTION of its own now -- the amount and its history row are written together, or neither is.
     */
    @Override
    @Transactional
    public Optional<Member> updateMonthlySubscription(Integer id, BigDecimal monthlySubscription) {
        return this.memberRepository.findById(id).map(existing -> {
            Map<String, String> before = this.trail.snapshot(existing);
            existing.setMonthlySubscription(monthlySubscription);
            Member saved = this.memberRepository.save(existing);
            this.trail.changed(saved.getId(), before, this.trail.snapshot(saved));
            return saved;
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
        // Single lookup for Subscriptions by bank name
        resolveSubscriptionFor(member).ifPresent(s -> {
            member.setMonthlySubscription(s.getSubscriptionAmount());
            applyBankDebitDay(member, s);
        });
        autoPopulateBankAddress(member);
    }

    /**
     * The bank-derived fields a BANKING edit may touch: the debit day and the
     * address, and deliberately <b>not</b> the subscription amount.
     *
     * <p>🔴 R1-F2. {@code updateBanking} used to call a second, UNGUARDED
     * derivation — {@code autoPopulateDebitDay} — which assigned
     * {@code acbDebitDay} whatever its value, so the service set a day and then
     * its own validator refused it. That is the assign-then-reject defect the
     * owner settled on 2026-09-09, fixed on {@code create} and {@code update} and
     * left standing on the third write path, where no test called the service.
     *
     * <p>🔑 It does not simply delegate to {@code autoPopulateBankDerivedFields}.
     * That method also re-prices the member from the bank's subscription row, and
     * the amount is maintenance's to change — not a side effect of correcting a
     * bank account number. {@code updateBankingLeavesTheSubscriptionAlone} is the
     * test that holds the two apart.
     */
    private void applyBankDebitDayAndAddress(Member member) {
        resolveSubscriptionFor(member).ifPresent(s -> applyBankDebitDay(member, s));
        autoPopulateBankAddress(member);
    }

    /**
     * Applies the bank's ACB day ONLY when this API would accept it.
     *
     * <p>⚠️ It is NOT silent. When the bank's day cannot be used the fact is
     * logged, because a derivation that quietly declines to derive is the shape
     * {@code autoPopulateBankAddress} spent this whole arc being caught by.
     */
    private void applyBankDebitDay(Member member, Subscriptions subscription) {
        Integer bankDay = subscription.getAcbDebitDay();
        if (bankDay != null && DebitDay.isInRange(bankDay)) {
            member.setDebitDay(bankDay);
        } else {
            log.warn("{} has an ACB debit day of {}, outside {} - the member's own "
                            + "choice of {} stands. The bank's subscription record and the "
                            + "permitted range disagree; the range is authoritative.",
                    member.getBankName(), bankDay, DebitDay.message(), member.getDebitDay());
        }
    }

    /**
     * The subscription row for this member's bank, or empty when no bank is named.
     *
     * <p>Returns the first match — a bank typically has one active subscription.
     *
     * <p>♻️ Batch 0's refactor pass folded two methods into this one. Extracting the
     * guarded debit-day derivation left a `resolveSubscriptionFor(Member)` wrapping a
     * `resolveSubscriptionByBankName(String)` that nothing else called: a name, a
     * javadoc and a hop, all to move one field out of the object both callers already
     * had. Every caller on this path holds a Member.
     */
    private Optional<Subscriptions> resolveSubscriptionFor(Member member) {
        if (member.getBankName() == null || member.getBankName().isBlank()) {
            return Optional.empty();
        }
        return this.bankRepository.findByNameIgnoreCase(member.getBankName())
                .flatMap(bank -> this.subscriptionsRepository
                        .findByBankIdWithBank(bank.getId().longValue())
                        .stream()
                        .findFirst());
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

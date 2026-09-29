package za.co.titandynamix.sasbo_online_backend.services.impl;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.entity.subscription.Subscriptions;
import za.co.titandynamix.sasbo_online_backend.repository.SubscriptionsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AreaRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruiterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.RecruitmentChannelRepository;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.Synthetic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The capture path, and the highest-risk surface on the server.
 *
 * <h2>The defect class this file exists for</h2>
 * "A field that reads but does not write." A member is captured with 57 fields;
 * if the service quietly drops one on the way to the repository, the browser
 * shows what was typed, the save reports success, and the value is simply not
 * there. Nothing fails. It is found months later by a person whose bank details
 * are blank.
 *
 * <p>So the round-trip test <b>has no field list of its own</b>. It populates
 * every field by reflection, creates, captures what reached the repository, and
 * demands each field arrived. Field 58 is covered the day someone adds it.
 *
 * <h2>What the service is allowed to change</h2>
 * {@code create} is not a pass-through: it clears the id, resolves relations
 * and derives values from the identity number and the bank. Each of those is
 * declared below <b>with its reason</b>. Anything not declared has to survive
 * the journey untouched -- which is what makes a silently dropped field fail
 * here, by name.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MemberServiceImpl")
class MemberServiceImplTest {

    /**
     * Fields {@code create} is expected to change, and why. Everything else
     * must arrive at the repository exactly as it was handed in.
     *
     * <p>This is the only list in the test, and it describes the SERVICE's
     * behaviour rather than the entity's shape -- so adding a field to
     * {@code Member} does not require touching it, while changing what the
     * service derives does.
     */
    private static final Map<String, String> SERVICE_MAY_CHANGE = Map.ofEntries(
            Map.entry("id", "cleared before save, so a create cannot overwrite an existing row"),
            Map.entry("createdAt", "set by @PrePersist"),
            Map.entry("updatedAt", "set by @PrePersist"),
            Map.entry("dateOfBirth", "derived from the identity number"),
            Map.entry("gender", "derived from the identity number"),
            Map.entry("structure", "resolved from structureId"),
            Map.entry("role", "resolved from roleId"),
            Map.entry("jobGrade", "resolved against the bank's grades"),
            Map.entry("jobGradeOutcome",
                    "REPORTED by the service -- it is the answer to what resolveJobGrade did "
                            + "(MATCHED/CAPTURED/CLEARED), not something the client sends. "
                            + "Asserted in JobGradeResolutionTest"),
            Map.entry("recruiter",
                    "resolved from the recruiter NAME the capture form submits (03.10). It "
                            + "used to resolve through `representatives`, a table seeded by "
                            + "nothing, so every capture stored null while the union's 33 real "
                            + "recruiters sat in `recruiters` behind no controller"),
            Map.entry("recruitmentRepresentative",
                    "resolved from the recruiter name -- `areasRep` on update, and from 03.3 "
                            + "`recruitmentRep` on create, which nothing had ever bound"),
            Map.entry("recruitedArea",
                    "resolved from `areaRecruited` (RE-3), into its OWN column since V24. It "
                            + "briefly wrote to `area`, which means 'the area this member "
                            + "belongs to' -- a different fact with a different lifetime"),
            Map.entry("recruitmentChannel",
                    "resolved from `recruitedHow` (03.8) -- HOW the member was recruited. The "
                            + "reference table was seeded and served all along with no member "
                            + "pointing at it"),
            Map.entry("bankName", "derived from the branch"),
            Map.entry("branch", "derived from the banking details"),
            Map.entry("branchCode", "derived from the banking details"),
            Map.entry("debitDay", "derived from the bank's subscription record"),
            Map.entry("monthlySubscription", "derived from the bank's subscription record"),
            Map.entry("bankingDetails", "rebuilt from the flat banking fields"),
            Map.entry("user", "its id is cleared so a create cannot adopt an existing user"));

    @Mock private MemberRepository memberRepository;
    @Mock private MemberMapper memberMapper;
    @Mock private RepresentativeRepository representativeRepository;
    @Mock private SubscriptionsRepository subscriptionsRepository;
    @Mock private BankBranchRepository bankBranchRepository;
    @Mock private BankRepository bankRepository;
    @Mock private RepresentativeRoleRepository representativeRoleRepository;
    @Mock private UnionStructureRepository unionStructureRepository;
    @Mock private JobGradeRepository jobGradeRepository;
    @Mock private JobGradeService jobGradeService;
    @Mock private AreaRepository areaRepository;
    @Mock private RecruitmentChannelRepository recruitmentChannelRepository;
    @Mock private RecruiterRepository recruiterRepository;
    @Mock private za.co.titandynamix.sasbo_online_backend.repository.MemberStatusChangeRepository statusChanges;
    // Month end 01.3: the workplace a save names -- none is named here, so it is never asked.
    @Mock private za.co.titandynamix.sasbo_online_backend.services.MemberWorkplaces workplaces;
    // Month end 03.5: the member's history -- its snapshot is empty here, so it writes nothing.
    @Mock private za.co.titandynamix.sasbo_online_backend.services.MemberTrail trail;

    @Captor
    private ArgumentCaptor<Member> saved;

    private MemberServiceImpl service;

    @BeforeEach
    void setUp() {
        this.service = new MemberServiceImpl(
                this.memberRepository, this.memberMapper, this.representativeRepository,
                this.subscriptionsRepository, this.bankBranchRepository, this.bankRepository,
                this.representativeRoleRepository, this.unionStructureRepository,
                this.jobGradeRepository, this.jobGradeService, this.areaRepository,
                this.recruitmentChannelRepository, this.recruiterRepository, this.statusChanges,
                this.workplaces, this.trail);

        when(this.memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.bankRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(this.unionStructureRepository.findById(any())).thenReturn(Optional.empty());
        when(this.representativeRoleRepository.findById(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("create carries every captured field through to the repository")
    void createRoundTripsEveryField() {
        // No field list. Synthetic fills all of them, the captor reads back what
        // actually reached save(), and anything the service is allowed to
        // change is declared above with a reason.
        Member captured = Synthetic.populated(Member.class);
        // paymentType has to resolve, or validation rejects the whole member
        // before the save and this test would prove nothing about the fields.
        captured.setPaymentType("Payroll");
        // And the account type (00.5): a synthetic string is not one of the three, and the validator says so.
        captured.setAccountType("Transmission");
        // Likewise the structure answers: Synthetic sets every Boolean, so COSATU Local
        // arrives true beside a synthetic isCosatu that is not "Y", which
        // validateStructures correctly refuses (COSATU requires BEC; Local requires COSATU).
        captured.setIsBec(Boolean.TRUE);
        captured.setIsCosatu("Y");

        List<Field> fields = Synthetic.fieldsOf(Member.class);
        assertNonVacuousScan(fields, 40, "Member fields");

        // Snapshot BEFORE the call. The service mutates the member in place and
        // hands the same instance to save(), so the captor returns the very
        // object that was passed in -- comparing the two would compare an
        // object with itself and pass no matter what the service did.
        //
        // Found by the reddening change: planting a silent field drop left this
        // test green. The snapshot is what makes it an assertion.
        Map<String, String> before = new LinkedHashMap<>();
        for (Field field : fields) {
            Object value = Synthetic.read(captured, field);
            if (value != null) {
                before.put(field.getName(), String.valueOf(value));
            }
        }

        this.service.create(captured);

        verify(this.memberRepository).save(this.saved.capture());
        Member reachedTheRepository = this.saved.getValue();

        List<String> dropped = new ArrayList<>();
        List<String> checked = new ArrayList<>();
        for (Field field : fields) {
            if (SERVICE_MAY_CHANGE.containsKey(field.getName())) {
                continue;
            }
            String expected = before.get(field.getName());
            Object actual = Synthetic.read(reachedTheRepository, field);
            if (expected == null) {
                continue;
            }
            checked.add(field.getName());
            if (actual == null || !expected.equals(String.valueOf(actual))) {
                dropped.add(field.getName() + " (captured '" + expected + "', saved '"
                        + actual + "')");
            }
        }

        assertThat(dropped)
                .as("these fields did not survive create(). A dropped field is invisible "
                        + "everywhere else: the browser shows what was typed, the save reports "
                        + "success, and the value is simply not there. If the service is meant "
                        + "to change one of them, add it to SERVICE_MAY_CHANGE with the reason")
                .isEmpty();

        assertThat(checked)
                .as("no field was actually compared, so this guard passed without asserting "
                        + "anything")
                .isNotEmpty();
    }

    @Test
    @DisplayName("create clears the id, so it cannot overwrite an existing member")
    void createClearsTheId() {
        // Named separately because it is the one change with a safety
        // consequence: without it, a POST carrying an id would update somebody
        // else's record and answer 200.
        Member incoming = MemberFixture.aMember();
        incoming.setId(4321);

        this.service.create(incoming);

        verify(this.memberRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getId()).isNull();
    }

    @Test
    @DisplayName("create derives date of birth and gender when identityType is the expected value")
    void createDerivesFromTheIdentityNumber() {
        // Clock-invariant: the century depends on the current year, so only the
        // month, day and gender are asserted. A test that pinned the year here
        // would assert the machine's clock.
        //
        // "ID" is the value the SERVER looks for. The browser sends something
        // else -- see the disabled test below, which is the defect. This one
        // proves the derivation itself works, so that test is about the
        // vocabulary and not about the parser.
        Member incoming = MemberFixture.aMember();
        incoming.setIdentityType("ID");
        incoming.setDateOfBirth(null);
        incoming.setGender(null);

        this.service.create(incoming);

        verify(this.memberRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getDateOfBirth()).isNotNull();
        assertThat(this.saved.getValue().getDateOfBirth().getMonthValue()).isEqualTo(4);
        assertThat(this.saved.getValue().getDateOfBirth().getDayOfMonth()).isEqualTo(27);
        assertThat(this.saved.getValue().getGender()).isEqualTo("Male");
    }

    @Test
    @Disabled(KnownDefects.IDENTITY_TYPE_VOCABULARY_DRIFT)
    @DisplayName("create derives from the identity number for the value the browser really sends")
    void createDerivesForTheBrowsersIdentityType() {
        // The browser's dropdown offers "I.D. Number" and submits it verbatim.
        // The server tests identityType against "ID". Neither side is wrong on
        // its own; they simply do not agree, so date of birth and gender are
        // never derived for a real capture.
        //
        // This is what a static comparison of the two vocabularies would show
        // and only an executed test can convict -- and it is what tells you
        // which side to change.
        Member incoming = MemberFixture.aMember();
        incoming.setIdentityType("I.D. Number");
        incoming.setDateOfBirth(null);
        incoming.setGender(null);

        this.service.create(incoming);

        verify(this.memberRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getDateOfBirth()).isNotNull();
        assertThat(this.saved.getValue().getGender()).isEqualTo("Male");
    }

    @Test
    @DisplayName("create rejects a debit order with no bank account, before saving anything")
    void createRejectsAnInvalidDebitOrder() {
        // Proves validation runs BEFORE the save rather than after it. The
        // negative verify is the assertion: a service that saved first and
        // validated afterwards would leave a half-captured member behind.
        Member incoming = MemberFixture.aMember();
        incoming.setPaymentType("Debit Order");
        incoming.setBankAccountNumber(null);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> this.service.create(incoming))
                .isInstanceOf(IllegalArgumentException.class);

        verify(this.memberRepository, org.mockito.Mockito.never()).save(any(Member.class));
    }

    /** A recruiter row as the table holds it, named. */
    private static Recruiter recruiterNamed(String name) {
        Recruiter recruiter = new Recruiter();
        recruiter.setName(name);
        return recruiter;
    }

    @Test
    @DisplayName("🗣️ a POST naming a VACANT post does not attribute the member to it")
    void createRefusesToAttributeAMemberToAVacancy() {
        // 🔴 THE HALF THE DROP-DOWN CANNOT ENFORCE. The owner decided on 2026-09-10
        // that an unfilled post is not selectable, and GET /api/recruiters now leaves
        // the three VACANT rows out. That is a suggestion: this endpoint takes a NAME,
        // it is permitAll(), and the row is still in the table -- so without the rule
        // here, one curl attributes a member to a vacancy and the count is credited to
        // nobody.
        Member incoming = MemberFixture.aMember();
        incoming.setRecruitmentRep("VACANT");
        when(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("VACANT"))
                .thenReturn(Optional.of(recruiterNamed("VACANT")));

        this.service.create(incoming);

        verify(this.memberRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getRecruiter())
                .as("the member is captured with NO recruiter rather than with a vacancy")
                .isNull();
    }

    @Test
    @DisplayName("...and a real recruiter with the same shape of lookup still lands")
    void createStillAttributesARealRecruiter() {
        // 🔑 THE MUST-PASS BESIDE THE MUST-FAIL. The assertion above is satisfied by a
        // resolver that stopped attributing ANY recruiter -- which is exactly the
        // regression 03.10 spent a session undoing, twice, and it would read as green.
        // This is what tells "vacancies are excluded" apart from "the recruiter is
        // dropped again".
        Recruiter real = recruiterNamed("SYDNEY BIYELA");
        Member incoming = MemberFixture.aMember();
        incoming.setRecruitmentRep("SYDNEY BIYELA");
        when(this.recruiterRepository.findFirstByNameIgnoreCaseOrderByIdAsc("SYDNEY BIYELA"))
                .thenReturn(Optional.of(real));

        this.service.create(incoming);

        verify(this.memberRepository).save(this.saved.capture());
        assertThat(this.saved.getValue().getRecruiter()).isSameAs(real);
    }

    @Test
    @DisplayName("findById delegates without altering the result")
    void findByIdDelegates() {
        Member member = MemberFixture.aMember();
        when(this.memberRepository.findById(1)).thenReturn(Optional.of(member));

        assertThat(this.service.findById(1)).contains(member);
    }

    @Test
    @DisplayName("update answers empty for a member that does not exist, and writes nothing")
    void updateOfAMissingMemberWritesNothing() {
        when(this.memberRepository.findById(99)).thenReturn(Optional.empty());

        assertThat(this.service.update(99, MemberFixture.aMember())).isEmpty();
        verify(this.memberRepository, org.mockito.Mockito.never()).save(any(Member.class));
    }

    // ============================================================
    // T3 — a PUT that changes a representative's role.
    //
    // 🔴 Nothing tested this. `MemberServiceImplTest` had exactly one `update` test
    // (the missing member above), the create round-trip EXEMPTS `structure`/`role`
    // from its field sweep, and both integration PUTs send `isRepresentative: false`
    // -- so the one combination that exercises the resolver was covered nowhere.
    //
    // 🔑 WHY THE MAPPER IS STUBBED RATHER THAN LEFT AS A BARE MOCK. `updateFromMember`
    // ignores `structure` and `role` deliberately -- they are resolved from transient
    // ids by the service -- but it DOES copy `isRepresentative`, `roleId` and
    // `structureId`, because MapStruct maps same-named properties and only those four
    // associations are @Mapping(ignore). A bare mock copies nothing, which would make
    // these tests fail for a reason production does not have. The stub below is that
    // contract, written out; `MemberMapperTest` and the IT prove the real mapper keeps
    // it. Get this wrong in the other direction and the test is vacuous.
    // ============================================================

    /** What MapStruct really does with the three transients the resolver reads. */
    private void mapperCarriesTheTransientIds() {
        org.mockito.Mockito.doAnswer(invocation -> {
            Member source = invocation.getArgument(0);
            Member target = invocation.getArgument(1);
            target.setIsRepresentative(source.getIsRepresentative());
            target.setRoleId(source.getRoleId());
            target.setStructureId(source.getStructureId());
            return null;
        }).when(this.memberMapper).updateFromMember(any(Member.class), any(Member.class));
    }

    @Test
    @DisplayName("🔴 a PUT that promotes a member to a role stores that role — R1-F1")
    void updateResolvesANewlyChosenRole() {
        // 🔴 RED ON THE BRANCH. `update` calls `resolveStructureAndRole(updated)` --
        // the TRANSIENT object -- and then `updateFromMember` ignores `role`, so the
        // resolved association is thrown away with the object that carried it. The
        // member is saved with whatever role it already had, and the API answers 200.
        mapperCarriesTheTransientIds();

        RepresentativeRole role = new RepresentativeRole();
        role.setId(3);
        role.setName("Shop Steward");
        when(this.representativeRoleRepository.findById(3)).thenReturn(Optional.of(role));

        Member existing = MemberFixture.aMember();
        existing.setIsRepresentative(false);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member updated = MemberFixture.aMember();
        updated.setIsRepresentative(true);
        updated.setRoleId(3);

        Member saved = this.service.update(4, updated).orElseThrow();

        assertThat(saved.getRole())
                .as("the PUT chose role 3; the stored member must carry it")
                .isSameAs(role);
    }

    @Test
    @DisplayName("...and a PUT that demotes one clears the role it used to hold")
    void updateClearsTheRoleOnDemotion() {
        // The other direction, and the one with a data-protection edge: a member who
        // stops being a representative keeps the title on every screen that reads it.
        // Without this case the fix above is satisfied by resolving only when a roleId
        // is present, which leaves a demotion silently ignored.
        mapperCarriesTheTransientIds();

        RepresentativeRole held = new RepresentativeRole();
        held.setId(3);
        held.setName("Shop Steward");
        UnionStructure structure = new UnionStructure();
        structure.setId(9);

        Member existing = MemberFixture.aMember();
        existing.setIsRepresentative(true);
        existing.setRole(held);
        existing.setStructure(structure);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member updated = MemberFixture.aMember();
        updated.setIsRepresentative(false);

        Member saved = this.service.update(4, updated).orElseThrow();

        assertThat(saved.getRole())
                .as("the member is no longer a representative, so the role must go")
                .isNull();
        assertThat(saved.getStructure())
                .as("and the structure with it -- they are one decision")
                .isNull();
    }

    // ============================================================
    // 🗣️ THE DEBIT DAY — settled by the owner 2026-09-09
    //
    // The service used to overwrite `debitDay` from the bank's subscription row
    // unconditionally, and MemberValidator then rejected the result against the
    // permitted 20-31 range — for ELEVEN of the fifteen seeded rows. A debit-order
    // member could not be captured for most banks, and the error blamed a day the
    // administrator never entered.
    //
    // The range is authoritative; the banks' seeded ACB days are placeholder data.
    // So the assignment is conditional now, and these two tests are what stop it
    // silently going back — DebitDayOverwriteConflictTest guards the RULE, but only
    // a service test can catch the service forgetting to apply it.
    // ============================================================

    /** Wires the bank + subscription lookup the service performs, with a given ACB day. */
    private void bankOffers(String bankName, int acbDebitDay) {
        Bank bank = new Bank();
        bank.setId(7);
        bank.setName(bankName);

        Subscriptions subscription = new Subscriptions();
        subscription.setBank(bank);
        subscription.setAcbDebitDay(acbDebitDay);
        subscription.setSubscriptionAmount(new java.math.BigDecimal("120.00"));

        when(this.bankRepository.findByNameIgnoreCase(bankName)).thenReturn(Optional.of(bank));
        when(this.subscriptionsRepository.findByBankIdWithBank(7L))
                .thenReturn(List.of(subscription));
    }

    @Test
    @DisplayName("🔴 a bank whose ACB day is out of range does not overwrite the member's own choice")
    void anUnusableBankDayLeavesTheMembersChoiceAlone() {
        // Absa's seeded row says the 5th. Before the fix this call threw, and the
        // message named a debit day the administrator had not chosen.
        bankOffers("Absa Bank Limited", 5);

        Member member = MemberFixture.aMember();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setAccountType("Cheque");
        member.setBankName("Absa Bank Limited");
        member.setDebitDay(25);

        Member saved = this.service.create(member);

        assertThat(saved.getDebitDay())
                .as("the member chose the 25th, which is legal; the bank's 5th is not, "
                        + "so it must not replace it")
                .isEqualTo(25);
        // And the subscription STILL comes from the same row -- declining one derived
        // field must not silently abandon the other two.
        assertThat(saved.getMonthlySubscription()).isEqualTo("120.00");
    }

    @Test
    @DisplayName("a bank whose ACB day IS in range still overwrites, as it always did")
    void aUsableBankDayIsStillApplied() {
        // The non-vacuity half. Without it the test above passes just as well against
        // a service that has stopped deriving the debit day at all -- which would be a
        // different defect wearing the same green.
        bankOffers("Nedbank Limited", 20);

        Member member = MemberFixture.aMember();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setAccountType("Cheque");
        member.setBankName("Nedbank Limited");
        member.setDebitDay(31);

        assertThat(this.service.create(member).getDebitDay())
                .as("the bank's day is usable, so it is authoritative over the default")
                .isEqualTo(20);
    }

    // ============================================================
    // 🔴 A67 — the test ABOVE is the defect's live pin, as it stands.
    //
    // It asserts the overwrite as intended, and until 2026-09-21 it was: SASBO then
    // answered Q42 -- the debit day must be flexible, "as banks debit days change" --
    // so a day the member CHOSE inside the range must survive the bank's. When A67
    // is fixed, the test above flips (or goes), the disabled one below is enabled,
    // and the must-pass twin beside it is what keeps the bank's day as the DEFAULT.
    // ============================================================

    @Test
    @Disabled(KnownDefects.MEMBER_DEBIT_DAY_OVERWRITTEN_BY_THE_BANKS)
    @DisplayName("🔴 A67: a debit day the member chose stands, even where the bank's day is usable")
    void aChosenDaySurvivesAUsableBankDay() {
        bankOffers("Nedbank Limited", 20);

        Member member = MemberFixture.aMember();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        // Required of a Debit Order since the ACB arc's account-type rule reached main
        // (MemberValidator.validateAccountType); without it `create` refuses this member
        // before the debit day is ever derived, and the case would pass or fail for a
        // reason that has nothing to do with A67.
        member.setAccountType("Cheque");
        member.setBankName("Nedbank Limited");
        member.setDebitDay(22);

        assertThat(this.service.create(member).getDebitDay())
                .as("the member chose the 22nd, inside the range; the bank's 20th must not replace it")
                .isEqualTo(22);
    }

    @Test
    @DisplayName("A67's must-pass twin: a member captured with NO debit day is given the bank's")
    void aMemberWithNoDayIsGivenTheBanks() {
        bankOffers("Nedbank Limited", 20);

        Member member = MemberFixture.aMember();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        // As above: the account type is what the ACB arc's rule now requires of a Debit
        // Order. This twin's job is the DEFAULT -- with no day chosen, the bank's day is
        // taken -- so it must reach the derivation at all.
        member.setAccountType("Cheque");
        member.setBankName("Nedbank Limited");
        member.setDebitDay(null);

        assertThat(this.service.create(member).getDebitDay())
                .as("with nothing chosen, the bank's usable day is the right default -- the fix "
                        + "for A67 must keep this")
                .isEqualTo(20);
    }

    // ============================================================
    // T1 — the same rule, on all THREE write paths.
    //
    // 🔑 WHY THESE EXIST AT ALL. `DebitDayOverwriteConflictTest` asserted this rule
    // by RE-IMPLEMENTING it -- `if (DebitDay.isInRange(row)) member.setDebitDay(row)`
    // -- because the service method is private. A test that mirrors the condition
    // passes whatever the service does, and it did: `updateBanking` never had the
    // guard at all, and four green tests said the rule held. The two mirrors are
    // retired with this batch; what replaces them is the service being CALLED.
    //
    // `create` is covered by the two tests above. These are the other two paths.
    // ============================================================

    @Test
    @DisplayName("update declines a bank day it would then refuse, as create does")
    void updateKeepsALegalDayWhenTheBanksIsUnusable() {
        bankOffers("Absa Bank Limited", 5);

        Member existing = debitOrderMemberAtAbsa();
        existing.setDebitDay(25);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member saved = this.service.update(4, MemberFixture.aMember()).orElseThrow();

        assertThat(saved.getDebitDay())
                .as("Absa's ACB day is the 5th, which this API refuses; the member's "
                        + "own 25 is legal and must survive the edit")
                .isEqualTo(25);
    }

    @Test
    @DisplayName("🔴 updateBanking declines a bank day it would then refuse — R1-F2")
    void updateBankingKeepsALegalDayWhenTheBanksIsUnusable() {
        // 🔴 RED ON THE BRANCH. `updateBanking` called `autoPopulateDebitDay`, which
        // assigns the bank's day UNCONDITIONALLY -- the exact shape `create` and
        // `update` were fixed out of on 2026-09-09, left in place on the third path.
        // The member below cannot be saved at all today: the service sets the 5th and
        // its own validator then throws, naming a day nobody entered.
        bankOffers("Absa Bank Limited", 5);

        Member existing = debitOrderMemberAtAbsa();
        existing.setDebitDay(25);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member saved = this.service.updateBanking(4, bankingPayloadFor(existing, 25)).orElseThrow();

        assertThat(saved.getDebitDay())
                .as("the administrator's 25 is legal; Absa's 5 is not, so it must not "
                        + "replace it -- and saving an unchanged member must not 400")
                .isEqualTo(25);
    }

    @Test
    @DisplayName("...and updateBanking still applies a bank day that IS usable")
    void updateBankingStillAppliesAUsableBankDay() {
        // The non-vacuity half. Without it the test above is satisfied by an
        // `updateBanking` that has stopped deriving the debit day at all, which is a
        // different defect wearing the same green.
        bankOffers("Nedbank Limited", 20);

        Member existing = debitOrderMemberAtAbsa();
        existing.setBankName("Nedbank Limited");
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member saved = this.service.updateBanking(4, bankingPayloadFor(existing, 31)).orElseThrow();

        assertThat(saved.getDebitDay())
                .as("Nedbank's 20 is inside the range, so it is authoritative")
                .isEqualTo(20);
    }

    @Test
    @DisplayName("🔑 updateBanking does NOT re-derive the monthly subscription")
    void updateBankingLeavesTheSubscriptionAlone() {
        // 🔑 THE MUST-PASS THAT CONSTRAINS THE FIX. The obvious repair for the test
        // above is to point `updateBanking` at `autoPopulateBankDerivedFields`, which
        // already carries the guard -- but that method ALSO assigns
        // `monthlySubscription` from the same row, and this path is documented not to:
        // the amount is maintenance's to change, not a side effect of editing a bank
        // account number. Without this test, the fix for R1-F2 silently makes a
        // banking edit re-price the member.
        bankOffers("Nedbank Limited", 20);

        Member existing = debitOrderMemberAtAbsa();
        existing.setBankName("Nedbank Limited");
        existing.setMonthlySubscription(new BigDecimal("999.99"));
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member saved = this.service.updateBanking(4, bankingPayloadFor(existing, 31)).orElseThrow();

        assertThat(saved.getMonthlySubscription())
                .as("the bank's subscription row says 120.00; a banking edit must not "
                        + "re-price the member, so what was stored still stands")
                .isEqualByComparingTo("999.99");
    }

    // ============================================================
    // R7-F1 / 🗣️ decision ⑦ — absent is not blank, and this is the pin.
    //
    // 🔴 WHAT WENT WRONG, AND WHERE. The Recruiters screen sent
    // `recruitmentRep: member.recruitmentRep || ''` on every PUT. It does not hold the
    // field, so the value was always `''` -- which this service reads as "clear it".
    // Every bulk edit from that screen wiped the recruiter the capture form had just
    // been fixed to store. The arc gave `recruitmentRep` a new meaning and an existing
    // caller was already sending the one value that meant "delete".
    //
    // 🗣️ The owner's decision, 2026-09-10: no change to the rule; fix the caller. The
    // absent-vs-blank distinction is right -- it is what makes a PUT that never
    // mentioned a field safe -- and the screen sending a key it does not own is the
    // thing that was wrong. The caller half is done on the design arc's branch and
    // arrives on merge.
    //
    // 🔑 SO THESE TWO ARE PINS, NOT REPAIRS, and that is exactly why they are needed:
    // the rule they protect has no test, and the obvious "fix" for the defect above is
    // to make the service ignore a blank -- which would remove the only way a user can
    // clear a recruiter, and special-case one field instead of one screen. Proved
    // non-vacuous by collapsing the distinction in `resolveByName` (`name == null` ->
    // `""`): the first test reddens, which is the defect reproduced.
    // ============================================================

    @Test
    @DisplayName("🔑 a PUT that never mentions the recruiter leaves the captured one — R7-F1")
    void aPutWithoutARecruiterLeavesTheCapturedRecruiterAlone() {
        Recruiter captured = new Recruiter();
        captured.setId(11);
        captured.setName("Thandi Mokoena");

        Member existing = MemberFixture.aMember();
        existing.setRecruiter(captured);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member updated = MemberFixture.aMember();
        updated.setRecruitmentRep(null);   // the field is absent from the request

        Member saved = this.service.update(4, updated).orElseThrow();

        assertThat(saved.getRecruiter())
                .as("the request said nothing about the recruiter, so there is nothing "
                        + "to change -- a screen that does not hold the field must not "
                        + "be able to erase it by omission")
                .isSameAs(captured);
    }

    @Test
    @DisplayName("...and a PUT that sends it BLANK clears it, which is the deliberate way")
    void aPutWithABlankRecruiterClearsIt() {
        // The other half, and the reason the caller is what had to change: blank really
        // does mean clear. Without this the pin above is satisfied by a service that
        // has stopped reading the field at all, and a user could never remove a
        // recruiter attributed in error.
        Recruiter captured = new Recruiter();
        captured.setId(11);
        captured.setName("Thandi Mokoena");

        Member existing = MemberFixture.aMember();
        existing.setRecruiter(captured);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member updated = MemberFixture.aMember();
        updated.setRecruitmentRep("");   // an explicit, deliberate clear

        Member saved = this.service.update(4, updated).orElseThrow();

        assertThat(saved.getRecruiter())
                .as("an explicit empty string is a request to clear, and stays one")
                .isNull();
    }

    // ============================================================
    // 00.5 — the account type: required where the save could give it, never on an unrelated edit.
    // ============================================================

    @Test
    @DisplayName("00.5 a new debit-order member without an account type is refused, and nothing is saved")
    void createRefusesADebitOrderWithoutAnAccountType() {
        bankOffers("Absa Bank Limited", 25);
        Member member = debitOrderMemberAtAbsa();
        member.setAccountType(null);
        member.setDebitDay(25);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.service.create(member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account type is required when payment type is 'Debit Order'");
        verify(this.memberRepository, org.mockito.Mockito.never()).save(any(Member.class));
    }

    @Test
    @DisplayName("00.5 ...and one given in any spelling is stored as the capture screen offers it")
    void createStoresTheAccountTypesDisplayName() {
        bankOffers("Absa Bank Limited", 25);
        Member member = debitOrderMemberAtAbsa();
        member.setAccountType(" SAVINGS ");
        member.setDebitDay(25);

        assertThat(this.service.create(member).getAccountType()).isEqualTo("Savings");
    }

    @Test
    @DisplayName("00.5 an edit of another field is never refused for the type a member was captured without")
    void anUnrelatedEditIsNotRefusedForAMissingType() {
        // R-ROB-I: every member captured before V49 has no type. Correcting their phone number must save.
        bankOffers("Absa Bank Limited", 25);
        Member existing = debitOrderMemberAtAbsa();
        existing.setAccountType(null);
        existing.setDebitDay(25);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));

        Member edit = new Member();
        edit.setPhone("+27 82 000 0009");

        // The property is the validator's, not the mapper's (a bare mock here, as the note above says): the save
        // happens, and the type the member was captured without stays unset rather than being refused or guessed.
        assertThat(this.service.update(4, edit)).isPresent();
        verify(this.memberRepository).save(existing);
        assertThat(existing.getAccountType()).isNull();
    }

    @Test
    @DisplayName("00.5 banking details saved as a debit order must carry the type -- the one screen it is edited on")
    void updateBankingRequiresTheType() {
        bankOffers("Absa Bank Limited", 25);
        Member existing = debitOrderMemberAtAbsa();
        existing.setDebitDay(25);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));
        Member banking = bankingPayloadFor(existing, 25);
        banking.setAccountType(null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> this.service.updateBanking(4, banking))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account type is required when payment type is 'Debit Order'");
        verify(this.memberRepository, org.mockito.Mockito.never()).save(any(Member.class));
    }

    @Test
    @DisplayName("00.5 ...and a type given there replaces the stored one")
    void updateBankingStoresTheType() {
        bankOffers("Absa Bank Limited", 25);
        Member existing = debitOrderMemberAtAbsa();
        existing.setDebitDay(25);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(existing));
        Member banking = bankingPayloadFor(existing, 25);
        banking.setAccountType("transmission");

        assertThat(this.service.updateBanking(4, banking).orElseThrow().getAccountType()).isEqualTo("Transmission");
    }

    // ============================================================
    // 03.5 — changeStatus: the status and nothing else, with its history.
    // ============================================================

    private static za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusChangeRequest terminated(Long runId,
                                                                                                          Long lineId) {
        return new za.co.titandynamix.sasbo_online_backend.dto.member.MemberStatusChangeRequest("Terminated",
                za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason.SUBS_IN_ARREARS, runId, lineId,
                "administrator", java.time.Instant.parse("2026-09-22T08:00:00Z"));
    }

    @Test
    @DisplayName("03.5 changeStatus moves the status and records who, when, from what and why")
    void changeStatusRecordsItsHistory() {
        Member member = MemberFixture.aMember();
        member.setId(4);
        member.setStatus("Active");
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(member));

        Member saved = this.service.changeStatus(4, terminated(null, null)).orElseThrow();

        assertThat(saved.getStatus()).isEqualTo("Terminated");
        org.mockito.ArgumentCaptor<za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange> row =
                org.mockito.ArgumentCaptor.forClass(za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange.class);
        verify(this.statusChanges).save(row.capture());
        assertThat(row.getValue().getMemberId()).isEqualTo(4);
        assertThat(row.getValue().getFromStatus()).isEqualTo("Active");
        assertThat(row.getValue().getToStatus()).isEqualTo("Terminated");
        assertThat(row.getValue().getReason().name()).isEqualTo("SUBS_IN_ARREARS");
        assertThat(row.getValue().getChangedBy()).isEqualTo("administrator");
        assertThat(row.getValue().getChangedAt()).isEqualTo(java.time.Instant.parse("2026-09-22T08:00:00Z"));
    }

    @Test
    @DisplayName("🔴 03.5 R-ROB-I: a member the whole-record validation would refuse still changes status -- none runs")
    void changeStatusNeverRunsTheWholeRecordValidation() {
        // COSATU without BEC and a debit order with no account number: `update` refuses this member on two
        // counts, for fields that have nothing to do with a status. The narrow method must not see either.
        Member member = MemberFixture.aMember();
        member.setId(4);
        member.setStatus("Active");
        member.setIsCosatu("Y");
        member.setIsBec(Boolean.FALSE);
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber(null);
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(member));

        assertThat(this.service.changeStatus(4, terminated(null, null)).orElseThrow().getStatus()).isEqualTo("Terminated");
    }

    @Test
    @DisplayName("03.5 a change to the status a member already has records nothing, and an unknown member is empty")
    void changeStatusIsIdempotentAndNamesNoOne() {
        Member member = MemberFixture.aMember();
        member.setId(4);
        member.setStatus("Terminated");
        when(this.memberRepository.findById(4)).thenReturn(Optional.of(member));
        when(this.memberRepository.findById(5)).thenReturn(Optional.empty());

        assertThat(this.service.changeStatus(4, terminated(null, null))).isPresent();
        assertThat(this.service.changeStatus(5, terminated(null, null))).isEmpty();
        verify(this.statusChanges, org.mockito.Mockito.never()).save(any());
    }

    /** A member the debit-day rule applies to, banked where the seed's day is unusable. */
    private static Member debitOrderMemberAtAbsa() {
        Member member = MemberFixture.aMember();
        member.setPaymentType("Debit Order");
        member.setBankAccountNumber("1234567890");
        member.setAccountType("Cheque");
        member.setBankName("Absa Bank Limited");
        return member;
    }

    /** What the banking screen submits: the flat banking fields and a chosen day. */
    private static Member bankingPayloadFor(Member existing, int debitDay) {
        Member banking = new Member();
        banking.setBankName(existing.getBankName());
        banking.setBankAccountNumber(existing.getBankAccountNumber());
        banking.setAccountType(existing.getAccountType());
        banking.setPaymentType(existing.getPaymentType());
        banking.setDebitDay(debitDay);
        return banking;
    }
}

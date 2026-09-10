package za.co.titandynamix.sasbo_online_backend.services.impl;

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
                this.recruitmentChannelRepository, this.recruiterRepository);

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
        member.setBankName("Nedbank Limited");
        member.setDebitDay(31);

        assertThat(this.service.create(member).getDebitDay())
                .as("the bank's day is usable, so it is authoritative over the default")
                .isEqualTo(20);
    }
}

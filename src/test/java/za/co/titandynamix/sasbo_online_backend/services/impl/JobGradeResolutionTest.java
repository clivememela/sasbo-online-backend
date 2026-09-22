package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.JobGradeOutcome;
import za.co.titandynamix.sasbo_online_backend.mapper.MemberMapper;
import za.co.titandynamix.sasbo_online_backend.repository.*;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * THE JOB-GRADE FIXTURE -- the server half (ux.md §6c clause ⑤).
 *
 * <h2>The same table is asserted in the browser</h2>
 * The rows below are the fixture from clause ⑤. The browser's
 * {@code jobGradeMatch.test.js} asserts the identical inputs, and the matching rule
 * itself is published in {@code api-vocabulary.json} -- computed by calling
 * {@link za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher}, not
 * transcribed -- so the two halves cannot drift apart quietly.
 *
 * <h2>🔴 Why the fixture is specified rather than discovered</h2>
 * Four registered defects in this system are one rule implemented twice and allowed
 * to diverge: A6 ({@code Payroll} / {@code Payroll Deduction}), A7 ({@code Passport} /
 * {@code Passport Number}), A17 ({@code ID} / {@code I.D. Number}), A9 (the client
 * re-deriving a rule the server publishes). The job-grade fallback needs both halves
 * to agree on what "the same grade" means, so it was going to become the fifth unless
 * the agreement was written down and checked.
 *
 * <p>{@code "Grade  c"} with two spaces is the case that carries the point: it must
 * MATCH {@code "Grade c"}. If only one side collapsed whitespace, that input would
 * match in the form, be captured as a brand-new grade on save, and the user would be
 * told it worked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Job grade resolution -- MATCHED / CAPTURED / CLEARED")
class JobGradeResolutionTest {

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

    private MemberServiceImpl service;

    private static final String BANK = "Absa Bank Limited";

    /** The master list this fixture resolves against: one grade from each of the three real conventions. */
    private static List<JobGrade> masterList() {
        return List.of(
                verifiedGrade(1, "BA2"),
                verifiedGrade(2, "Grade c"),
                verifiedGrade(3, "SBG04"));
    }

    private static JobGrade verifiedGrade(Integer id, String grade) {
        Bank bank = new Bank();
        bank.setId(99);
        bank.setName(BANK);
        JobGrade jg = new JobGrade();
        jg.setId(id);
        jg.setBank(bank);
        jg.setGrade(grade);
        jg.setVerified(true);
        return jg;
    }

    @BeforeEach
    void setUp() {
        this.service = new MemberServiceImpl(
                this.memberRepository, this.memberMapper, this.representativeRepository,
                this.subscriptionsRepository, this.bankBranchRepository, this.bankRepository,
                this.representativeRoleRepository, this.unionStructureRepository,
                this.jobGradeRepository, this.jobGradeService, this.areaRepository,
                this.recruitmentChannelRepository, this.recruiterRepository, this.statusChanges);

        when(this.memberRepository.save(any(Member.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(this.bankRepository.findByNameIgnoreCase(any())).thenReturn(Optional.empty());
        when(this.jobGradeService.findByBankIdentifier(BANK)).thenReturn(masterList());
    }

    private Member captureWithGrade(String grade) {
        Member member = new Member();
        member.setBankName(BANK);
        member.setPaymentType("Payroll Deduction");
        member.setJobGrade(grade == null ? null : new JobGrade(grade));
        return this.service.create(member);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> MATCHED as \"{1}\"")
    @CsvSource({
            // The fixture from ux.md §6c clause ⑤, plus the whitespace shapes that
            // are the actual drift risk between the two halves.
            "ba2,        BA2",
            "BA2,        BA2",
            "'BA2 ',     BA2",
            "grade c,    Grade c",
            "'Grade  c', Grade c",
            "SBG04,      SBG04",
    })
    @DisplayName("a grade on the master list MATCHES, and the canonical spelling is stored")
    void matchedGradesResolveToTheCanonicalValue(String submitted, String expectedCanonical) {
        Member saved = captureWithGrade(submitted);

        assertThat(saved.getJobGradeOutcome()).isEqualTo(JobGradeOutcome.MATCHED);
        assertThat(saved.getJobGrade()).isNotNull();
        assertThat(saved.getJobGrade().getGrade())
                .as("the master-list spelling wins -- this is where most 'spelling inconsistency' "
                        + "dies, for free")
                .isEqualTo(expectedCanonical);
        verify(this.jobGradeService, never()).capture(any(), any());
    }

    @Test
    @DisplayName("'Grade c' keeps its lowercase c -- the canonical value is never 'tidied'")
    void canonicalCaseIsNeverUpperCased() {
        // Grade c is a real Old Mutual grade sitting beside Grade K. Upper-casing to
        // tidy the data would corrupt the union's own reference values.
        assertThat(captureWithGrade("GRADE C").getJobGrade().getGrade()).isEqualTo("Grade c");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> CAPTURED")
    @CsvSource({
            "SBG4",        // the realistic typo for a zero-padded grade
            "Prokureur",   // a genuinely new grade
            "'  Senior   Clerk  '",
    })
    @DisplayName("a grade the master list does not contain is CAPTURED, never discarded")
    void unknownGradesAreCaptured(String submitted) {
        JobGrade stored = new JobGrade();
        // The rule itself, not a transcription of it. This file's own header argues the
        // rule must live in exactly one place; hand-inlining `trim().replaceAll(...)`
        // here would have been the very drift it exists to catch.
        stored.setGrade(JobGradeMatcher.storedForm(submitted));
        stored.setVerified(false);
        when(this.jobGradeService.capture(any(), any())).thenReturn(stored);

        Member saved = captureWithGrade(submitted);

        assertThat(saved.getJobGradeOutcome()).isEqualTo(JobGradeOutcome.CAPTURED);
        assertThat(saved.getJobGrade()).isNotNull();
        verify(this.jobGradeService).capture(BANK, submitted);
    }

    @Test
    @DisplayName("🔴 the regression that made this unit necessary: an unknown grade used to vanish")
    void anUnknownGradeIsNoLongerSilentlyDiscarded() {
        // Before 02.5 this method ended `.orElse(null)`. The member saved, the response
        // said success, and the job grade came back empty with nothing said. In a field
        // the stakeholder had just asked us to open to free text, that is the whole
        // feature failing silently.
        when(this.jobGradeService.capture(any(), any())).thenReturn(null);

        Member saved = captureWithGrade("Prokureur");

        assertThat(saved.getJobGrade()).isNull();
        assertThat(saved.getJobGradeOutcome())
                .as("even when the grade could not be stored, the server SAYS SO rather than "
                        + "returning a silent null")
                .isEqualTo(JobGradeOutcome.CLEARED);
    }

    @Test
    @DisplayName("a blank grade is CLEARED, and says so")
    void blankIsCleared() {
        Member saved = captureWithGrade("   ");

        assertThat(saved.getJobGrade()).isNull();
        assertThat(saved.getJobGradeOutcome()).isEqualTo(JobGradeOutcome.CLEARED);
        verify(this.jobGradeService, never()).capture(any(), any());
    }

    @Test
    @DisplayName("no grade supplied is not the same as a cleared grade")
    void absentIsNotCleared() {
        // Nothing was sent, so nothing is reported and the member's existing value is
        // left alone. Reporting CLEARED here would tell the user we had wiped a field
        // they never touched.
        Member saved = captureWithGrade(null);

        assertThat(saved.getJobGradeOutcome()).isNull();
    }

    @Test
    @DisplayName("a grade matching a row somebody TYPED is reported CAPTURED, not MATCHED")
    void matchingAnUnverifiedRowIsStillCaptured() {
        // It resolves -- so the member gets the row and nothing is duplicated -- but it
        // is not part of the master list yet, and calling it MATCHED would imply a
        // confirmation that has not happened.
        JobGrade typedEarlier = verifiedGrade(4, "Prokureur");
        typedEarlier.setVerified(false);
        when(this.jobGradeService.findByBankIdentifier(BANK))
                .thenReturn(List.of(verifiedGrade(1, "BA2"), typedEarlier));

        Member saved = captureWithGrade("prokureur");

        assertThat(saved.getJobGrade()).isSameAs(typedEarlier);
        assertThat(saved.getJobGradeOutcome()).isEqualTo(JobGradeOutcome.CAPTURED);
        verify(this.jobGradeService, never()).capture(any(), any());
    }
}

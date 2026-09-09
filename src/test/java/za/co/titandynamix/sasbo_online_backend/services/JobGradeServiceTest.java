package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.services.impl.JobGradeServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JobGradeServiceImpl Tests")
class JobGradeServiceTest {

    @Mock
    private JobGradeRepository jobGradeRepository;

    @Mock
    private BankRepository bankRepository;

    private JobGradeServiceImpl jobGradeService;

    @BeforeEach
    void setUp() {
        this.jobGradeService = new JobGradeServiceImpl(this.jobGradeRepository, this.bankRepository);
    }

    private static JobGrade grade(Integer id, Bank bank, String value) {
        return new JobGrade(id, bank, value, null, null, true);
    }

    @Test
    @DisplayName("Should return grades when identifier is a numeric universal branch code")
    void testFindByUniversalBranchCode() {
        // Arrange — the front end sends the resolved universal code (e.g. Absa = 632005)
        Bank absa = new Bank(1, "Absa Bank Limited", null, "632005");
        List<JobGrade> expected = List.of(grade(1, absa, "BA1"), grade(2, absa, "BA2"));
        when(this.jobGradeRepository.findByBank_UniversalBranchCode("632005")).thenReturn(expected);

        // Act
        List<JobGrade> result = this.jobGradeService.findByBankIdentifier("632005");

        // Assert — a numeric universal code must not be mistaken for a bank id
        assertThat(result)
                .hasSize(2)
                .extracting(JobGrade::getGrade)
                .containsExactly("BA1", "BA2");

        verify(this.jobGradeRepository, never()).findByBank_Id(632005);
    }

    @Test
    @DisplayName("Should fall back to bank id when no bank matches the numeric universal code")
    void testFindByBankIdWhenCodeHasNoMatch() {
        // Arrange
        Bank absa = new Bank(1, "Absa Bank Limited", null, "632005");
        when(this.jobGradeRepository.findByBank_UniversalBranchCode("1")).thenReturn(List.of());
        when(this.jobGradeRepository.findByBank_Id(1)).thenReturn(List.of(grade(1, absa, "BA1")));

        // Act
        List<JobGrade> result = this.jobGradeService.findByBankIdentifier("1");

        // Assert
        assertThat(result)
                .hasSize(1)
                .extracting(JobGrade::getGrade)
                .containsExactly("BA1");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"12345", "632005"})
    @DisplayName("Should not read a numeric candidate of five digits or more as a bank id when no universal code matches")
    void testFindByLongNumericCodeWithNoMatch(String candidate) {
        // Arrange — a universal branch code shape that matches no bank. The universal-code
        // test above stubs the code lookup to SUCCEED, so its never() holds with or without
        // the length gate; starving the lookup leaves the gate as the only thing in the way.
        // "12345" is the boundary the gate defines, "632005" the real-world shape.
        when(this.jobGradeRepository.findByBank_UniversalBranchCode(candidate)).thenReturn(List.of());

        // Act
        List<JobGrade> result = this.jobGradeService.findByBankIdentifier(candidate);

        // Assert — the candidate is not a primary key, so it is never tried as one
        assertThat(result).isEmpty();
        verify(this.jobGradeRepository, never()).findByBank_Id(Integer.valueOf(candidate));
    }

    @Test
    @DisplayName("Should resolve grades by bank name ignoring case")
    void testFindByBankName() {
        // Arrange
        Bank absa = new Bank(1, "Absa Bank Limited", null, "632005");
        when(this.jobGradeRepository.findByBank_UniversalBranchCode("absa bank limited")).thenReturn(List.of());
        when(this.bankRepository.findByNameIgnoreCase("absa bank limited")).thenReturn(Optional.of(absa));
        when(this.jobGradeRepository.findByBank_Id(1)).thenReturn(List.of(grade(1, absa, "BA1")));

        // Act
        List<JobGrade> result = this.jobGradeService.findByBankIdentifier("absa bank limited");

        // Assert
        assertThat(result)
                .hasSize(1)
                .extracting(JobGrade::getGrade)
                .containsExactly("BA1");
    }

    @Test
    @DisplayName("Should return empty list when identifier is null or blank")
    void testFindByBlankIdentifier() {
        // Act & Assert
        assertThat(this.jobGradeService.findByBankIdentifier(null)).isEmpty();
        assertThat(this.jobGradeService.findByBankIdentifier("   ")).isEmpty();

        verifyNoInteractions(this.jobGradeRepository, this.bankRepository);
    }

    @Test
    @DisplayName("Should return empty list when no lookup strategy matches")
    void testFindByUnknownIdentifier() {
        // Arrange
        when(this.jobGradeRepository.findByBank_UniversalBranchCode("Unknown Bank")).thenReturn(List.of());
        when(this.bankRepository.findByNameIgnoreCase("Unknown Bank")).thenReturn(Optional.empty());

        // Act
        List<JobGrade> result = this.jobGradeService.findByBankIdentifier("Unknown Bank");

        // Assert
        assertThat(result).isEmpty();
    }

    // ─── 02.5: the master list and the captured list are two different lists ───

    @Test
    @DisplayName("🔴 the verified list EXCLUDES a grade somebody typed; the resolution list keeps it")
    void verifiedAndResolutionListsDiffer() {
        // The pair of assertions is the point, and neither half is sufficient alone.
        //
        // If the drop-down showed captured grades, one typo would become everyone's
        // option and the field would stop delivering the consistency it exists for.
        // If RESOLUTION excluded them, then re-saving a member who carries a captured
        // grade would fail to find it and capture a near-duplicate every single time.
        Bank bank = new Bank();
        bank.setId(1);

        JobGrade master = grade(1, bank, "BA1");
        JobGrade typed = grade(2, bank, "Prokureur");
        typed.setVerified(false);

        when(this.jobGradeRepository.findByBank_UniversalBranchCode("632005"))
                .thenReturn(List.of(master, typed));

        assertThat(this.jobGradeService.findByBankIdentifier("632005"))
                .as("resolution sees every grade, or a captured one would be captured twice")
                .containsExactly(master, typed);

        assertThat(this.jobGradeService.findVerifiedByBankIdentifier("632005"))
                .as("the drop-down sees the master list only")
                .containsExactly(master);
    }

    @Test
    @DisplayName("capturing a grade that already exists returns the existing row")
    void captureIsIdempotentUnderTheMatchingRule() {
        // `resolveJobGrade` runs on create AND update, so without this, editing a
        // member who carries a captured grade would insert a fresh near-duplicate on
        // every save. Idempotence here is correctness, not tidiness.
        Bank bank = new Bank();
        bank.setId(1);
        JobGrade typed = grade(2, bank, "Prokureur");
        typed.setVerified(false);

        when(this.jobGradeRepository.findByBank_UniversalBranchCode("632005"))
                .thenReturn(List.of(typed));

        // Different case and spacing -- the matching rule folds both.
        assertThat(this.jobGradeService.capture("632005", "  prokureur ")).isSameAs(typed);
        verify(this.jobGradeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("a blank grade is never captured")
    void blankIsNeverCaptured() {
        assertThat(this.jobGradeService.capture("632005", "   ")).isNull();
        assertThat(this.jobGradeService.capture("632005", null)).isNull();
        verifyNoInteractions(this.jobGradeRepository, this.bankRepository);
    }

    @Test
    @DisplayName("a captured grade keeps the case it was typed in, and is stored unverified")
    void capturedGradeKeepsItsCaseAndIsUnverified() {
        Bank bank = new Bank();
        bank.setId(1);
        when(this.jobGradeRepository.findByBank_UniversalBranchCode("Old Mutual")).thenReturn(List.of());
        when(this.bankRepository.findByNameIgnoreCase("Old Mutual")).thenReturn(Optional.of(bank));
        when(this.jobGradeRepository.save(org.mockito.ArgumentMatchers.any(JobGrade.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobGrade captured = this.jobGradeService.capture("Old Mutual", "  Grade   c  ");

        // Whitespace is collapsed; CASE IS NOT TOUCHED. "Grade c" is a real grade
        // with a lowercase c sitting beside "Grade K" -- upper-casing it to tidy the
        // data would corrupt the union's own reference values.
        assertThat(captured.getGrade()).isEqualTo("Grade c");
        assertThat(captured.getVerified()).isFalse();
        assertThat(captured.getBank()).isSameAs(bank);
    }
}

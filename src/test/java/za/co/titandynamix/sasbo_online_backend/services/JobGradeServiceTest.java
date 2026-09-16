package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
        return new JobGrade(id, bank, value, null, null,null);
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
}

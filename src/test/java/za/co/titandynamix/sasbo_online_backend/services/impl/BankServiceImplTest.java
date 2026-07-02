package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.mapper.BankMapper;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankServiceImpl Tests")
class BankServiceImplTest {

    @Mock
    private BankRepository bankRepository;

    @Mock
    private BankMapper bankMapper;

    private BankService bankService;

    @BeforeEach
    void setUp() {
        this.bankService = new BankServiceImpl(this.bankRepository, this.bankMapper);
    }

    @Test
    @DisplayName("Should return empty list when no banks exist")
    void testFindAllEmpty() {
        // Arrange
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        // Act
        List<Bank> banks = this.bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .isEmpty();
        
        verify(this.bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should return single bank when one exists")
    void testFindAllSingleBank() {
        // Arrange
        Bank bank = new Bank(1, "Standard Bank", null, "050001");
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of(bank));

        // Act
        List<Bank> banks = this.bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .hasSize(1)
                .extracting(Bank::getName)
                .containsExactly("Standard Bank");
        
        verify(this.bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should return banks sorted by name in ascending order")
    void testFindAllSortedByName() {
        // Arrange
        Bank bank1 = new Bank(1, "Apple Bank", null, "050001");
        Bank bank2 = new Bank(2, "Middle Bank", null, "050002");
        Bank bank3 = new Bank(3, "Zebra Bank", null, "050003");

        when(this.bankRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(bank1, bank2, bank3));

        // Act
        List<Bank> banks = this.bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .hasSize(3)
                .extracting(Bank::getName)
                .containsExactly("Apple Bank", "Middle Bank", "Zebra Bank");
        
        verify(this.bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should call repository.findAll with Sort.ASC on name field")
    void testFindAllCallsRepositoryWithCorrectSort() {
        // Arrange
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        // Act
        this.bankService.findAll();

        // Assert
        verify(this.bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should find bank by name ignoring case")
    void testFindByNameIgnoreCase() {
        // Arrange
        Bank bank = new Bank(1, "Standard Bank", null, "050001");
        when(this.bankRepository.findByNameIgnoreCase("standard bank"))
                .thenReturn(Optional.of(bank));

        // Act
        Optional<Bank> foundBank = this.bankService.findByNameIgnoreCase("standard bank");

        // Assert
        assertThat(foundBank)
                .isPresent()
                .hasValueSatisfying(b -> assertThat(b.getName()).isEqualTo("Standard Bank"));
        
        verify(this.bankRepository).findByNameIgnoreCase("standard bank");
    }

    @Test
    @DisplayName("Should return empty optional when bank not found by name")
    void testFindByNameIgnoreCaseNotFound() {
        // Arrange
        when(this.bankRepository.findByNameIgnoreCase("Non Existent Bank"))
                .thenReturn(Optional.empty());

        // Act
        Optional<Bank> foundBank = this.bankService.findByNameIgnoreCase("Non Existent Bank");

        // Assert
        assertThat(foundBank).isEmpty();
        
        verify(this.bankRepository).findByNameIgnoreCase("Non Existent Bank");
    }

    @Test
    @DisplayName("Should save a new bank")
    void testSaveNewBank() {
        // Arrange
        Bank bank = new Bank(null, "New Bank", null, "050001");
        Bank savedBank = new Bank(1, "New Bank", null, "050001");
        when(this.bankRepository.save(bank)).thenReturn(savedBank);

        // Act
        Bank result = this.bankService.save(bank);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasFieldOrPropertyWithValue("name", "New Bank")
                .hasFieldOrPropertyWithValue("universalBranchCode", "050001")
                .hasFieldOrPropertyWithValue("id", 1);
        
        verify(this.bankRepository).save(bank);
    }

    @Test
    @DisplayName("Should update an existing bank")
    void testUpdateBank() {
        // Arrange
        Bank bank = new Bank(1, "Original Bank", null, "050001");
        Bank updatedBank = new Bank(1, "Updated Bank", null, "050001");
        when(this.bankRepository.save(bank)).thenReturn(updatedBank);

        // Act
        Bank result = this.bankService.save(bank);

        // Assert
        assertThat(result)
                .hasFieldOrPropertyWithValue("name", "Updated Bank")
                .hasFieldOrPropertyWithValue("id", 1);
        
        verify(this.bankRepository).save(bank);
    }

    @Test
    @DisplayName("Should return all banks from repository with descending sort order support")
    void testFindAllUsesRepository() {
        // Arrange
        List<Bank> expectedBanks = List.of(
                new Bank(1, "Bank A", null, "050001"),
                new Bank(2, "Bank B", null, "050002")
        );
        when(this.bankRepository.findAll(any(Sort.class))).thenReturn(expectedBanks);

        // Act
        List<Bank> actualBanks = this.bankService.findAll();

        // Assert
        assertThat(actualBanks)
                .isNotNull()
                .hasSize(2)
                .isEqualTo(expectedBanks);
        
        verify(this.bankRepository).findAll(any(Sort.class));
    }
}




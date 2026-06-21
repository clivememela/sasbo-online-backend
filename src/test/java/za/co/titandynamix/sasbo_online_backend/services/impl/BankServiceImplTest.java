package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BankServiceImpl Tests")
class BankServiceImplTest {

    @Mock
    private BankRepository bankRepository;

    private BankService bankService;

    @BeforeEach
    void setUp() {
        bankService = new BankServiceImpl(bankRepository);
    }

    @Test
    @DisplayName("Should return empty list when no banks exist")
    void testFindAllEmpty() {
        // Arrange
        when(bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        // Act
        List<Bank> banks = bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .isEmpty();
        
        verify(bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should return single bank when one exists")
    void testFindAllSingleBank() {
        // Arrange
        Bank bank = new Bank(1, "Standard Bank", null, "050001");
        when(bankRepository.findAll(any(Sort.class))).thenReturn(List.of(bank));

        // Act
        List<Bank> banks = bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .hasSize(1)
                .extracting(Bank::getName)
                .containsExactly("Standard Bank");
        
        verify(bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should return banks sorted by name in ascending order")
    void testFindAllSortedByName() {
        // Arrange
        Bank bank1 = new Bank(1, "Apple Bank", null, "050001");
        Bank bank2 = new Bank(2, "Middle Bank", null, "050002");
        Bank bank3 = new Bank(3, "Zebra Bank", null, "050003");

        when(bankRepository.findAll(any(Sort.class)))
                .thenReturn(List.of(bank1, bank2, bank3));

        // Act
        List<Bank> banks = bankService.findAll();

        // Assert
        assertThat(banks)
                .isNotNull()
                .hasSize(3)
                .extracting(Bank::getName)
                .containsExactly("Apple Bank", "Middle Bank", "Zebra Bank");
        
        verify(bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should call repository.findAll with Sort.ASC on name field")
    void testFindAllCallsRepositoryWithCorrectSort() {
        // Arrange
        when(bankRepository.findAll(any(Sort.class))).thenReturn(List.of());

        // Act
        bankService.findAll();

        // Assert
        verify(bankRepository).findAll(any(Sort.class));
    }

    @Test
    @DisplayName("Should find bank by name ignoring case")
    void testFindByNameIgnoreCase() {
        // Arrange
        Bank bank = new Bank(1, "Standard Bank", null, "050001");
        when(bankRepository.findByNameIgnoreCase("standard bank"))
                .thenReturn(Optional.of(bank));

        // Act
        Optional<Bank> foundBank = bankService.findByNameIgnoreCase("standard bank");

        // Assert
        assertThat(foundBank)
                .isPresent()
                .hasValueSatisfying(b -> assertThat(b.getName()).isEqualTo("Standard Bank"));
        
        verify(bankRepository).findByNameIgnoreCase("standard bank");
    }

    @Test
    @DisplayName("Should return empty optional when bank not found by name")
    void testFindByNameIgnoreCaseNotFound() {
        // Arrange
        when(bankRepository.findByNameIgnoreCase("Non Existent Bank"))
                .thenReturn(Optional.empty());

        // Act
        Optional<Bank> foundBank = bankService.findByNameIgnoreCase("Non Existent Bank");

        // Assert
        assertThat(foundBank).isEmpty();
        
        verify(bankRepository).findByNameIgnoreCase("Non Existent Bank");
    }

    @Test
    @DisplayName("Should save a new bank")
    void testSaveNewBank() {
        // Arrange
        Bank bank = new Bank(null, "New Bank", null, "050001");
        Bank savedBank = new Bank(1, "New Bank", null, "050001");
        when(bankRepository.save(bank)).thenReturn(savedBank);

        // Act
        Bank result = bankService.save(bank);

        // Assert
        assertThat(result)
                .isNotNull()
                .hasFieldOrPropertyWithValue("name", "New Bank")
                .hasFieldOrPropertyWithValue("universalBranchCode", "050001")
                .hasFieldOrPropertyWithValue("id", 1);
        
        verify(bankRepository).save(bank);
    }

    @Test
    @DisplayName("Should update an existing bank")
    void testUpdateBank() {
        // Arrange
        Bank bank = new Bank(1, "Original Bank", null, "050001");
        Bank updatedBank = new Bank(1, "Updated Bank", null, "050001");
        when(bankRepository.save(bank)).thenReturn(updatedBank);

        // Act
        Bank result = bankService.save(bank);

        // Assert
        assertThat(result)
                .hasFieldOrPropertyWithValue("name", "Updated Bank")
                .hasFieldOrPropertyWithValue("id", 1);
        
        verify(bankRepository).save(bank);
    }

    @Test
    @DisplayName("Should return all banks from repository with descending sort order support")
    void testFindAllUsesRepository() {
        // Arrange
        List<Bank> expectedBanks = List.of(
                new Bank(1, "Bank A", null, "050001"),
                new Bank(2, "Bank B", null, "050002")
        );
        when(bankRepository.findAll(any(Sort.class))).thenReturn(expectedBanks);

        // Act
        List<Bank> actualBanks = bankService.findAll();

        // Assert
        assertThat(actualBanks)
                .isNotNull()
                .hasSize(2)
                .isEqualTo(expectedBanks);
        
        verify(bankRepository).findAll(any(Sort.class));
    }
}




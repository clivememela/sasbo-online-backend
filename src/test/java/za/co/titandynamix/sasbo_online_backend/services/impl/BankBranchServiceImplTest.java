package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A29's server half: a branch save that cannot be made is refused in words, with a code, before anything is written.
 *
 * <p>Before it, an unknown id threw a bare {@code RuntimeException} -- the catch-all's 500, "Something went wrong on
 * our side" -- and a code another branch of the bank holds was refused only by the database's unique key, which the
 * catch-all answered with its sentence about "a member with the same identity number".
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("BankBranchServiceImpl -- A29's refusals")
class BankBranchServiceImplTest {

    @Mock private BankBranchRepository bankBranchRepository;
    @Mock private BankService bankService;

    private BankBranchServiceImpl service;
    private Bank absa;

    @BeforeEach
    void setUp() {
        this.service = new BankBranchServiceImpl(this.bankBranchRepository, this.bankService);
        this.absa = new Bank();
        this.absa.setId(3);
        this.absa.setName("Absa Bank Limited");
        when(this.bankService.findByNameIgnoreCase("Absa Bank Limited")).thenReturn(Optional.of(this.absa));
        when(this.bankBranchRepository.save(any(BankBranch.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static BankBranchDto edit(String branchCode) {
        BankBranchDto dto = new BankBranchDto();
        dto.setBankName("Absa Bank Limited");
        dto.setBranch("Sandton");
        dto.setBranchCode(branchCode);
        return dto;
    }

    private BankBranch storedBranch(int id) {
        BankBranch branch = new BankBranch();
        branch.setId(id);
        branch.setBank(this.absa);
        branch.setBranchName("Sandton");
        branch.setBranchCode("632005");
        branch.setPhysicalAddress(PhysicalAddress.builder().streetNumber("15").streetName("Alice Lane").postalCode("2196").build());
        when(this.bankBranchRepository.findById(id)).thenReturn(Optional.of(branch));
        return branch;
    }

    @Test
    @DisplayName("🔴 a branch not on record is BRANCH_NOT_FOUND, in words, and nothing is written")
    void aBranchNotOnRecordIsRefusedInWords() {
        when(this.bankBranchRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> this.service.updateFromDto(99, edit("632005")))
                .isInstanceOf(ReferenceRefusal.class)
                .hasMessage("That branch is no longer on record. Reload the branch list, then try again.")
                .satisfies(thrown -> assertThat(((ReferenceRefusal) thrown).code())
                        .isEqualTo(ReferenceRefusalCode.BRANCH_NOT_FOUND));
        verify(this.bankBranchRepository, never()).save(any(BankBranch.class));
    }

    @Test
    @DisplayName("🔴 a code another branch of the bank holds is BRANCH_CODE_TAKEN, naming both, and nothing is written")
    void aCodeAnotherBranchHoldsIsRefusedInWords() {
        storedBranch(4);
        when(this.bankBranchRepository.existsByBank_IdAndBranchCodeAndIdNot(3, "632009", 4)).thenReturn(true);

        assertThatThrownBy(() -> this.service.updateFromDto(4, edit("632009")))
                .isInstanceOf(ReferenceRefusal.class)
                .hasMessage("Another Absa Bank Limited branch already has branch code 632009. "
                        + "Each branch of a bank has its own code.")
                .satisfies(thrown -> assertThat(((ReferenceRefusal) thrown).details())
                        .containsEntry("branchCode", "632009").containsEntry("bankName", "Absa Bank Limited"));
        verify(this.bankBranchRepository, never()).save(any(BankBranch.class));
    }

    @Test
    @DisplayName("🔴 a new branch with a code the bank already has is refused the same way")
    void aNewBranchWithATakenCodeIsRefused() {
        when(this.bankBranchRepository.existsByBank_IdAndBranchCode(3, "632005")).thenReturn(true);

        assertThatThrownBy(() -> this.service.createFromDto(edit("632005")))
                .isInstanceOf(ReferenceRefusal.class)
                .satisfies(thrown -> assertThat(((ReferenceRefusal) thrown).code())
                        .isEqualTo(ReferenceRefusalCode.BRANCH_CODE_TAKEN));
        verify(this.bankBranchRepository, never()).save(any(BankBranch.class));
    }

    @Test
    @DisplayName("the must-pass: a branch keeping its own code saves, and a save naming no address keeps the stored one")
    void aBranchKeepingItsOwnCodeSavesAndKeepsItsAddress() {
        BankBranch stored = storedBranch(4);
        PhysicalAddress address = stored.getPhysicalAddress();
        // Its own code is not "taken": the query excludes the branch being saved.
        when(this.bankBranchRepository.existsByBank_IdAndBranchCodeAndIdNot(3, "632005", 4)).thenReturn(false);
        BankBranchDto dto = edit("632005");
        dto.setManagerName("Synthetic Manager");

        BankBranch saved = this.service.updateFromDto(4, dto);

        assertThat(saved.getManagerName()).isEqualTo("Synthetic Manager");
        assertThat(saved.getPhysicalAddress()).as("the dialog sends no address when it was not changed").isSameAs(address);
        verify(this.bankBranchRepository).save(stored);
    }
}

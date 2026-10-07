package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.ReferenceRefusalCode;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BankBranchServiceImpl implements BankBranchService {

    private final BankBranchRepository bankBranchRepository;
    private final BankService bankService;

    @Override
    public List<BankBranch> findAll() {
        return this.bankBranchRepository.findAll();
    }

    @Override
    public Optional<BankBranch> findById(Integer id) {
        return this.bankBranchRepository.findById(id);
    }

    @Override
    public BankBranch save(BankBranch branch) {
        return this.bankBranchRepository.save(branch);
    }

    /** A new branch, of a bank already on record (see {@link #bankNamed}). */
    @Override
    @Transactional
    public BankBranch createFromDto(BankBranchDto dto) {
        Bank bank = bankNamed(dto.getBankName());
        refuseATakenCode(bank, dto.getBranchCode(), null);

        BankBranch branch = new BankBranch();
        branch.setBank(bank);
        branch.setBankAbbreviation(dto.getBankAbbreviation());
        branch.setBranchName(dto.getBranch());
        branch.setBranchCode(dto.getBranchCode());
        branch.setArea(dto.getArea());
        branch.setRandomAccessNumber(dto.getRandomAccessNumber());
        branch.setPhoneNumber(dto.getPhoneNumber());
        branch.setManagerName(dto.getManagerName());
        branch.setFax(dto.getFax());
        branch.setEmail(dto.getEmail());
        branch.setPostalAddress(dto.getPostalAddress());
        if (dto.getPhysicalAddress() != null) {
            branch.setPhysicalAddress(new PhysicalAddress(dto.getPhysicalAddress()));
        }
        branch.setSasboAreaCode(dto.getSasboAreaCode());
        branch.setProvinceCommitteeCode(dto.getProvinceCommitteeCode());
        branch.setProvinceCommitteeName(dto.getProvinceCommitteeName());
        branch.setBankDepartmentCode(dto.getBankDepartmentCode());
        branch.setTableCode(dto.getTableCode());
        branch.setSubsValueBranch(dto.getSubsValueBranch());
        branch.setBranchStaffComplement(dto.getBranchStaffComplement());
        branch.setBranchMemberComplement(dto.getBranchMemberComplement());
        return this.bankBranchRepository.save(branch);
    }

    /**
     * A29's server half: a branch that is not on record is a 404 in words -- it was a bare {@code RuntimeException},
     * the catch-all's 500 "Something went wrong on our side" -- and a code another branch of the bank holds is refused
     * before the database refuses it. A transaction, as {@link #createFromDto} is.
     *
     * <p>A {@code null} physical address keeps the stored one: the branch dialog sends the address only when it was
     * changed, because a line of text cannot carry back the suburb, city and province rows it was formatted from.
     */
    @Override
    @Transactional
    public BankBranch updateFromDto(Integer id, BankBranchDto dto) {
        BankBranch branch = this.bankBranchRepository.findById(id)
                .orElseThrow(() -> new ReferenceRefusal(ReferenceRefusalCode.BRANCH_NOT_FOUND,
                        "That branch is no longer on record. Reload the branch list, then try again.",
                        Map.of("branchId", String.valueOf(id))));
        Bank bank = bankNamed(dto.getBankName());
        refuseATakenCode(bank, dto.getBranchCode(), id);
        branch.setBank(bank);
        branch.setBankAbbreviation(dto.getBankAbbreviation());
        branch.setBranchName(dto.getBranch());
        branch.setBranchCode(dto.getBranchCode());
        branch.setArea(dto.getArea());
        branch.setRandomAccessNumber(dto.getRandomAccessNumber());
        branch.setPhoneNumber(dto.getPhoneNumber());
        branch.setManagerName(dto.getManagerName());
        branch.setFax(dto.getFax());
        branch.setEmail(dto.getEmail());
        branch.setPostalAddress(dto.getPostalAddress());
        if (dto.getPhysicalAddress() != null) {
            branch.setPhysicalAddress(new PhysicalAddress(dto.getPhysicalAddress()));
        }
        branch.setSasboAreaCode(dto.getSasboAreaCode());
        branch.setProvinceCommitteeCode(dto.getProvinceCommitteeCode());
        branch.setProvinceCommitteeName(dto.getProvinceCommitteeName());
        branch.setBankDepartmentCode(dto.getBankDepartmentCode());
        branch.setTableCode(dto.getTableCode());
        branch.setSubsValueBranch(dto.getSubsValueBranch());
        branch.setBranchStaffComplement(dto.getBranchStaffComplement());
        branch.setBranchMemberComplement(dto.getBranchMemberComplement());
        return this.bankBranchRepository.save(branch);
    }

    /**
     * The bank on record with this name, or a refusal in words: a branch belongs to a bank that is already there.
     *
     * <p>🔴 A bank name that matched none used to CREATE a bank, silently, with a blank universal code -- so a typo in
     * Bank Name ("Absa Bnak") added a bank to every bank list in the application, and moved the branch onto it. The
     * received name is not repeated in the refusal: the dialog still shows what was typed.
     */
    private Bank bankNamed(String name) {
        String wanted = name == null ? "" : name.trim();
        return this.bankService.findByNameIgnoreCase(wanted).orElseThrow(() -> new ReferenceRefusal(
                ReferenceRefusalCode.BANK_UNKNOWN,
                "There is no bank of that name on record. Check the spelling against the bank list: a branch is added "
                        + "to a bank that is already there."));
    }

    /**
     * A code another branch of the same bank already holds, refused in words before the save. The unique key
     * {@code (bank_id, branch_code)} would refuse it in the database, and that failure answered the catch-all's 409
     * about "a member with the same identity number". The key stays as the net under a race.
     */
    private void refuseATakenCode(Bank bank, String branchCode, Integer branchId) {
        if (branchCode == null || branchCode.isBlank() || bank.getId() == null) {
            return;
        }
        boolean taken = branchId == null
                ? this.bankBranchRepository.existsByBank_IdAndBranchCode(bank.getId(), branchCode)
                : this.bankBranchRepository.existsByBank_IdAndBranchCodeAndIdNot(bank.getId(), branchCode, branchId);
        if (taken) {
            throw new ReferenceRefusal(ReferenceRefusalCode.BRANCH_CODE_TAKEN,
                    "Another " + bank.getName() + " branch already has branch code " + branchCode
                            + ". Each branch of a bank has its own code.",
                    Map.of("bankName", bank.getName(), "branchCode", branchCode));
        }
    }

    @Override
    public List<BankBranch> search(String bankName, String branch, String area, String randomAccessNumber) {
        // Sanitize inputs: strip characters that have no place in a name/code search
        // This prevents LDAP injection and XSS from being reflected in error messages
        String safeBankName = sanitize(bankName);
        String safeBranch = sanitize(branch);
        String safeArea = sanitize(area);
        String safeRan = sanitize(randomAccessNumber);

        return this.bankBranchRepository.findAll().stream()
                .filter(b -> safeBankName.isBlank() || b.getBankName().toLowerCase(Locale.ROOT).contains(safeBankName.toLowerCase(Locale.ROOT)))
                .filter(b -> safeBranch.isBlank() || (b.getBranchName() != null && b.getBranchName().toLowerCase(Locale.ROOT).contains(safeBranch.toLowerCase(Locale.ROOT))))
                .filter(b -> safeArea.isBlank() || (b.getArea() != null && b.getArea().toLowerCase(Locale.ROOT).contains(safeArea.toLowerCase(Locale.ROOT))))
                .filter(b -> safeRan.isBlank() || (b.getRandomAccessNumber() != null && b.getRandomAccessNumber().toLowerCase(Locale.ROOT).contains(safeRan.toLowerCase(Locale.ROOT))))
                .toList();
    }

    /** Strip characters that are not alphanumeric, spaces, hyphens or dots. */
    private static String sanitize(String input) {
        if (input == null) return "";
        return input.replaceAll("[^\\w\\s.\\-]", "").trim();
    }
}

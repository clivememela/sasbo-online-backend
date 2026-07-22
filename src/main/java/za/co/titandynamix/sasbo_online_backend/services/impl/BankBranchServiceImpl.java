package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;
import java.util.Locale;
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

    @Override
    public BankBranch createFromDto(BankBranchDto dto) {
        Bank bank = this.bankService.findByNameIgnoreCase(dto.getBankName()).orElseGet(() -> {
            Bank b = new Bank();
            b.setName(dto.getBankName());
            b.setUniversalBranchCode("");
            return this.bankService.save(b);
        });

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

    @Override
    public BankBranch updateFromDto(Integer id, BankBranchDto dto) {
        BankBranch branch = this.bankBranchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("BankBranch not found: " + id));
        Bank bank = this.bankService.findByNameIgnoreCase(dto.getBankName()).orElseGet(() -> {
            Bank b = new Bank();
            b.setName(dto.getBankName());
            b.setUniversalBranchCode("");
            return this.bankService.save(b);
        });
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

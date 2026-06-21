package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;

import java.util.List;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankBranchRepository bankBranchRepository;
    private final BankRepository bankRepository;

    @GetMapping
    public List<BankBranch> getAll() {
        return this.bankBranchRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<BankBranch> create(@RequestBody BankBranchDto dto) {
        Bank bank = this.bankRepository.findByNameIgnoreCase(dto.getBankName()).orElseGet(() -> {
            Bank b = new Bank();
            b.setName(dto.getBankName());
            b.setUniversalBranchCode("");
            return this.bankRepository.save(b);
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

        return ResponseEntity.ok(this.bankBranchRepository.save(branch));
    }
}

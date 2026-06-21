package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.entity.location.PhysicalAddress;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankBranchService bankBranchService;

    private final BankService bankService;

    @GetMapping
    public List<Bank> getAllBanks() {
        return this.bankService.findAll();
    }

    @GetMapping("/branches")
    public List<BankBranch> getAll() {
        return this.bankBranchService.findAll();
    }

    @PostMapping("/branches")
    public ResponseEntity<BankBranch> create(@RequestBody BankBranchDto dto) {
        Bank bank = this.bankService.findByNameIgnoreCase(dto.getBankName()).orElseGet(() -> {
            Bank b = new Bank();
            b.setName(dto.getBankName());
            b.setUniversalBranchCode("");
            return this.bankService.save(b);
        });

        BankBranch bankBranch = new BankBranch();
        bankBranch.setBank(bank);
        bankBranch.setBankAbbreviation(dto.getBankAbbreviation());
        bankBranch.setBranchName(dto.getBranch());
        bankBranch.setBranchCode(dto.getBranchCode());
        bankBranch.setArea(dto.getArea());
        bankBranch.setRandomAccessNumber(dto.getRandomAccessNumber());
        bankBranch.setPhoneNumber(dto.getPhoneNumber());
        bankBranch.setManagerName(dto.getManagerName());
        bankBranch.setFax(dto.getFax());
        bankBranch.setEmail(dto.getEmail());
        bankBranch.setPostalAddress(dto.getPostalAddress());
        if (dto.getPhysicalAddress() != null) {
            bankBranch.setPhysicalAddress(new PhysicalAddress(dto.getPhysicalAddress()));
        }
        bankBranch.setSasboAreaCode(dto.getSasboAreaCode());
        bankBranch.setProvinceCommitteeCode(dto.getProvinceCommitteeCode());
        bankBranch.setProvinceCommitteeName(dto.getProvinceCommitteeName());
        bankBranch.setBankDepartmentCode(dto.getBankDepartmentCode());
        bankBranch.setTableCode(dto.getTableCode());
        bankBranch.setSubsValueBranch(dto.getSubsValueBranch());
        bankBranch.setBranchStaffComplement(dto.getBranchStaffComplement());
        bankBranch.setBranchMemberComplement(dto.getBranchMemberComplement());

        return ResponseEntity.ok((BankBranch) this.bankBranchService.save(bankBranch));
    }
}

package za.co.titandynamix.sasbo_online_backend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.dto.bank.BankDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankService;

import java.util.List;

@RestController
@RequestMapping("/api/banks")
@RequiredArgsConstructor
public class BankController {

    private final BankService bankService;
    private final BankBranchService bankBranchService;

    @GetMapping
    public List<BankDto> getAllBanks() {
        return this.bankService.findAllDtos();
    }

    @GetMapping("/branches")
    public List<BankBranch> getAllBranches() {
        return this.bankBranchService.findAll();
    }

    @PostMapping("/branches")
    public ResponseEntity<BankBranch> createBranch(@Valid @RequestBody BankBranchDto dto) {
        return ResponseEntity.ok(this.bankBranchService.createFromDto(dto));
    }

    @PutMapping("/branches/{id}")
    public ResponseEntity<BankBranch> updateBranch(@PathVariable Integer id, @Valid @RequestBody BankBranchDto dto) {
        return ResponseEntity.ok(this.bankBranchService.updateFromDto(id, dto));
    }
}

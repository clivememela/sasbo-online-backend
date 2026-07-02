package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;

import java.util.List;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BankBranchController {

    private final BankBranchService bankBranchService;

    @GetMapping
    public List<BankBranch> search(
            @RequestParam(defaultValue = "") String bankName,
            @RequestParam(defaultValue = "") String branch,
            @RequestParam(defaultValue = "") String area,
            @RequestParam(defaultValue = "") String randomAccessNumber
    ) {
        return this.bankBranchService.search(bankName, branch, area, randomAccessNumber);
    }
}

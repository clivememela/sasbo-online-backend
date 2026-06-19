package za.co.titandynamix.sasbo_online_backend.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BankBranchController {

    private final BankBranchRepository branchRepository;

    @GetMapping
    public List<BankBranch> search(
            @RequestParam(defaultValue = "") String bankName,
            @RequestParam(defaultValue = "") String branch,
            @RequestParam(defaultValue = "") String area,
            @RequestParam(defaultValue = "") String randomAccessNumber
    ) {
        return branchRepository.findAll().stream()
                .filter(b -> bankName.isBlank() || b.getBankName().toLowerCase().contains(bankName.toLowerCase()))
                .filter(b -> branch.isBlank() || (b.getBranchName() != null && b.getBranchName().toLowerCase().contains(branch.toLowerCase())))
                .filter(b -> area.isBlank() || (b.getArea() != null && b.getArea().toLowerCase().contains(area.toLowerCase())))
                .filter(b -> randomAccessNumber.isBlank() || (b.getRandomAccessNumber() != null && b.getRandomAccessNumber().toLowerCase().contains(randomAccessNumber.toLowerCase())))
                .toList();
    }
}

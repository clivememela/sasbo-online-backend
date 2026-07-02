package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;
import za.co.titandynamix.sasbo_online_backend.services.BankingService;

@Service
@RequiredArgsConstructor
public class BankingServiceImpl implements BankingService {

    private final BankBranchService bankBranchService;

    @Override
    public String getBankNameFromBranch(Long branchId) {
        return this.bankBranchService.findById(branchId.intValue())
                .map(BankBranch::getBankName)
                .orElseThrow(() -> new EntityNotFoundException("Branch not found: " + branchId));
    }
}

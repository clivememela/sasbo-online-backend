package za.co.titandynamix.sasbo_online_backend.services.impl;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankingService;

@Service
public class BankingServiceImpl implements BankingService {

    @Autowired
    private BankBranchRepository repository;

    @Override
    public String getBankNameFromBranch(Long branchId) {
        BankBranch branch = this.repository.findById(Math.toIntExact(branchId))
                                    .orElseThrow(() -> new EntityNotFoundException("Branch not found"));

        return branch.getBankName(); // Returns e.g., "Standard Bank"
    }
}

package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;
import za.co.titandynamix.sasbo_online_backend.repository.BankBranchRepository;
import za.co.titandynamix.sasbo_online_backend.services.BankBranchService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BankBranchServiceImpl implements BankBranchService {

    private final BankBranchRepository bankBranchRepository;

    @Override
    public List<BankBranch> findAll() {
        return this.bankBranchRepository.findAll();
    }

    @Override
    public BankBranch save(BankBranch branch) {
        return this.bankBranchRepository.save(branch);
    }
}


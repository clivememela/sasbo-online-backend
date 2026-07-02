package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.dto.bank.BankBranchDto;
import za.co.titandynamix.sasbo_online_backend.entity.bank.BankBranch;

import java.util.List;
import java.util.Optional;

public interface BankBranchService {
    List<BankBranch> findAll();
    Optional<BankBranch> findById(Integer id);
    BankBranch save(BankBranch branch);
    BankBranch createFromDto(BankBranchDto dto);
    List<BankBranch> search(String bankName, String branch, String area, String randomAccessNumber);
}

package za.co.titandynamix.sasbo_online_backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobGradeService {
    private final JobGradeRepository repository;
    private final BankRepository bankRepository;

    /**
     * Find job grades by a bank identifier. Accepts either a bankCode (universalBranchCode),
     * a numeric bank id, or a human-readable bank name.
     */
    public List<JobGrade> findByBankIdentifier(String bankCodeOrNameOrId) {
        if (bankCodeOrNameOrId == null || bankCodeOrNameOrId.isBlank()) return Collections.emptyList();

        String candidate = bankCodeOrNameOrId.trim();

        // Try by universal branch code first: the front end resolves the selected bank to its
        // (numeric) universal code, so a numeric candidate must not be mistaken for a bank id.
        var byCode = repository.findByBank_UniversalBranchCode(candidate);
        if (byCode != null && !byCode.isEmpty()) return byCode;

        // If it looks numeric, fall back to lookup by bank id
        try {
            Integer id = Integer.valueOf(candidate);
            var byId = repository.findByBank_Id(id);
            if (byId != null && !byId.isEmpty()) return byId;
        } catch (NumberFormatException ignored) {
        }

        // Finally try to resolve by bank name (case-insensitive)
        return bankRepository.findByNameIgnoreCase(candidate)
                .map(b -> repository.findByBank_Id(b.getId()))
                .orElse(Collections.emptyList());
    }
}


package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JobGradeServiceImpl implements JobGradeService {

    private final JobGradeRepository jobGradeRepository;
    private final BankRepository bankRepository;

    /**
     * Find job grades by a bank identifier. Accepts either a bankCode (universalBranchCode),
     * a numeric bank id, or a human-readable bank name.
     */
    public List<JobGrade> findByBankIdentifier(String bankCodeOrNameOrId) {
        if (bankCodeOrNameOrId == null || bankCodeOrNameOrId.isBlank()) return Collections.emptyList();

        String candidate = bankCodeOrNameOrId.trim();

        // 1. Try by universal branch code first
        var byCode = jobGradeRepository.findByBank_UniversalBranchCode(candidate);
        if (byCode != null && !byCode.isEmpty()) return byCode;

        // 2. If it is numeric AND short (likely a real table primary key ID), fall back to lookup by bank id
        if (candidate.matches("\\d+") && candidate.length() < 5) {
            try {
                Integer id = Integer.valueOf(candidate);
                var byId = jobGradeRepository.findByBank_Id(id);
                if (byId != null && !byId.isEmpty()) return byId;
            } catch (NumberFormatException ignored) {
            }
        }

        // 3. Finally try to resolve by bank name (case-insensitive)
        return bankRepository.findByNameIgnoreCase(candidate)
                       .map(b -> jobGradeRepository.findByBank_Id(b.getId()))
                       .orElse(Collections.emptyList());
    }

}


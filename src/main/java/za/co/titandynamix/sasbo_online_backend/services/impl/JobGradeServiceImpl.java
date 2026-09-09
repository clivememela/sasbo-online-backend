package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import za.co.titandynamix.sasbo_online_backend.entity.bank.Bank;
import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;
import za.co.titandynamix.sasbo_online_backend.repository.BankRepository;
import za.co.titandynamix.sasbo_online_backend.repository.JobGradeRepository;
import za.co.titandynamix.sasbo_online_backend.services.JobGradeService;
import za.co.titandynamix.sasbo_online_backend.utils.JobGradeMatcher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobGradeServiceImpl implements JobGradeService {

    private final JobGradeRepository jobGradeRepository;
    private final BankRepository bankRepository;

    /**
     * Find job grades by a bank identifier. Accepts either a bankCode (universalBranchCode),
     * a numeric bank id, or a human-readable bank name.
     */
    @Override
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

    @Override
    public List<JobGrade> findVerifiedByBankIdentifier(String bankCodeOrNameOrId) {
        return findByBankIdentifier(bankCodeOrNameOrId).stream()
                .filter(jg -> Boolean.TRUE.equals(jg.getVerified()))
                .toList();
    }

    @Override
    public JobGrade capture(String bankCodeOrNameOrId, String grade) {
        if (JobGradeMatcher.isBlank(grade)) {
            return null;
        }

        // Idempotence first, and it is not an optimisation: `resolveJobGrade` runs on
        // create AND update, so without this, editing a member who carries a captured
        // grade would insert a fresh near-duplicate row on every single save.
        Optional<JobGrade> existing = findByBankIdentifier(bankCodeOrNameOrId).stream()
                .filter(jg -> JobGradeMatcher.matches(grade, jg.getGrade()))
                .findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }

        Bank bank = resolveBank(bankCodeOrNameOrId);
        if (bank == null) {
            // A grade with no bank has nowhere to live. Returning null lets the caller
            // record CLEARED rather than failing the whole save over a reference field.
            log.warn("Cannot capture job grade '{}': no bank matched '{}'", grade, bankCodeOrNameOrId);
            return null;
        }

        JobGrade captured = new JobGrade();
        captured.setBank(bank);
        // Stored EXACTLY as supplied, bar trimming and collapsing whitespace. Case is
        // never changed: "Grade c" is a real grade with a lowercase c, and upper-casing
        // to tidy the data would corrupt the union's own reference values.
        captured.setGrade(JobGradeMatcher.storedForm(grade));
        captured.setVerified(false);
        return jobGradeRepository.save(captured);
    }

    /** The same three-step identifier resolution, returning the bank rather than its grades. */
    private Bank resolveBank(String bankCodeOrNameOrId) {
        if (bankCodeOrNameOrId == null || bankCodeOrNameOrId.isBlank()) {
            return null;
        }
        String candidate = bankCodeOrNameOrId.trim();

        var byCode = jobGradeRepository.findByBank_UniversalBranchCode(candidate);
        if (byCode != null && !byCode.isEmpty()) {
            return byCode.get(0).getBank();
        }

        if (candidate.matches("\\d+") && candidate.length() < 5) {
            try {
                Optional<Bank> byId = bankRepository.findById(Integer.valueOf(candidate));
                if (byId.isPresent()) {
                    return byId.get();
                }
            } catch (NumberFormatException ignored) {
            }
        }

        return bankRepository.findByNameIgnoreCase(candidate).orElse(null);
    }
}

package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;

import java.util.List;

public interface JobGradeService {

    /**
     * Find job grades by a bank identifier. Accepts either a bankCode
     * (universalBranchCode), a numeric bank id, or a human-readable bank name.
     */
    List<JobGrade> findByBankIdentifier(String bankCodeOrNameOrId);

}

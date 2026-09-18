package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.member.JobGrade;

import java.util.List;

public interface JobGradeService {

    /**
     * Find job grades by a bank identifier. Accepts either a bankCode
     * (universalBranchCode), a numeric bank id, or a human-readable bank name.
     *
     * <p>Returns EVERY grade for the bank, verified or not. This is the list a
     * submitted grade is resolved against: a grade captured last week must still
     * resolve, or re-saving that member would capture it a second time.
     */
    List<JobGrade> findByBankIdentifier(String bankCodeOrNameOrId);

    /**
     * The grades offered in the drop-down: the verified master list only.
     *
     * <p>🔴 Deliberately NOT the same list as {@link #findByBankIdentifier}. A grade
     * one administrator typed is stored and reviewable, but offering it to the next
     * user would turn a single typo into everyone's option -- and consistency is the
     * entire reason the field is a drop-down first (ux.md §6c clause ③).
     */
    List<JobGrade> findVerifiedByBankIdentifier(String bankCodeOrNameOrId);

    /**
     * Store a grade an administrator typed that the master list did not contain,
     * against that bank, flagged unverified.
     *
     * <p>Idempotent under the matching rule: capturing a grade that is already
     * captured returns the existing row rather than inserting a near-duplicate, so
     * re-saving a member does not accumulate copies.
     *
     * @return the persisted row, or {@code null} if the bank cannot be resolved --
     *         a grade with no bank has nowhere to live and is not worth a broken save.
     */
    JobGrade capture(String bankCodeOrNameOrId, String grade);
}

package za.co.titandynamix.sasbo_online_backend.services;

import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;

import java.util.List;

/**
 * The people who sign members up (03.10).
 *
 * <p>Read-only by design. Creating a recruiter from the capture screen would let a
 * typo become a permanent option in everybody's drop-down — the same failure 02.5's
 * {@code verified} flag exists to prevent for job grades.
 */
public interface RecruiterService {

    /**
     * The recruiters a member may be attributed to, ordered by name.
     *
     * <p>🔑 <b>NOT {@code findAll()}, and the rename is the point.</b> It excludes the
     * seed's {@code VACANT} rows — see {@link Recruiter#isVacancy} for the owner's
     * decision and for why those rows stay in the table. A method called
     * {@code findAll} that returns fewer than all is one name meaning two things,
     * which is this codebase's most repeated defect ({@code area_id},
     * {@code job_grades.description}, {@code Recruiter}/{@code Representative}).
     */
    List<Recruiter> findSelectable();
}

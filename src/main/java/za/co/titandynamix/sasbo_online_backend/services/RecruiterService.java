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

    /** Every recruiter, ordered by name. */
    List<Recruiter> findAll();
}

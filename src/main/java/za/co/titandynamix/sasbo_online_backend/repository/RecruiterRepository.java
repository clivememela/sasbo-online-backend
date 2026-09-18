package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;

public interface RecruiterRepository extends JpaRepository<Recruiter, Integer> {

    /**
     * The recruiter a capture named -- exact, case-insensitive, the way every other
     * reference lookup here matches. V25 adds the matching functional index so this
     * stays an index scan rather than a sequential one.
     *
     * <h2>🔴 findFirst..., NOT findBy..., and the seed is why</h2>
     * {@code recruiters.name} carries <b>no unique constraint</b>, and the seeded
     * data really does repeat: <b>"Vacant" appears three times</b> -- three area
     * posts with no incumbent. A plain {@code findByNameIgnoreCase} returning
     * {@code Optional} throws {@code NonUniqueResultException} on a duplicate, so
     * an administrator choosing "Vacant" from the drop-down would have received a
     * <b>500 on save</b>, from a value the screen itself offered them.
     *
     * <p>Found by {@code RecruiterRepositoryIT} against a real database on the first
     * run. No unit test could have seen it: a mocked repository returns whatever the
     * test hands it, and the seed is the thing that is wrong.
     *
     * <p>{@code OrderById} makes the choice DETERMINISTIC rather than
     * whatever-the-planner-returns. It is deliberately not a fix for the data: three
     * rows called "Vacant" is a question for whoever maintains that file, and
     * {@code RecruiterSeedTest} keeps it visible until it is answered.
     */
    java.util.Optional<Recruiter> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.reference.Committee;

import java.util.Optional;

/**
 * SASBO's committees (V53) as entities, for the one thing JPA is needed for here: a member's BEC seat
 * points at one (V55). The lists themselves are read through {@code SasboReferenceRepository}.
 */
@Repository
public interface CommitteeRepository extends JpaRepository<Committee, Long> {

    /** SASBO's code for the committee -- unique, and what {@code /api/reference/committees} answers with. */
    Optional<Committee> findByCode(String code);
}

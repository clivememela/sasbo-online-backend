package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.recruiter.RecruiterType;

public interface RecruiterTypeRepository extends JpaRepository<RecruiterType, Long> {
    boolean existsByNameIgnoreCase(String name);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.recruitment.Recruiter;

public interface RecruiterRepository extends JpaRepository<Recruiter, Integer> {
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

public interface RepresentativeRepository extends JpaRepository<Representative, Integer> {
    boolean existsByNameIgnoreCase(String name);
}

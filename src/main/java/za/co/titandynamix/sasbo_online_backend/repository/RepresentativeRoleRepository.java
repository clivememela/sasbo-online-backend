package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;

import java.util.Optional;

@Repository
public interface RepresentativeRoleRepository extends JpaRepository<RepresentativeRole, Integer> {
    Optional<RepresentativeRole> findByNameIgnoreCase(String name);
}

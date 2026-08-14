package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;

import java.util.Optional;

@Repository
public interface UnionStructureRepository extends JpaRepository<UnionStructure, Integer> {
    Optional<UnionStructure> findByNameIgnoreCase(String name);
}

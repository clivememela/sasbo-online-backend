package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.Area;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

import java.util.List;

public interface RepresentativeRepository extends JpaRepository<Representative, Integer> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByMemberId(Integer memberId);
    
    // Filtering methods
    List<Representative> findByArea(Area area);
    List<Representative> findByAreaId(Integer areaId);
    List<Representative> findByRoleId(Integer roleId);

    // "members" must match the variable name of the collection inside your Representative class
    @EntityGraph(attributePaths = {"members"})
    List<Representative> findAll();
}


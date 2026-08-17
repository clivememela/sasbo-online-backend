package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

public interface RepresentativeRepository extends JpaRepository<Representative, Integer> {

    @Override
    @EntityGraph(attributePaths = {"member", "area", "role", "structure", "members"})
    List<Representative> findAll();

    @Override
    @EntityGraph(attributePaths = {"member", "area", "role", "structure", "members"})
    Optional<Representative> findById(Integer id);
    // Matches your entity fields: firstNames and surname
    Optional<Representative> findByFirstNamesAndSurname(String firstNames, String surname);

    /**
     * Looks up a representative by their combined display name
     * (firstNames + ' ' + surname, trimmed). Handles both split and combined storage.
     */
    @Query("SELECT r FROM Representative r WHERE LOWER(TRIM(CONCAT(COALESCE(r.firstNames, ''), ' ', COALESCE(r.surname, '')))) = LOWER(:name)")
    Optional<Representative> findByFullName(@Param("name") String name);
}

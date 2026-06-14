package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;

import java.util.List;

@Repository
public interface SuburbRepository extends JpaRepository<Suburb, Long> {
    List<Suburb> findByTownId(Long townId);
    List<Suburb> findByPostalCode(String postalCode);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;

import java.util.List;

@Repository
public interface TownRepository extends JpaRepository<Town, Long> {
    List<Town> findByCityId(Long cityId);
}

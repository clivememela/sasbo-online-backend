package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.signin.StaffPosition;

import java.util.List;
import java.util.Optional;

/** SASBO's positions (V65), each with its default role. */
public interface StaffPositionRepository extends JpaRepository<StaffPosition, Long> {

    @Query("select p from StaffPosition p join fetch p.defaultRole where p.code = :code")
    Optional<StaffPosition> findByCodeWithRole(@Param("code") String code);

    boolean existsByCode(String code);

    /** Every position in SASBO's order, then by name. */
    @Query("select p from StaffPosition p join fetch p.defaultRole order by p.ordinal, p.name")
    List<StaffPosition> findAllInOrder();
}

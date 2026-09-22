package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbOpeningPosition;

import java.util.Optional;

public interface AcbOpeningPositionRepository extends JpaRepository<AcbOpeningPosition, Long> {

    /** The last capture's row -- who entered it and when; every row of one capture carries the same. */
    Optional<AcbOpeningPosition> findFirstByOrderByEnteredAtDescIdDesc();

    /** A capture replaces the one before it whole. Joins the capture's transaction. */
    @Modifying
    @Transactional
    @Query("DELETE FROM AcbOpeningPosition p")
    int deleteEverything();
}

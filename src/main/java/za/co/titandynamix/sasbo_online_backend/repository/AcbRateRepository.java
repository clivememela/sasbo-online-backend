package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRate;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface AcbRateRepository extends JpaRepository<AcbRate, Long> {

    /** The rate in force on {@code day}: the latest one that had taken effect by then. */
    Optional<AcbRate> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate day);

    /** Every dated rate, oldest first -- one a year, so the list is bounded by the calendar. */
    List<AcbRate> findAllByOrderByEffectiveFromAsc();
}

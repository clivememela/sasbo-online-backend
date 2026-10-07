package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbReturnsBatch;

import java.util.List;

/** The debit days' returns reports, as typed in (DOD-06, V72). */
public interface AcbReturnsBatchRepository extends JpaRepository<AcbReturnsBatch, Long> {

    /** A run's reports, newest debit day first, a page at a time. */
    Page<AcbReturnsBatch> findByRunIdOrderByActionDateDescIdDesc(Long runId, Pageable page);

    /** {@code [actionDate, count]} of a run's reports -- one per debit day, usually (DOD-06). */
    @Query("SELECT b.actionDate, count(b) FROM AcbReturnsBatch b WHERE b.runId = :runId GROUP BY b.actionDate")
    List<Object[]> countByActionDate(@Param("runId") Long runId);
}

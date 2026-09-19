package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbAuditEvent;

import java.util.List;

public interface AcbAuditEventRepository extends JpaRepository<AcbAuditEvent, Long> {

    /** A run's trail, in the order it happened. */
    List<AcbAuditEvent> findByRunIdOrderByOccurredAtAscIdAsc(Long runId);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.co.titandynamix.sasbo_online_backend.entity.signin.AuditEvent;

/**
 * The record of who did what (V67). Written by {@code AuditTrail} only; read a page at a time, filtered by the
 * specifications it builds -- each filter a predicate only when it is asked for.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>, JpaSpecificationExecutor<AuditEvent> {
}

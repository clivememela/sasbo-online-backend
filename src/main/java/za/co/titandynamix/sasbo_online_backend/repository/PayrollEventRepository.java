package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollEvent;

import java.util.List;

/** The payroll module's record of what was done. Written, and read by file. Append-only (V76's trigger). */
public interface PayrollEventRepository extends JpaRepository<PayrollEvent, Long> {

    List<PayrollEvent> findAllByFileIdOrderByOccurredAtAscIdAsc(Long fileId);
}

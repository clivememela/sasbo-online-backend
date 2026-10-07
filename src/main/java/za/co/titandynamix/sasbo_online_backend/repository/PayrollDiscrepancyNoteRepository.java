package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancyNote;

import java.util.List;

/** A discrepancy's notes, in the order they were written -- a handful each. Append-only (V75's trigger). */
public interface PayrollDiscrepancyNoteRepository extends JpaRepository<PayrollDiscrepancyNote, Long> {

    List<PayrollDiscrepancyNote> findAllByDiscrepancyIdOrderByWrittenAtAscIdAsc(Long discrepancyId);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollQueryFile;

import java.util.List;

/** The protected files produced from a query list -- a few a query at most. */
public interface PayrollQueryFileRepository extends JpaRepository<PayrollQueryFile, Long> {

    List<PayrollQueryFile> findAllByQueryIdOrderByProducedAtDescIdDesc(Long queryId);
}

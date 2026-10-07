package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollDiscrepancy;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** A file's discrepancies -- a few dozen a file, so a file's are read whole; lists across files are paged elsewhere. */
public interface PayrollDiscrepancyRepository extends JpaRepository<PayrollDiscrepancy, Long> {

    List<PayrollDiscrepancy> findAllByFileId(Long fileId);

    List<PayrollDiscrepancy> findAllByFileIdAndStatusIn(Long fileId, Collection<PayrollDiscrepancyStatus> statuses);

    List<PayrollDiscrepancy> findAllByQueryIdOrderByTypeAscLineNumberAscIdAsc(Long queryId);

    Optional<PayrollDiscrepancy> findByFileIdAndTypeAndSubjectKey(Long fileId, PayrollDiscrepancyType type,
                                                                  String subjectKey);
}

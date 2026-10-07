package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** The payroll files. A file's bytes are read only where they are needed: the queries below select ids and states. */
public interface PayrollFileRepository extends JpaRepository<PayrollFile, Long> {

    /** The same bytes for the same employer and month: a second upload of them loads nothing (V74). */
    Optional<PayrollFile> findByPayrollEmployerIdAndPeriodAndSha256(Long payrollEmployerId, LocalDate period,
                                                                     String sha256);

    /** The live file of an employer for a month: one at most (V74's partial unique index). */
    Optional<PayrollFile> findFirstByPayrollEmployerIdAndPeriodAndStatus(Long payrollEmployerId, LocalDate period,
                                                                        PayrollFileStatus status);

    /** The files whose year is up (PAY-39), oldest first, a batch at a time. */
    @Query("SELECT f.id FROM PayrollFile f WHERE f.status <> za.co.titandynamix.sasbo_online_backend.enums"
            + ".PayrollFileStatus.PURGED AND f.retainUntil < :today ORDER BY f.retainUntil, f.id")
    List<Long> idsToPurge(@Param("today") LocalDate today, org.springframework.data.domain.Pageable batch);
}

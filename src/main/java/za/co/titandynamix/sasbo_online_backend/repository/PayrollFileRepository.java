package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.payroll.PayrollFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The payroll files. A file's bytes are read only where they are needed -- comparing it, and downloading it as it
 * arrived; every query below selects ids and states ({@link PayrollFileState}), never the content.
 */
public interface PayrollFileRepository extends JpaRepository<PayrollFile, Long> {

    /** The columns of a {@link PayrollFileState}: everything a status check needs, and not the bytes. */
    String STATE = "SELECT new za.co.titandynamix.sasbo_online_backend.repository.PayrollFileState(f.id, "
            + "f.payrollEmployerId, f.period, f.status, f.supersededBy, f.fileTotal, f.adminFeePercent, f.uploadedAt, "
            + "f.uploadedBy) FROM PayrollFile f";

    /** A file's state. */
    @Query(STATE + " WHERE f.id = :id")
    Optional<PayrollFileState> stateOf(@Param("id") Long id);

    /** The same bytes for the same employer and month, whatever became of them -- one row at most (V74). */
    @Query(STATE + " WHERE f.payrollEmployerId = :employer AND f.period = :period AND f.sha256 = :sha256")
    Optional<PayrollFileState> sameBytes(@Param("employer") Long payrollEmployerId, @Param("period") LocalDate period,
                                         @Param("sha256") String sha256);

    /** The live file of an employer for a month: one at most (V74's partial unique index). */
    @Query(STATE + " WHERE f.payrollEmployerId = :employer AND f.period = :period AND f.status = "
            + "za.co.titandynamix.sasbo_online_backend.enums.PayrollFileStatus.RECONCILED")
    Optional<PayrollFileState> liveStateOf(@Param("employer") Long payrollEmployerId, @Param("period") LocalDate period);

    /** The files whose year is up (PAY-39), oldest first, a batch at a time. */
    @Query("SELECT f.id FROM PayrollFile f WHERE f.status <> za.co.titandynamix.sasbo_online_backend.enums"
            + ".PayrollFileStatus.PURGED AND f.retainUntil < :today ORDER BY f.retainUntil, f.id")
    List<Long> idsToPurge(@Param("today") LocalDate today, org.springframework.data.domain.Pageable batch);
}

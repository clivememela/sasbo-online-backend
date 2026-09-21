package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AcbRunRepository extends JpaRepository<AcbRun, Long> {

    /** The latest run of a month in one state -- the declined run a new preparation replaces (SD3-H). */
    Optional<AcbRun> findFirstByActionMonthAndStatusOrderByIdDesc(LocalDate actionMonth, AcbRunStatus status);

    /**
     * The run holding {@code month}, if one does. The same predicate as the partial unique index,
     * so this and the database agree on what "already prepared" means.
     */
    @Query("SELECT r FROM AcbRun r WHERE r.actionMonth = :month AND r.status NOT IN :released")
    Optional<AcbRun> findLive(@Param("month") LocalDate month,
                              @Param("released") Collection<AcbRunStatus> released);

    /** The earliest action month of any run in {@code statuses}, or {@code null} when there is none. */
    @Query("SELECT min(r.actionMonth) FROM AcbRun r WHERE r.status IN :statuses")
    LocalDate firstMonthIn(@Param("statuses") Collection<AcbRunStatus> statuses);

    /** The runs on record, newest month first. */
    @Query(value = "SELECT r FROM AcbRun r ORDER BY r.actionMonth DESC, r.id DESC",
            countQuery = "SELECT count(r) FROM AcbRun r")
    Page<AcbRun> findPage(Pageable page);

    /** Runs whose preparation job has gone quiet -- the sweep's candidates. */
    List<AcbRun> findByStatusAndHeartbeatAtBefore(AcbRunStatus status, Instant cutoff);

    /**
     * The preparation job's pulse. A bulk update on purpose: it must not bump the run's version,
     * because a heartbeat is not a change anybody else could have raced with.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AcbRun r SET r.heartbeatAt = :at WHERE r.id = :id AND r.status = :status")
    int touchHeartbeat(@Param("id") Long id, @Param("status") AcbRunStatus status,
                       @Param("at") Instant at);
}

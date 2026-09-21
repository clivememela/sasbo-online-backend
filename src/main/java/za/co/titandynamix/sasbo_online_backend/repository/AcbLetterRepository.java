package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;

import java.util.List;
import java.util.Optional;

public interface AcbLetterRepository extends JpaRepository<AcbLetter, Long> {

    Optional<AcbLetter> findByLineId(Long lineId);

    /** The letters of a page of lines -- one query. */
    List<AcbLetter> findByLineIdIn(java.util.Collection<Long> lineIds);

    /** {@code [status, count]} for the letters of one run's unpaids. */
    @Query("SELECT l.status, count(l) FROM AcbLetter l, AcbRunLine rl WHERE l.lineId = rl.id AND rl.runId = :runId "
            + "GROUP BY l.status")
    List<Object[]> countByStatusForRun(@Param("runId") Long runId);
}

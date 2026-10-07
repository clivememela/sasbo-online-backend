package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;

import java.util.List;
import java.util.Optional;

public interface AcbLetterRepository extends JpaRepository<AcbLetter, Long> {

    /**
     * The first letter queued for a line -- its unpaid's. Since V71 a line can also carry the ending letter (DOD-24),
     * queued after it, so "the" letter of a line is the first one, never a lookup that fails on finding two.
     */
    default Optional<AcbLetter> findByLineId(Long lineId) {
        return findFirstByLineIdOrderByIdAsc(lineId);
    }

    Optional<AcbLetter> findFirstByLineIdOrderByIdAsc(Long lineId);

    /** A line's letter of one template: V71 allows one of each (UNIQUE (line_id, template)). */
    Optional<AcbLetter> findByLineIdAndTemplate(Long lineId,
                                                za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate template);

    /** The letters in one state for one channel -- the sender reads the RENDERED emails (03.3). */
    List<AcbLetter> findByStatusAndChannel(za.co.titandynamix.sasbo_online_backend.enums.LetterStatus status,
                                           za.co.titandynamix.sasbo_online_backend.enums.LetterChannel channel);

    /** The letters of a page of lines -- one query. */
    List<AcbLetter> findByLineIdIn(java.util.Collection<Long> lineIds);

    /** {@code [status, count]} for the letters of one run's unpaids. */
    @Query("SELECT l.status, count(l) FROM AcbLetter l, AcbRunLine rl WHERE l.lineId = rl.id AND rl.runId = :runId "
            + "GROUP BY l.status")
    List<Object[]> countByStatusForRun(@Param("runId") Long runId);
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunExclusion;

import java.util.List;

public interface AcbRunExclusionRepository extends JpaRepository<AcbRunExclusion, Long> {

    /**
     * A run's excluded members, a page at a time, with who they are -- read from the member NOW,
     * because this is a report of what to fix, and the fix is made on the member record.
     */
    @Query(value = "SELECT new za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto("
            + "e.memberId, m.membershipNumber, m.surname, m.initials, e.reason, e.detail) "
            + "FROM AcbRunExclusion e, Member m "
            + "WHERE e.memberId = m.id AND e.runId = :runId "
            + "ORDER BY e.reason, e.memberId",
            countQuery = "SELECT count(e) FROM AcbRunExclusion e WHERE e.runId = :runId")
    Page<AcbExclusionDto> findPage(@Param("runId") Long runId, Pageable page);

    /** How many members each listed reason excluded: {@code [reason, count]} rows. */
    @Query("SELECT e.reason, count(e) FROM AcbRunExclusion e WHERE e.runId = :runId GROUP BY e.reason")
    List<Object[]> countByReason(@Param("runId") Long runId);
}

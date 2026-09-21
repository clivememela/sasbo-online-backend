package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRunExclusionCount;

import java.util.List;

public interface AcbRunExclusionCountRepository extends JpaRepository<AcbRunExclusionCount, Long> {

    List<AcbRunExclusionCount> findByRunIdOrderByReasonAscDetailAsc(Long runId);
}

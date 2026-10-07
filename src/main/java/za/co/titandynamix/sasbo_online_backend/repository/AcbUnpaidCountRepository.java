package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCount;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The unpaid counts captured by hand (DOD-28, V72): one row per member. */
public interface AcbUnpaidCountRepository extends JpaRepository<AcbUnpaidCount, Long> {

    Optional<AcbUnpaidCount> findByMemberId(Integer memberId);

    /** The counts captured since {@code from} -- the unpaid count's window, like the report's (SD3-K). */
    List<AcbUnpaidCount> findByCountedOnGreaterThanEqual(LocalDate from);

    /** The rows of a list of members -- one query, for a list being captured. */
    List<AcbUnpaidCount> findByMemberIdIn(Collection<Integer> memberIds);

    /** A page of the counts on record, by membership number. */
    Page<AcbUnpaidCount> findAllByOrderByMembershipNumberAscIdAsc(Pageable page);
}

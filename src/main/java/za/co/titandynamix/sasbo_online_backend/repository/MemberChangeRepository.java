package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberChange;

/**
 * A member's history, one row a field a save changed (month end 03.5). Only ever APPENDED to: the table refuses an
 * update or a delete of a row (MD46). It is read, merged with the member's status changes, by
 * {@code MemberHistoryRepository}'s one statement.
 */
public interface MemberChangeRepository extends JpaRepository<MemberChange, Long> {
}

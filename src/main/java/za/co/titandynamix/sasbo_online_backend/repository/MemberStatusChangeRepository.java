package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.member.MemberStatusChange;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason;

import java.util.List;

/** The status changes {@code MemberService.changeStatus} made (ACB {@code 03.5}, V50). */
public interface MemberStatusChangeRepository extends JpaRepository<MemberStatusChange, Long> {

    /** A member's changes, oldest first. */
    List<MemberStatusChange> findByMemberIdOrderByChangedAtAscIdAsc(Integer memberId);

    /** Q25: how many members a reason moved -- "exactly how many members we cancelled on debit orders". */
    long countByReason(MemberStatusReason reason);
}

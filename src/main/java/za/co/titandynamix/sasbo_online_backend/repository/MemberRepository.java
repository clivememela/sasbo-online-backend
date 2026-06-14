package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.Member;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    boolean existsByMembershipNumber(String membershipNumber);
}

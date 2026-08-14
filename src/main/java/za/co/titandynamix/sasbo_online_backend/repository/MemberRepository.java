package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.co.titandynamix.sasbo_online_backend.entity.Area;
import za.co.titandynamix.sasbo_online_backend.entity.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    boolean existsByMembershipNumber(String membershipNumber);
    
    // Filtering methods
    List<Member> findByArea(Area area);
    List<Member> findByAreaId(Integer areaId);
    List<Member> findByRecruitmentRepresentative(Representative representative);
    List<Member> findByRecruitmentRepresentativeId(Integer representativeId);
}

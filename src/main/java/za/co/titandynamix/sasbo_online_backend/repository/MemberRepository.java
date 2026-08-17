package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    boolean existsByMembershipNumber(String membershipNumber);

    @Override
    @EntityGraph(attributePaths = {"recruitmentRepresentative", "structure", "role", "area", "user"})
    List<Member> findAll();

    @Override
    @EntityGraph(attributePaths = {"recruitmentRepresentative", "structure", "role", "area", "user"})
    Optional<Member> findById(Integer id);
    
    // Filtering methods
    List<Member> findByArea(Area area);
    List<Member> findByAreaId(Integer areaId);
    List<Member> findByRecruitmentRepresentative(Representative representative);
    List<Member> findByRecruitmentRepresentativeId(Integer representativeId);
    // Business Rule: Fetch only members who are designated as representatives
    // Pull down only members marked as active Representatives to populate recruiters list
    List<Member> findByIsRepresentativeTrue();

    // Alternate lookup utility to double check active status configurations
    @Query("SELECT m FROM Member m WHERE m.isRepresentative = true AND m.status = 'ACTIVE'")
    List<Member> findAllActiveRepresentatives();
}

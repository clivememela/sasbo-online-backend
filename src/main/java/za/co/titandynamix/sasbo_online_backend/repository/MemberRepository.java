package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Integer> {
    boolean existsByMembershipNumber(String membershipNumber);

    /**
     * The next membership number, from the sequence V22 creates.
     *
     * <p>🔴 NOT {@code existsByMembershipNumber}. The obvious implementation --
     * find the highest, add one, check it is free -- is a check-then-act race: two
     * captures at the same moment both read 100160, both find 100161 free, and both
     * take it. The window is small and the damage is permanent, because there is no
     * DELETE endpoint (register D9), so a duplicate has to be edited into something
     * else by hand, forever.
     *
     * <p>{@code nextval} is atomic by construction: it never hands the same value to
     * two callers, with no lock, no retry and no window. That is the whole reason
     * this is a sequence and not a query.
     */
    @Query(value = "SELECT nextval('membership_number_seq')", nativeQuery = true)
    long nextMembershipNumber();

    @Override
    @EntityGraph(attributePaths = {"recruitmentRepresentative", "recruiter", "structure", "role", "area", "recruitedArea", "user", "recruitmentChannel", "jobGrade", "workplace", "becCommittee"})
    List<Member> findAll();

    @Override
    @EntityGraph(attributePaths = {"recruitmentRepresentative", "recruiter", "structure", "role", "area", "recruitedArea", "user", "recruitmentChannel", "jobGrade", "workplace", "becCommittee"})
    Optional<Member> findById(Integer id);
    
    // Filtering methods
    List<Member> findByArea(Area area);
    List<Member> findByAreaId(Integer areaId);
    List<Member> findByRecruitmentRepresentative(Representative representative);
    List<Member> findByRecruitmentRepresentativeId(Integer representativeId);
    // Business Rule: Fetch only members who are designated as representatives
    // Pull down only members marked as active Representatives to populate recruiters list
    @EntityGraph(attributePaths = {"recruitmentRepresentative", "recruiter", "structure", "role", "area", "recruitedArea", "user", "recruitmentChannel", "jobGrade", "workplace", "becCommittee"})
    List<Member> findByIsRepresentativeTrue();

    // Alternate lookup utility to double check active status configurations
    @Query("SELECT m FROM Member m WHERE m.isRepresentative = true AND m.status = 'ACTIVE'")
    List<Member> findAllActiveRepresentatives();

    /**
     * A member a capture would duplicate (October build, Stephne MEN-19): enough to name the record, never the person.
     * The aliases are one word each, as both databases fold an unquoted alias's case.
     */
    interface CapturedAlready {
        Integer getId();

        String getNumber();

        String getStatus();
    }

    /** MEN-19: the member holding this identity number -- the same person, whatever their status (V26's index). */
    @Query(value = "SELECT m.id AS id, m.membership_number AS number, m.status AS status FROM members m "
            + "WHERE m.identity_number = :identity ORDER BY m.id LIMIT 1", nativeQuery = true)
    Optional<CapturedAlready> findCapturedByIdentityNumber(@Param("identity")
                                                           String identity);

    /**
     * MEN-19: a member with no identity number holding this passport number, case and spaces aside -- the predicate is
     * V79's {@code members_passport_unique}, word for word, so the index answers it.
     */
    @Query(value = "SELECT m.id AS id, m.membership_number AS number, m.status AS status FROM members m "
            + "WHERE upper(trim(m.passport_number)) = upper(trim(:passport)) "
            + "AND (m.identity_number IS NULL OR m.identity_number = '') "
            + "AND m.passport_number IS NOT NULL AND trim(m.passport_number) <> '' ORDER BY m.id LIMIT 1",
            nativeQuery = true)
    Optional<CapturedAlready> findCapturedByPassportNumber(@Param("passport")
                                                           String passport);

    /**
     * MEN-19: a member with no identity number holding this employee number at the same "Bank Where Employed", case
     * and spaces aside -- V79's {@code members_employee_at_employer_unique}, word for word.
     */
    @Query(value = "SELECT m.id AS id, m.membership_number AS number, m.status AS status FROM members m "
            + "WHERE lower(trim(m.bank_name)) = lower(trim(:employer)) "
            + "AND upper(trim(m.employee_number)) = upper(trim(:employee)) "
            + "AND (m.identity_number IS NULL OR m.identity_number = '') "
            + "AND m.employee_number IS NOT NULL AND trim(m.employee_number) <> '' "
            + "AND m.bank_name IS NOT NULL AND trim(m.bank_name) <> '' ORDER BY m.id LIMIT 1", nativeQuery = true)
    Optional<CapturedAlready> findCapturedByEmployeeNumber(
            @Param("employer") String employer,
            @Param("employee") String employee);

    /**
     * Month end 03.1: the member, LOCKED for a status change until the transaction ends -- so that of two changes
     * asked at once on the same view, the second waits, finds the status the first wrote, and is told its view is
     * stale (MV-5), rather than both writing. {@code SELECT ... FOR UPDATE}, which both databases accept (BR65).
     */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForStatusChange(@org.springframework.data.repository.query.Param("id") Integer id);
}

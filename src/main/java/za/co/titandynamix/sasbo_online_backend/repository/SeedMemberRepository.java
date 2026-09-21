package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;

import java.util.Collection;
import java.util.List;

/**
 * The seeder's two questions about members, each ONE query over a set of membership numbers.
 *
 * <p>Its own repository, read-only, so the member repository -- the capture arc's -- is untouched.
 * One query per question rather than an {@code exists} per member: once seeded, every start of every
 * environment asks these, for ever (SD4-F).
 */
public interface SeedMemberRepository extends Repository<Member, Integer> {

    /** Which of {@code numbers} are already on record. */
    @Query("SELECT m.membershipNumber FROM Member m WHERE m.membershipNumber IN :numbers")
    List<String> findMembershipNumbersIn(@Param("numbers") Collection<String> numbers);

    /** How many members on record carry one of {@code numbers}. */
    @Query("SELECT count(m) FROM Member m WHERE m.membershipNumber IN :numbers")
    long countMembershipNumbersIn(@Param("numbers") Collection<String> numbers);
}

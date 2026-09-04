package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.fixtures.LocationFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractPostgresIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The nine declared methods on {@code MemberRepository}, including the one
 * hand-written {@code @Query}.
 *
 * <p>This is the highest-risk repository in the application: it holds the
 * capture path, and two of its methods filter on a flag and a status that
 * nothing else checks.
 */
// READ_WRITE: resets the database and writes rows.
@ResourceLock(value = DatabaseReset.SHARED_DATABASE, mode = ResourceAccessMode.READ_WRITE)
@DisplayName("MemberRepository against the real migration chain")
class MemberRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        DatabaseReset.reset(this.jdbc);
    }

    @Test
    @DisplayName("existsByMembershipNumber distinguishes a taken number from a free one")
    void existsByMembershipNumber() {
        Member member = MemberFixture.aMember();
        member.setMembershipNumber("M0001");
        this.memberRepository.save(member);

        assertThat(this.memberRepository.existsByMembershipNumber("M0001")).isTrue();
        assertThat(this.memberRepository.existsByMembershipNumber("M9999")).isFalse();
    }

    @Test
    @DisplayName("existsByMembershipNumber is case sensitive")
    void existsByMembershipNumberIsCaseSensitive() {
        // Not IgnoreCase, unlike most lookups here. Pinned because a caller
        // moving from findByNameIgnoreCase will assume otherwise, and a
        // duplicate membership number is a real data problem.
        Member member = MemberFixture.aMember();
        member.setMembershipNumber("M0001");
        this.memberRepository.save(member);

        assertThat(this.memberRepository.existsByMembershipNumber("m0001")).isFalse();
    }

    @Test
    @DisplayName("findByAreaId returns only that area's members")
    void findByAreaId() {
        // The negative half is the assertion that matters: a derived query with
        // the filter dropped returns everything, and a fixture with one area
        // cannot tell the difference.
        Area northern = this.areaRepository.save(LocationFixture.area("Northern Gauteng"));
        Area western = this.areaRepository.save(LocationFixture.area("Western Cape"));

        this.memberRepository.save(inArea(MemberFixture.withSurname("Khumalo"), northern));
        this.memberRepository.save(inArea(MemberFixture.withSurname("Naidoo"), northern));
        this.memberRepository.save(inArea(MemberFixture.withSurname("Botha"), western));

        assertThat(this.memberRepository.findByAreaId(northern.getId()))
                .extracting(Member::getSurname)
                .containsExactlyInAnyOrder("Khumalo", "Naidoo");
    }

    @Test
    @DisplayName("findByArea and findByAreaId agree")
    void findByAreaAgreesWithFindByAreaId() {
        // Two methods, one question. They can drift, and nothing else would
        // notice: each is used from a different caller.
        Area area = this.areaRepository.save(LocationFixture.area("Northern Gauteng"));
        this.memberRepository.save(inArea(MemberFixture.aMember(), area));

        assertThat(this.memberRepository.findByArea(area))
                .hasSameSizeAs(this.memberRepository.findByAreaId(area.getId()));
    }

    @Test
    @DisplayName("findByIsRepresentativeTrue returns only members carrying the flag")
    void findByIsRepresentativeTrue() {
        this.memberRepository.save(
                MemberFixture.representativeMember("Khumalo", "ACTIVE"));
        this.memberRepository.save(MemberFixture.withSurname("Botha"));

        assertThat(this.memberRepository.findByIsRepresentativeTrue())
                .extracting(Member::getSurname)
                .containsExactly("Khumalo");
    }

    @Test
    @DisplayName("findAllActiveRepresentatives applies BOTH halves of its query")
    void findAllActiveRepresentativesFiltersOnFlagAndStatus() {
        // The hand-written @Query, and the only place its two conditions are
        // checked. Three rows, each failing a different half, is what
        // distinguishes "the query works" from "the query returns rows".
        this.memberRepository.save(
                MemberFixture.representativeMember("Khumalo", "ACTIVE"));
        this.memberRepository.save(
                MemberFixture.representativeMember("Naidoo", "RESIGNED"));

        Member activeNonRep = MemberFixture.withSurname("Botha");
        activeNonRep.setStatus("ACTIVE");
        this.memberRepository.save(activeNonRep);

        assertThat(this.memberRepository.findAllActiveRepresentatives())
                .extracting(Member::getSurname)
                .containsExactly("Khumalo");
    }

    @Test
    @DisplayName("findAllActiveRepresentatives matches the status exactly, not case-insensitively")
    void activeStatusIsMatchedExactly() {
        // The query compares to the literal 'ACTIVE'. A row stored as 'active'
        // is invisible to it -- which is a real hazard, because status is a
        // free-text String column with no constraint behind it.
        this.memberRepository.save(
                MemberFixture.representativeMember("Khumalo", "active"));

        assertThat(this.memberRepository.findAllActiveRepresentatives()).isEmpty();
    }

    @Test
    @DisplayName("orders members by surname in this container's collation")
    void ordersBySurname() {
        // Asserted here rather than in the fast lane: H2 in PostgreSQL mode does
        // not reproduce PostgreSQL collation, so the same assertion there would
        // pass under any ordering.
        //
        // Every surname is collation-neutral, so a failure can only be the
        // ORDER BY clause.
        for (String surname : List.of("Naidoo", "Abrahams", "Khumalo", "Botha")) {
            this.memberRepository.save(MemberFixture.withSurname(surname));
        }

        assertThat(this.memberRepository.findAll(Sort.by(Sort.Direction.ASC, "surname")))
                .extracting(Member::getSurname)
                .containsExactly(MemberFixture.COLLATION_NEUTRAL_SURNAMES);
    }

    @Test
    @DisplayName("findByRecruitmentRepresentativeId returns only that recruiter's members")
    void findByRecruitmentRepresentativeId() {
        Member recruiterMember = this.memberRepository.save(
                MemberFixture.representativeMember("Khumalo", "ACTIVE"));
        Representative recruiter = this.representativeOf(recruiterMember);

        Member recruited = MemberFixture.withSurname("Botha");
        recruited.setRecruitmentRepresentative(recruiter);
        this.memberRepository.save(recruited);
        this.memberRepository.save(MemberFixture.withSurname("Naidoo"));

        assertThat(this.memberRepository.findByRecruitmentRepresentativeId(recruiter.getId()))
                .extracting(Member::getSurname)
                .containsExactly("Botha");
        assertThat(this.memberRepository.findByRecruitmentRepresentative(recruiter))
                .hasSize(1);
    }

    @Test
    @DisplayName("starts from the migration chain's own state, not a sibling's leftovers")
    void startsFromTheMigrationSeed() {
        assertThat(this.memberRepository.count()).isZero();
    }

    private static Member inArea(Member member, Area area) {
        member.setArea(area);
        return member;
    }

    @Autowired
    private RepresentativeRepository representativeRepository;

    private Representative representativeOf(Member member) {
        return this.representativeRepository.save(
                za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture
                        .representative("Thandi", member.getSurname(), member));
    }
}

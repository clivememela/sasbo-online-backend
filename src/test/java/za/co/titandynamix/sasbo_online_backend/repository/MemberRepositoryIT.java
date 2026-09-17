package za.co.titandynamix.sasbo_online_backend.repository;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import za.co.titandynamix.sasbo_online_backend.entity.location.Area;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.fixtures.LocationFixture;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractWritingIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.QueryCount;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The nine declared methods on {@code MemberRepository}, including the one
 * hand-written {@code @Query}.
 *
 * <p>This is the highest-risk repository in the application: it holds the
 * capture path, and two of its methods filter on a flag and a status that
 * nothing else checks.
 */
@DisplayName("MemberRepository against the real migration chain")
class MemberRepositoryIT extends AbstractWritingIT {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AreaRepository areaRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

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

    @Test
    @DisplayName("COSATU's four columns survive a write and a read back")
    void cosatuFieldsRoundTrip() {
        // V19 added four columns and nothing asserted a value written to them
        // is the value read back.
        //
        // 🔑 WHY MigrationSchemaValidationTest DOES NOT COVER THIS. That test
        // runs `ddl-auto: validate`, which proves the columns EXIST and that
        // their types match what the entity declares. It cannot prove a
        // mapping is right: a field mapped to the wrong column, or two fields
        // mapped to one, passes validation completely and loses data at run
        // time. The only assertion that distinguishes those is a value going in
        // and the same value coming out.
        //
        // Distinct values per field, deliberately. Four fields all set to "Y"
        // would pass even if the mapping crossed two of them over -- which is
        // the mistake actually available here, because `cosatuLocal` and
        // `cosatuRegion` are adjacent, same-typed, and named alike.
        Member member = MemberFixture.aMember();
        member.setMembershipNumber("M-COSATU-1");
        member.setIsCosatu("Y");
        member.setCosatuLocal("Johannesburg Central");
        member.setCosatuRegion("Gauteng");
        member.setCosatuPositions("PEC,CEC");

        Integer id = this.memberRepository.save(member).getId();

        // 🔑 THE READ IS GENUINELY A READ, and it is worth knowing why rather
        // than assuming it. These tests carry no @Transactional -- deliberately,
        // because AbstractWritingIT resets with TRUNCATE on both sides instead
        // of relying on rollback. So `save` commits and `findById` runs in its
        // own transaction with its own persistence context: the fields below
        // come from the database, not from an identity map.
        //
        // ⚠️ Under @Transactional this test would be nearly worthless -- the
        // same managed instance would come back and it would assert that the
        // setters work. If a future change makes these tests transactional, this
        // one needs an explicit flush-and-clear to keep meaning anything.
        Member reread = this.memberRepository.findById(id).orElseThrow();

        assertThat(reread.getIsCosatu()).isEqualTo("Y");
        assertThat(reread.getCosatuLocal()).isEqualTo("Johannesburg Central");
        assertThat(reread.getCosatuRegion()).isEqualTo("Gauteng");
        assertThat(reread.getCosatuPositions())
                .as("the positions column is a comma-separated VARCHAR(500), written by "
                        + "StringArrayToCommaSeparatedDeserializer from the browser's array. "
                        + "It must survive storage as the single string it was joined into")
                .isEqualTo("PEC,CEC");
    }

    @Test
    @DisplayName("a member who is not in COSATU stores nulls, not empty strings")
    void aNonCosatuMemberStoresNulls() {
        // The common case, and the one whose breakage would be widest. The
        // client's detail view branches on `cosatuPositions?.length > 0`, which
        // treats null and "" identically -- so the two are indistinguishable
        // from the browser and a change between them would be invisible there.
        // This is the layer that can tell them apart, which makes it the layer
        // that should say which one the database holds.
        Member member = MemberFixture.aMember();
        member.setMembershipNumber("M-COSATU-2");

        Integer id = this.memberRepository.save(member).getId();
        Member reread = this.memberRepository.findById(id).orElseThrow();

        assertThat(reread.getCosatuLocal()).isNull();
        assertThat(reread.getCosatuRegion()).isNull();
        assertThat(reread.getCosatuPositions()).isNull();
    }

    @Test
    @DisplayName("listing members costs the same number of queries at 5 rows and at 50")
    void listingDoesNotGrowWithTheData() {
        // The member listing is the one that will hurt: Member holds five
        // @ManyToOne relations and a @OneToOne, all lazy, and GET /api/members
        // returns every row with every field. If any of them is resolved during
        // mapping, the cost grows with the register.
        //
        // Measured at two sizes, because one measurement cannot tell a constant
        // cost from a growing one.
        QueryCount.assertConstantAcrossSizes(
                this.entityManagerFactory,
                this::seedTo,
                () -> this.memberRepository.findAll().forEach(Member::getSurname),
                5, 50);
    }

    /**
     * 🔴 A48. Production on 2026-09-16 held every one of its 160 members TWICE -- ids 1-160
     * and 161-320, the same identity and membership numbers -- after the database was
     * rebuilt from empty and the seeder ran twice.
     *
     * <p>HALF FIXED: V26's partial unique index refuses a repeated identity number, which
     * {@code MemberIdentityUniquenessIT} asserts. What is still open is the membership
     * number, so this pair now covers that half only.
     *
     * <p>Asserted here and not in the fast lane for the reason {@code BankRepositoryIT}
     * gives: H2's schema is one Hibernate invents, so only the migration chain can say
     * whether the database refuses a duplicate.
     */
    @Test
    @Disabled(KnownDefects.DUPLICATE_MEMBERS_ARE_ACCEPTED)
    @DisplayName("🔴 A48: refuses a second member with a membership number already on record")
    void membershipNumbersAreUnique() {
        Member first = MemberFixture.withSurname("Mokoena");
        first.setMembershipNumber("M-100002");
        first.setIdentityNumber("8001015009087");
        this.memberRepository.saveAndFlush(first);

        Member sameMembershipNumber = MemberFixture.withSurname("Mokoena");
        sameMembershipNumber.setMembershipNumber("M-100002");
        sameMembershipNumber.setIdentityNumber("9001015009086");
        assertThatThrownBy(() -> this.memberRepository.saveAndFlush(sameMembershipNumber))
                .as("a membership number identifies one member")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("🔴 A48 still reproduces: two members can share a membership number")
    void aMembershipNumberCanStillBeStoredTwice() {
        // Different identity numbers, or V26 refuses the second save for the other reason
        // and this pin would stop observing the half that is still open.
        String[] identities = {"8001015009087", "9001015009086"};
        for (String identity : identities) {
            Member member = MemberFixture.withSurname("Mokoena");
            member.setMembershipNumber("M-100002");
            member.setIdentityNumber(identity);
            this.memberRepository.saveAndFlush(member);
        }

        assertThat(this.jdbc.queryForObject(
                "SELECT count(*) FROM members WHERE membership_number = 'M-100002'",
                Integer.class))
                .as("A48 has changed. If the second save was refused, the membership-number half is "
                        + "fixed: enable membershipNumbersAreUnique and delete the KnownDefects row")
                .isEqualTo(2);
    }

    /** Rebuilds the table with exactly {@code rows} members. */
    private void seedTo(int rows) {
        DatabaseReset.reset(this.jdbc);
        for (int i = 1; i <= rows; i++) {
            this.memberRepository.save(MemberFixture.withSurname("Member" + i));
        }
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

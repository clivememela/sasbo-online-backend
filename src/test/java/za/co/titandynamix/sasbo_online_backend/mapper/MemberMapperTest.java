package za.co.titandynamix.sasbo_online_backend.mapper;

import java.math.BigDecimal;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code MemberDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("MemberMapper")
class MemberMapperTest extends AbstractMapperContractTest<Member, MemberDto> {

    private static final MemberMapper MAPPER = new MemberMapperImpl();

    MemberMapperTest() {
        super(Member.class, MemberDto.class, MAPPER::toDto, 40,
                MapperContract.expecting()
                        // Every explicit @Mapping on MemberMapper, transcribed.
                        // Not duplication: several of these targets ALSO exist as
                        // plain fields on the entity (roleId, structureId, areaId),
                        // which the client fills on write and the mapper overwrites
                        // from the relation on read. Without saying which one the
                        // mapper claims, this guard cannot tell a correct mapping
                        // from a crossed one -- and it caught exactly that.
                        .sourcePath("jobGrade", "jobGrade.grade")
                        .sourcePath("structure", "structure.name")
                        .sourcePath("structureId", "structure.id")
                        .sourcePath("roleId", "role.id")
                        .sourcePath("roleName", "role.name")
                        .sourcePath("areaId", "area.id")
                        .sourcePath("areaName", "area.name")
                        // 03.3 / 03.8 -- the Recruitment Channel tab reads back the three
                        // things it wrote.
                        //
                        // 🔴 `areaRecruited` reads `recruitedArea`, NOT `area`. It used to
                        // share a source with `areaName`, and this declaration is where that
                        // was visible: two DTO fields claiming one relation. They are two
                        // different facts -- where a member belongs, and where they joined --
                        // and V24 gave the second its own column.
                        .sourcePath("areaRecruited", "recruitedArea.name")
                        .sourcePath("recruitedHow", "recruitmentChannel.name")
                        .sourcePath("userId", "user.id")
                        // V55 -- the committee a BEC member sits on, by SASBO's code and name. The
                        // entity's transient `becCommitteeCode` shares the code's name; this is the
                        // declaration that says the STORED committee wins.
                        .sourcePath("becCommitteeCode", "becCommittee.code")
                        .sourcePath("becCommitteeName", "becCommittee.name")
                        .sourcePath("recruitmentRepresentativeId",
                                "recruitmentRepresentative.id")
                        .computed("recruitmentRepresentativeName",
                                "mapRepToName joins the representative's names")
                        .computed("areasRep", "the same mapRepToName, under a second name")
                        // Two real gaps, found by this guard on its first run.
                        // MemberMapper is declared unmappedTargetPolicy = IGNORE,
                        // so MapStruct never warned about either -- on a 61-field
                        // DTO where a null field is invisible to every other test,
                        // because the endpoint still answers 200.
                        .unmapped("jobGradeId", "Member.jobGrade.id exists and is never mapped, "
                                + "so every member response carries jobGradeId: null")
                        .unmapped("isRepresentative", "the entity's Boolean and the DTO's "
                                + "primitive get different Lombok property names, so MapStruct "
                                + "matches neither -- see MEMBER_IS_REPRESENTATIVE_NEVER_MAPPED")
                        .unmapped("jobGradeName", "JobGrade has no 'name' -- the field is "
                                + "'grade', already mapped to MemberDto.jobGrade. jobGradeName "
                                + "appears to be a duplicate nobody removed, and it is always "
                                + "null")
                        // Not a gap, and held null ON PURPOSE: no member record says anything
                        // about a letter. MemberController sets it on the one answer that asked
                        // for one -- a capture with sendWelcome -- after the member is saved (A64).
                        .unmapped("welcomeLetter", "set by MemberController on a capture's answer, "
                                + "never read from the entity -- a member read back must not "
                                + "claim a letter went"));
    }

    @Test
    @Disabled(KnownDefects.MEMBER_IS_REPRESENTATIVE_NEVER_MAPPED)
    @DisplayName("a member who is a representative is reported as one")
    void isRepresentativeArrives() {
        // The gap above, named on its own so that fixing it produces a green
        // test rather than a quietly-removed line in a declaration list. This
        // one is worth naming because it does not read as missing data: the
        // primitive default is `false`, which every consumer will treat as an
        // answer.
        Member member = new Member();
        member.setIsRepresentative(Boolean.TRUE);

        assertThat(MAPPER.toDto(member).isRepresentative()).isTrue();
    }

    // ═══ `updateFromMember` — THE PUT MERGE CONTRACT (H8 · BR2-6 · decision ⑨) ═══
    //
    // 🔴 BR2-6: NOTHING ON EITHER SIDE TESTED THIS METHOD AT ALL. The contract above
    // is a `MapperContract` over `toDto` only; Run 1 recorded that the mapper test
    // "asserts the regression", and Run 2 corrected it -- the test neither asserted
    // nor contradicted the ignores, because it never looked at the write path. The
    // method that decides what a PUT is allowed to change had no test.
    //
    // 🔑 ASSERTED BEHAVIOURALLY, NOT TRANSCRIBED. Listing the @Mapping(ignore)
    // annotations here would be a copy of the source that passes whenever it matches
    // itself -- the mirrored-rule shape this pass retired two tests for in Batch 0.
    // These call the mapper and read what it did.

    /** A persisted member with a value in every field this contract is about. */
    private static Member stored() {
        Member stored = new Member();
        stored.setSurname("Stored");
        stored.setPhone("0115550000");
        stored.setStatus("Active");
        stored.setMonthlySubscription(new BigDecimal("120.00"));
        stored.setCurrentBalance(new BigDecimal("50.00"));
        stored.setArrearsBalance(new BigDecimal("10.00"));
        stored.setLastMonthBalance(new BigDecimal("20.00"));
        stored.setJoinDate(java.time.LocalDate.of(2020, 1, 31));
        stored.setMembershipNumber("M-1");
        return stored;
    }

    @Test
    @DisplayName("🔒 H8 — money and provenance survive a PUT that tries to set them")
    void theMergeRefusesTheFieldsACallerMayNotSet() {
        Member target = stored();

        Member submitted = new Member();
        submitted.setMonthlySubscription(new BigDecimal("0.01"));
        submitted.setCurrentBalance(new BigDecimal("999999"));
        submitted.setArrearsBalance(new BigDecimal("888888"));
        submitted.setLastMonthBalance(new BigDecimal("777777"));
        submitted.setJoinDate(java.time.LocalDate.of(1999, 12, 31));
        submitted.setMembershipNumber("M-HIJACKED");
        submitted.setStatus("Inactive");
        submitted.setSurname("Submitted");

        MAPPER.updateFromMember(submitted, target);

        assertThat(target.getMonthlySubscription())
                .as("PATCH /subscription is the one door for the amount")
                .isEqualTo("120.00");
        assertThat(target.getCurrentBalance())
                .as("the Recruiters screen sends `currentBalance || 0` on every PUT, so a "
                        + "bulk assign would write 0 over an empty balance")
                .isEqualTo("50.00");
        assertThat(target.getArrearsBalance()).isEqualTo("10.00");
        assertThat(target.getLastMonthBalance()).isEqualTo("20.00");
        assertThat(target.getJoinDate())
                .as("when a member joined is a historical fact, not an editable field")
                .isEqualTo(java.time.LocalDate.of(2020, 1, 31));
        assertThat(target.getMembershipNumber())
                .as("server-allocated, and there is no DELETE on this API")
                .isEqualTo("M-1");

        // 🔑 The two non-vacuity halves. Without them every assertion above is
        // satisfied by a merge that copies NOTHING.
        assertThat(target.getStatus())
                .as("`status` is DELIBERATELY writable -- Edit Member edits it and the "
                        + "Recruiters screen sets Inactive through PUT (BR3-1)")
                .isEqualTo("Inactive");
        assertThat(target.getSurname())
                .as("and an ordinary field still merges")
                .isEqualTo("Submitted");
    }

    @Test
    @DisplayName("🗣️ ⑨ a field the request omits keeps its stored value; a blank one clears")
    void theMergeTreatsAbsentAndBlankDifferently() {
        Member target = stored();

        Member submitted = new Member();
        submitted.setSurname("Changed");   // sent
        // `phone` is NOT set -> absent -> must keep "0115550000"

        MAPPER.updateFromMember(submitted, target);

        assertThat(target.getPhone())
                .as("absent means keep -- this is the half that was backwards, and it is "
                        + "the opposite of what the resolvers do on the same request")
                .isEqualTo("0115550000");
        assertThat(target.getSurname())
                .as("a field that WAS sent still changes")
                .isEqualTo("Changed");

        Member clearing = new Member();
        clearing.setPhone("");             // sent, empty -> a deliberate clear

        MAPPER.updateFromMember(clearing, target);

        assertThat(target.getPhone())
                .as("blank means clear, so a user can still remove a value entered by "
                        + "mistake -- without this half, ⑨ would be satisfied by a mapper "
                        + "that has simply stopped copying the field")
                .isEmpty();
    }

}

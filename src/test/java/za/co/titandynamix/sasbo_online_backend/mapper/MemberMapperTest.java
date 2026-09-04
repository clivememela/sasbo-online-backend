package za.co.titandynamix.sasbo_online_backend.mapper;

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
class MemberMapperTest {

    private final MemberMapper mapper = new MemberMapperImpl();

    @Test
    @DisplayName("every field on MemberDto arrives from a fully populated Member")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                Member.class, MemberDto.class, this.mapper::toDto, 40,
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
                        .sourcePath("userId", "user.id")
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
                                + "null"));
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

        assertThat(this.mapper.toDto(member).isRepresentative()).isTrue();
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class MemberMapper {

    @Autowired
    protected za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRepository representativeRepository;

    @Autowired
    protected za.co.titandynamix.sasbo_online_backend.repository.UnionStructureRepository unionStructureRepository; // Ensure this repository exists!

    @Autowired
    protected za.co.titandynamix.sasbo_online_backend.repository.RepresentativeRoleRepository representativeRoleRepository;

    // ============================================================
    // ENTITY TO DTO
    // ============================================================
    // 🗣️ 03.10 -- the RECRUITER is what the capture form asked for and what it now
    // reads back. `recruitmentRepresentativeName` keeps its wire name deliberately:
    // it is the field `Members.jsx` already reads, and renaming an external contract
    // buys nothing here while breaking the screen. The SOURCE is what changed.
    @Mapping(target = "recruitmentRepresentativeId", source = "recruiter.id")
    @Mapping(target = "recruitmentRepresentativeName", source = "recruiter.name")
    // `areasRep` still reflects the REPRESENTATIVE link, which is a different fact
    // and a different column -- the register's edit screen owns it.
    @Mapping(target = "areasRep", source = "recruitmentRepresentative", qualifiedByName = "mapRepToName")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "roleName", source = "role.name")
    @Mapping(target = "structureId", source = "structure.id")
    @Mapping(target = "structure", source = "structure.name")
    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "areaName", source = "area.name")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "jobGrade", source = "jobGrade.grade") // Extracts the string field from the class
    // 02.5. Named explicitly rather than relying on the by-name default: the entity
    // carries an enum and the DTO a String, and this mapping is the ONLY thing that
    // puts the job-grade outcome on the wire. It was missing once already, and the
    // failure was silent in both directions -- IGNORE drops an unmapped target
    // without a word, and nothing on the server noticed because the service test
    // reads the entity.
    @Mapping(target = "jobGradeOutcome", source = "jobGradeOutcome")
    // 03.8 / 03.3 -- the Recruitment Channel tab reads back what it wrote. Named
    // explicitly for the same reason as the line above: an unmapped target is dropped
    // without a word, and this arc has already paid for that once.
    @Mapping(target = "recruitedHow", source = "recruitmentChannel.name")
    @Mapping(target = "areaRecruited", source = "recruitedArea.name")
    // Month end 01.3 -- the member's workplace: three scalars, from the one join the list's entity graph gains
    // (MR-PERF-9). Whether they are ANSWERED is the deployment's switch's (MD62), asked by MemberController, which
    // also sets the lineage -- on the answer for one member, and on no list.
    @Mapping(target = "workplaceId", source = "workplace.id")
    @Mapping(target = "workplaceName", source = "workplace.name")
    @Mapping(target = "workplaceRaNumber", source = "workplace.raNumber")
    @Mapping(target = "workplace", ignore = true)
    // V55 -- the committee a BEC member sits on. Named explicitly: the entity's transient
    // `becCommitteeCode` shares the DTO's field name, so by name MapStruct would copy the
    // REQUEST's code back out rather than the stored committee's.
    @Mapping(target = "becCommitteeCode", source = "becCommittee.code")
    @Mapping(target = "becCommitteeName", source = "becCommittee.name")
    public abstract MemberDto toDto(Member member);

    // ============================================================
    // DTO TO ENTITY
    // ============================================================
    @Mapping(target = "recruitmentRepresentative", source = "recruitmentRepresentativeName", qualifiedByName = "mapNameToRep")
    @Mapping(target = "recruiter", ignore = true)
    @Mapping(target = "structure", source = "structureId", qualifiedByName = "mapIdToStructure")
    @Mapping(target = "role", source = "roleId", qualifiedByName = "mapIdToRole")
    @Mapping(target = "jobGrade.grade", source = "jobGrade") // Sets the string into the new class instance
    // 01.3: never bound -- the transient workplaceId is what a save names, and the service resolves it.
    @Mapping(target = "workplace", ignore = true)
    public abstract Member toEntity(MemberDto memberDto);

    /**
     * Merges a submitted member onto the persisted one for {@code PUT}.
     * <p>
     * 🔒 The ignored properties are identity and provenance, not data: they are
     * either server-allocated or resolved from a transient id, and every one of
     * them is {@code READ_ONLY} on the entity — so they arrive {@code null} on
     * {@code source} and a blanket copy would ERASE the persisted value. A null
     * {@code id} is the sharp one: it turns the following {@code save()} into an
     * INSERT. {@code structure}, {@code role} and {@code recruitmentRepresentative}
     * are resolved onto the target by the service instead.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "membershipNumber", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "structure", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "recruitmentRepresentative", ignore = true)
    @Mapping(target = "recruiter", ignore = true)
    // Month end 01.3: resolved from the transient `workplaceId` by the service, as `structure` and `role` are.
    @Mapping(target = "workplace", ignore = true)
    // V55: resolved onto the target from `becCommitteeCode` by the service, like `structure`.
    @Mapping(target = "becCommittee", ignore = true)
    // ── H8: MONEY IS NOT THE CALLER'S TO SET, even when it sends it ────────────
    //
    // 🔴 These four were copied straight off the request body. `monthlySubscription`
    // was harmless only because `update()` immediately overwrote it from the bank —
    // which is the defect `R1-F4` removes, so removing it WITHOUT this would have
    // made a client-sent subscription authoritative. The balances need no such
    // accident: the Recruiters screen's builder sends
    // `currentBalance: member.currentBalance || 0` on every PUT, so a member whose
    // balance was empty is written as `0` by a bulk assign action.
    //
    // 🔑 `PATCH /subscription` stays the ONE door for the amount. And note what is
    // NOT here: `status` is deliberately writable — Edit Member edits it and the
    // Recruiters screen sets `Inactive` through PUT, so ignoring it would break two
    // live screens (BR3-1).
    @Mapping(target = "monthlySubscription", ignore = true)
    @Mapping(target = "currentBalance", ignore = true)
    @Mapping(target = "arrearsBalance", ignore = true)
    @Mapping(target = "lastMonthBalance", ignore = true)
    // Provenance, not data: when the member joined is a historical fact, and
    // `updatedAt` is stamped by the entity's own lifecycle callback.
    @Mapping(target = "joinDate", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // ── 🗣️ DECISION ⑨(b), owner 2026-09-10: ABSENT KEEPS, BLANK CLEARS ────────
    //
    // Without this the merge copies NULLS, so a field a PUT never mentioned is
    // ERASED. That was safe only by convention — every caller happened to send the
    // whole DTO — it was stated in no contract, and it was the exact opposite of the
    // `absent = keep` rule the resolvers apply on the same request
    // (`resolveByName`: null returns, "" clears). One request, two opposite readings
    // of a missing field.
    //
    // 🔑 What it buys: every future caller is safe by default. A dirty-fields form,
    // a partial update from a script, a screen that does not hold a field — none of
    // them can silently blank a column any more. A deliberate clear still works,
    // because it already needed `""` rather than omission. It closes the whole class
    // that `R7-F1` was one instance of, while decision ⑦ still fixes that caller.
    //
    // ⚠️ Measured, not assumed: none of the five client services enforced whole-DTO
    // PUTs (`sasbo-online-80`, 2026-09-10), and the design arc is rewriting the three
    // biggest screens now. `MemberPutSemanticsIT` asserts BOTH halves.
    @org.mapstruct.BeanMapping(
            nullValuePropertyMappingStrategy = org.mapstruct.NullValuePropertyMappingStrategy.IGNORE)
    public abstract void updateFromMember(Member source, @org.mapstruct.MappingTarget Member target);

    // Helper Method 1: Turn Representative into String Name
    @Named("mapRepToName")
    protected String mapRepToName(Representative representative) {
        if (representative == null) {
            return null;
        }
        String first = representative.getFirstNames() != null ? representative.getFirstNames() : "";
        String last = representative.getSurname() != null ? representative.getSurname() : "";
        return (first + " " + last).trim();
    }

    // Helper Method 2: Look up Representative by String Name
    @Named("mapNameToRep")
    protected Representative mapNameToRep(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        String[] parts = name.trim().split("\\s+", 2);
        String firstNames = parts[0];
        String surname = parts.length > 1 ? parts[1] : "";
        return representativeRepository.findByFirstNamesAndSurname(firstNames, surname)
                       .orElse(null);
    }

    // Helper Method 3: Look up UnionStructure Entity by ID (from dropdown selection)
    @Named("mapIdToStructure")
    protected UnionStructure mapIdToStructure(Integer structureId) {
        if (structureId == null) {
            return null;
        }
        return unionStructureRepository.findById(structureId).orElse(null);
    }

    // Helper Method 4: Look up RepresentativeRole Entity by ID (from dropdown selection)
    @Named("mapIdToRole")
    protected za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole mapIdToRole(Integer roleId) {
        if (roleId == null) {
            return null;
        }
        return representativeRoleRepository.findById(roleId).orElse(null);
    }

}

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
    @Mapping(target = "recruitmentRepresentativeId", source = "recruitmentRepresentative.id")
    @Mapping(target = "recruitmentRepresentativeName", source = "recruitmentRepresentative", qualifiedByName = "mapRepToName")
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
    public abstract MemberDto toDto(Member member);

    // ============================================================
    // DTO TO ENTITY
    // ============================================================
    @Mapping(target = "recruitmentRepresentative", source = "recruitmentRepresentativeName", qualifiedByName = "mapNameToRep")
    @Mapping(target = "structure", source = "structureId", qualifiedByName = "mapIdToStructure")
    @Mapping(target = "role", source = "roleId", qualifiedByName = "mapIdToRole")
    @Mapping(target = "jobGrade.grade", source = "jobGrade") // Sets the string into the new class instance
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

package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RepresentativeMapper {

    @Mapping(target = "name", source = "entity", qualifiedByName = "repFullName")
    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "structureId", source = "structure.id")
    @Mapping(target = "membersCount", source = "entity", qualifiedByName = "countMembers")
    RepresentativeDto toDto(Representative entity);

    Representative toEntity(RepresentativeDto dto);

    @Named("repFullName")
    default String repFullName(Representative entity) {
        if (entity == null) return null;
        String first = entity.getFirstNames() != null ? entity.getFirstNames() : "";
        String last = entity.getSurname() != null ? entity.getSurname() : "";
        String name = (first + " " + last).trim();
        return name.isEmpty() ? null : name;
    }

    @Named("countMembers")
    default Integer countMembers(Representative entity) {
        if (entity == null || entity.getMembers() == null) return 0;
        return entity.getMembers().size();
    }
}

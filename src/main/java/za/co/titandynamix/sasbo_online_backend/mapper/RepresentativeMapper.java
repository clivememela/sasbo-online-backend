package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

@Mapper(componentModel = "spring")
public interface RepresentativeMapper {

    @Mapping(target = "memberId", source = "member.id")
    @Mapping(target = "areaId", source = "area.id")
    @Mapping(target = "roleId", source = "role.id")
    @Mapping(target = "structureId", source = "structure.id")
    @Mapping(target = "membersCount", expression = "java(representative.getMembers() != null ? representative.getMembers().size() : 0)")
    RepresentativeDto toDto(Representative representative);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "member", ignore = true)          // set manually in service
    @Mapping(target = "area", ignore = true)            // set manually in service
    @Mapping(target = "role", ignore = true)            // set manually in service
    @Mapping(target = "structure", ignore = true)       // set manually in service
    @Mapping(target = "electionDate", ignore = true)    // set manually in service
    @Mapping(target = "members", ignore = true)         // read-only collection
    Representative toEntity(RepresentativeDto dto);
}

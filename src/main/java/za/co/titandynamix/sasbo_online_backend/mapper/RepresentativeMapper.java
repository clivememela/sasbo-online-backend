package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

@Mapper(componentModel = "spring")
public interface RepresentativeMapper {

    @Mapping(target = "memberId", source = "member.id")
    RepresentativeDto toDto(Representative representative);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "member", ignore = true)  // set manually in service
    @Mapping(target = "area", ignore = true)
    @Mapping(target = "repRole", ignore = true)
    @Mapping(target = "structure", ignore = true)
    @Mapping(target = "electionDate", ignore = true)
    Representative toEntity(RepresentativeDto dto);
}

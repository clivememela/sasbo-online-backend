package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;

@Mapper(componentModel = "spring")
public interface RepresentativeRoleMapper {
    RepresentativeRoleDto toDto(RepresentativeRole role);
    RepresentativeRole toEntity(RepresentativeRoleDto dto);
}

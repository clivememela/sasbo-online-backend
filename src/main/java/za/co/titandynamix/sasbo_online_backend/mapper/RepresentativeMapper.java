package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;

@Mapper(componentModel = "spring")
public interface RepresentativeMapper {
    RepresentativeDto toDto(Representative representative);

    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Representative toEntity(RepresentativeDto dto);
}

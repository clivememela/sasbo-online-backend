package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.SuburbDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;

@Mapper(componentModel = "spring", uses = {TownMapper.class})
public interface SuburbMapper {
    SuburbMapper INSTANCE = Mappers.getMapper(SuburbMapper.class);

    @Mapping(target = "townId", source = "town.id")
    @Mapping(target = "townName", source = "town.name")
    SuburbDto toDto(Suburb suburb);

    @Mapping(target = "town", ignore = true)
    Suburb toEntity(SuburbDto suburbDto);
}

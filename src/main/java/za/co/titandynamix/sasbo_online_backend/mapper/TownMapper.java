package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.TownDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;

@Mapper(componentModel = "spring", uses = {CityMapper.class})
public interface TownMapper {
    TownMapper INSTANCE = Mappers.getMapper(TownMapper.class);

    @Mapping(target = "cityId", source = "city.id")
    @Mapping(target = "cityName", source = "city.name")
    TownDto toDto(Town town);

    @Mapping(target = "city", ignore = true)
    @Mapping(target = "suburbs", ignore = true)
    Town toEntity(TownDto townDto);
}

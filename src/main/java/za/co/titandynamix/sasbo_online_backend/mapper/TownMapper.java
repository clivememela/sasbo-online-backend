package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.TownDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;

@Mapper(componentModel = "spring", uses = {CityMapper.class})
public interface TownMapper {
    TownMapper INSTANCE = Mappers.getMapper(TownMapper.class);

    TownDto toDto(Town town);
    Town toEntity(TownDto townDto);
}

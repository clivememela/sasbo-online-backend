package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.CityDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;

@Mapper(componentModel = "spring", uses = {ProvinceMapper.class})
public interface CityMapper {
    CityMapper INSTANCE = Mappers.getMapper(CityMapper.class);

    CityDto toDto(City city);
    City toEntity(CityDto cityDto);
}

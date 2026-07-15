package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.CityDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;

@Mapper(componentModel = "spring", uses = {ProvinceMapper.class})
public interface CityMapper {
    CityMapper INSTANCE = Mappers.getMapper(CityMapper.class);

    @Mapping(target = "provinceId", source = "province.id")
    @Mapping(target = "provinceName", source = "province.name")
    CityDto toDto(City city);

    @Mapping(target = "province", ignore = true)
    @Mapping(target = "towns", ignore = true)
    City toEntity(CityDto cityDto);
}

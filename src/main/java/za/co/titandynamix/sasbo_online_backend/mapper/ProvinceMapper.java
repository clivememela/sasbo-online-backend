package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;

@Mapper(componentModel = "spring")
public interface ProvinceMapper {
    ProvinceMapper INSTANCE = Mappers.getMapper(ProvinceMapper.class);

    ProvinceDto toDto(Province province);
    Province toEntity(ProvinceDto provinceDto);
}

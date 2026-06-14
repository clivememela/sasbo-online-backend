package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import za.co.titandynamix.sasbo_online_backend.dto.location.SuburbDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;

@Mapper(componentModel = "spring", uses = {TownMapper.class})
public interface SuburbMapper {
    SuburbMapper INSTANCE = Mappers.getMapper(SuburbMapper.class);

    SuburbDto toDto(Suburb suburb);
    Suburb toEntity(SuburbDto suburbDto);
}

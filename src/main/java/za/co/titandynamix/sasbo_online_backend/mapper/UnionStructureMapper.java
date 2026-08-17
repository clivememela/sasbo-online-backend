package za.co.titandynamix.sasbo_online_backend.mapper;

import org.mapstruct.Mapper;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;

@Mapper(componentModel = "spring")
public interface UnionStructureMapper {
    UnionStructureDto toDto(UnionStructure unionStructure);
    UnionStructure toEntity(UnionStructureDto unionStructureDto);
}

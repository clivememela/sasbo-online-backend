package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code ProvinceDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("ProvinceMapper")
class ProvinceMapperTest extends AbstractMapperContractTest<Province, ProvinceDto> {

    ProvinceMapperTest() {
        super(Province.class, ProvinceDto.class, new ProvinceMapperImpl()::toDto, 2);
    }
}

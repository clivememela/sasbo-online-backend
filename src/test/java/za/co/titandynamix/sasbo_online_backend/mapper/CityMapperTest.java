package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.location.CityDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code CityDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("CityMapper")
class CityMapperTest extends AbstractMapperContractTest<City, CityDto> {

    CityMapperTest() {
        super(City.class, CityDto.class, new CityMapperImpl()::toDto, 2);
    }
}

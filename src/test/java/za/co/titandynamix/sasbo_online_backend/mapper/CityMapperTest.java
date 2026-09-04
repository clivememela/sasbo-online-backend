package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.location.CityDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.City;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code CityDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("CityMapper")
class CityMapperTest {

    private final CityMapper mapper = new CityMapperImpl();

    @Test
    @DisplayName("every field on CityDto arrives from a fully populated City")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                City.class, CityDto.class, this.mapper::toDto, 2);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

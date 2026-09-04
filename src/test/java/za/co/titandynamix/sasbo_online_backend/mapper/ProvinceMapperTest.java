package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.location.ProvinceDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Province;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code ProvinceDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("ProvinceMapper")
class ProvinceMapperTest {

    private final ProvinceMapper mapper = new ProvinceMapperImpl();

    @Test
    @DisplayName("every field on ProvinceDto arrives from a fully populated Province")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                Province.class, ProvinceDto.class, this.mapper::toDto, 2);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

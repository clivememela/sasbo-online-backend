package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.location.TownDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code TownDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("TownMapper")
class TownMapperTest {

    private final TownMapper mapper = new TownMapperImpl();

    @Test
    @DisplayName("every field on TownDto arrives from a fully populated Town")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                Town.class, TownDto.class, this.mapper::toDto, 2);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

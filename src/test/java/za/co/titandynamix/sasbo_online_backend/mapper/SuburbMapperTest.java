package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.location.SuburbDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code SuburbDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("SuburbMapper")
class SuburbMapperTest {

    private final SuburbMapper mapper = new SuburbMapperImpl();

    @Test
    @DisplayName("every field on SuburbDto arrives from a fully populated Suburb")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                Suburb.class, SuburbDto.class, this.mapper::toDto, 3);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

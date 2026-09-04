package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code RepresentativeRoleDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("RepresentativeRoleMapper")
class RepresentativeRoleMapperTest {

    private final RepresentativeRoleMapper mapper = new RepresentativeRoleMapperImpl();

    @Test
    @DisplayName("every field on RepresentativeRoleDto arrives from a fully populated RepresentativeRole")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                RepresentativeRole.class, RepresentativeRoleDto.class, this.mapper::toDto, 3);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

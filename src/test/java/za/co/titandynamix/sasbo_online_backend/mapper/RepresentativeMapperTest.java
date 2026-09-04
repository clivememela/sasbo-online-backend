package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code RepresentativeDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("RepresentativeMapper")
class RepresentativeMapperTest {

    private final RepresentativeMapper mapper = new RepresentativeMapperImpl();

    @Test
    @DisplayName("every field on RepresentativeDto arrives from a fully populated Representative")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                Representative.class, RepresentativeDto.class, this.mapper::toDto, 8,
                MapperContract.expecting()
                        .computed("name", "repFullName joins firstNames and surname")
                        .computed("membersCount", "countMembers reads the members collection")
                        .unfillable("structureIds", "mapped from the structures collection, "
                                + "which the synthetic fixture does not populate"));
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

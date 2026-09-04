package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Copied from {@code BankMapperTest}, the reference. Three things change: the
 * mapper, the two types, and the non-vacuity floor.
 *
 * <p>The field list is deliberately absent. {@link MapperContract} walks
 * {@code UnionStructureDto} by reflection, so a field added next month is covered because
 * it exists -- which is the whole point, and why a hand-written list would be
 * worse than nothing here.
 */
@DisplayName("UnionStructureMapper")
class UnionStructureMapperTest {

    private final UnionStructureMapper mapper = new UnionStructureMapperImpl();

    @Test
    @DisplayName("every field on UnionStructureDto arrives from a fully populated UnionStructure")
    void everyTargetFieldArrives() {
        MapperContract.assertEveryTargetFieldArrives(
                UnionStructure.class, UnionStructureDto.class, this.mapper::toDto, 3,
                MapperContract.expecting()
                        .unfillable("parentId", "mapped from parent.id, and parent is a "
                                + "self-reference the synthetic fixture stops at to keep a "
                                + "cycle from recursing. The mapping itself is asserted by "
                                + "mapsAChildToItsParentId below, against a real parent")
                        .unfillable("children", "a collection; the synthetic fixture leaves "
                                + "collections alone, because what is inside one is a domain "
                                + "decision rather than a generic filler's"));
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        assertThat(this.mapper.toDto(null)).isNull();
    }
}

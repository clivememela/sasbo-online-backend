package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.location.TownDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Town;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code TownDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("TownMapper")
class TownMapperTest extends AbstractMapperContractTest<Town, TownDto> {

    TownMapperTest() {
        super(Town.class, TownDto.class, new TownMapperImpl()::toDto, 2);
    }
}

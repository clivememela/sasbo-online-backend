package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.location.SuburbDto;
import za.co.titandynamix.sasbo_online_backend.entity.location.Suburb;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code SuburbDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("SuburbMapper")
class SuburbMapperTest extends AbstractMapperContractTest<Suburb, SuburbDto> {

    SuburbMapperTest() {
        super(Suburb.class, SuburbDto.class, new SuburbMapperImpl()::toDto, 3);
    }
}

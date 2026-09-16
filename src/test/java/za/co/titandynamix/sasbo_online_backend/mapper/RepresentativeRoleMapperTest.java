package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeRoleDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.RepresentativeRole;

/**
 * Four lines, because {@link AbstractMapperContractTest} holds the two
 * assertions every mapper test makes. What is specific to this mapper is the
 * two types and the non-vacuity floor -- and the floor is deliberately below
 * {@code RepresentativeRoleDto}'s real field count, because it proves the walk walked rather
 * than setting a coverage target.
 */
@DisplayName("RepresentativeRoleMapper")
class RepresentativeRoleMapperTest extends AbstractMapperContractTest<RepresentativeRole, RepresentativeRoleDto> {

    RepresentativeRoleMapperTest() {
        super(RepresentativeRole.class, RepresentativeRoleDto.class, new RepresentativeRoleMapperImpl()::toDto, 3);
    }
}

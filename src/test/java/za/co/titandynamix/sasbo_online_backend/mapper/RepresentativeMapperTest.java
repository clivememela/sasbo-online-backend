package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.representative.RepresentativeDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.Representative;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two shared assertions come from {@link AbstractMapperContractTest}. What
 * is specific here is the three computed fields, which are built by custom
 * methods rather than copied — so "it arrived" is all the reflective walk can
 * say about them, and the values are asserted directly below.
 */
@DisplayName("RepresentativeMapper")
class RepresentativeMapperTest
        extends AbstractMapperContractTest<Representative, RepresentativeDto> {

    private static final RepresentativeMapper MAPPER = new RepresentativeMapperImpl();

    RepresentativeMapperTest() {
        super(Representative.class, RepresentativeDto.class, MAPPER::toDto, 8,
                MapperContract.expecting()
                        .computed("name", "repFullName joins firstNames and surname")
                        .computed("membersCount", "countMembers reads the members collection")
                        .unfillable("structureIds", "mapped from the structures collection, "
                                + "which the synthetic fixture does not populate"));
    }

    @Test
    @DisplayName("builds the display name by joining the two name columns")
    void buildsTheDisplayName() {
        // A computed field, so the walk can only say it arrived. What it should
        // CONTAIN is asserted here -- and it is the field the whole browser
        // list is rendered from.
        Representative representative =
                MemberFixture.representative("Thandi", "Khumalo", null);

        assertThat(MAPPER.toDto(representative).getName()).isEqualTo("Thandi Khumalo");
    }

    @Test
    @DisplayName("trims cleanly when one of the two name columns is empty")
    void handlesAMissingName() {
        // Neither column is NOT NULL, so this is reachable. Without the trim the
        // display name comes out with a leading or trailing space, which sorts
        // and matches differently from every other name.
        Representative noSurname = MemberFixture.representative("Thandi", null, null);

        assertThat(MAPPER.toDto(noSurname).getName()).isEqualTo("Thandi");
    }
}

package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.dto.representative.UnionStructureDto;
import za.co.titandynamix.sasbo_online_backend.entity.representative.UnionStructure;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two shared assertions come from {@link AbstractMapperContractTest}. What
 * is specific here is the parent link, which the synthetic fixture cannot
 * populate — so it is asserted directly instead.
 */
@DisplayName("UnionStructureMapper")
class UnionStructureMapperTest
        extends AbstractMapperContractTest<UnionStructure, UnionStructureDto> {

    private static final UnionStructureMapper MAPPER = new UnionStructureMapperImpl();

    UnionStructureMapperTest() {
        super(UnionStructure.class, UnionStructureDto.class, MAPPER::toDto, 3,
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
    @DisplayName("a child structure carries its parent's id")
    void mapsAChildToItsParentId() {
        // The half the reflective walk cannot reach. parentId is the field the
        // browser's cascading structure dropdown is built from, so a mapper
        // that dropped it would flatten the hierarchy into a list and every
        // structure would look top-level.
        UnionStructure parent = MemberFixture.structure("SASBO");
        parent.setId(1);

        UnionStructure child = MemberFixture.structure("Provincial Committee");
        child.setId(2);
        child.setParent(parent);

        assertThat(MAPPER.toDto(child).getParentId()).isEqualTo(1);
    }

    @Test
    @DisplayName("a root structure has no parent id, rather than a zero")
    void mapsARootToANullParentId() {
        // The boundary, and it matters: the browser treats null as "top level".
        // A 0 would be read as a real id that resolves to nothing.
        UnionStructure root = MemberFixture.structure("SASBO");
        root.setId(1);

        assertThat(MAPPER.toDto(root).getParentId()).isNull();
    }
}

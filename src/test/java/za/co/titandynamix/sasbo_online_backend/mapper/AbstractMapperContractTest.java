package za.co.titandynamix.sasbo_online_backend.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.MapperContract;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two assertions every mapper test makes, written once.
 *
 * <h2>Why the ten files still exist</h2>
 * They could have been one parameterized class, and they are not, deliberately:
 * {@code CoverageFloorTest} asserts that <b>every mapper has a test named after
 * it</b>. That register is the coverage contract, and collapsing ten files into
 * one would satisfy the guard by deleting the thing it checks.
 *
 * <p>So the files stay and the bodies go. Six of the ten were the same
 * thirty-seven lines with three tokens swapped -- the Javadoc on each even said
 * so. A subclass now names the two types, the mapping method and the floor, and
 * adds any assertion specific to its own mapper.
 *
 * <h2>What a subclass supplies</h2>
 * <ul>
 *   <li>the source and target types -- the target is the specification;
 *   <li>the mapping method, as a reference;
 *   <li>a non-vacuity floor, deliberately BELOW the target's real field count;
 *   <li>optionally a {@link MapperContract.Declared} for the fields that are
 *       not plain copies.
 * </ul>
 */
abstract class AbstractMapperContractTest<S, T> {

    private final Class<S> sourceType;
    private final Class<T> targetType;
    private final Function<S, T> map;
    private final int floor;
    private final MapperContract.Declared declared;

    protected AbstractMapperContractTest(
            Class<S> sourceType, Class<T> targetType, Function<S, T> map, int floor) {
        this(sourceType, targetType, map, floor, MapperContract.expecting());
    }

    protected AbstractMapperContractTest(
            Class<S> sourceType, Class<T> targetType, Function<S, T> map, int floor,
            MapperContract.Declared declared) {
        this.sourceType = sourceType;
        this.targetType = targetType;
        this.map = map;
        this.floor = floor;
        this.declared = declared;
    }

    @Test
    @DisplayName("every field on the DTO arrives from a fully populated entity")
    void everyTargetFieldArrives() {
        // No field list anywhere. MapperContract walks the target type by
        // reflection, so field 24 is covered the day someone adds it -- which
        // is the whole point, and why a hand-written list would be worse than
        // nothing here.
        MapperContract.assertEveryTargetFieldArrives(
                this.sourceType, this.targetType, this.map, this.floor, this.declared);
    }

    @Test
    @DisplayName("maps a null entity to null rather than an empty DTO")
    void mapsNullToNull() {
        // An empty DTO here would reach the client as a record with no values,
        // which reads as a data problem rather than an absent one.
        assertThat(this.map.apply(null)).isNull();
    }
}

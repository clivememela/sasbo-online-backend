package za.co.titandynamix.sasbo_online_backend.support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The field-exhaustive mapper assertion, written once for all ten mappers.
 *
 * <h2>The defect it catches</h2>
 * A mapper test that names its fields by hand passes forever after someone adds
 * field 24 and forgets to map it. That is "a field that reads but does not
 * write" -- the worst defect class these standards name -- at the layer where
 * it is cheapest to catch and where it is otherwise invisible: the field is
 * present in the API response, it is simply always {@code null}, and every
 * other test agrees the endpoint works.
 *
 * <p>So this test has <b>no list of its own to forget</b>. It populates every
 * source field by reflection, maps, and then walks the TARGET type demanding
 * that each of its fields arrived. The target type is the specification, which
 * is legitimate here because MapStruct generates the implementation from it:
 * reflecting over a generated mapper is reading the contract, not guessing at
 * it.
 *
 * <h2>Flattened mappings are resolved, not excused</h2>
 * A target field with no same-named source field is tried as a camel-case path:
 * {@code bankName} against {@code bank.name}, {@code branchCode} against
 * {@code branch.code}. That is MapStruct's own convention, so the guard follows
 * the same rule the mapper does rather than being told the answer.
 *
 * <h2>What an unmapped field costs</h2>
 * A target field that resolves to nothing fails the test <b>by name</b>. If it
 * genuinely has no source, it goes in {@code knownUnmapped} <b>with a written
 * reason</b> -- and the guard then asserts it is still {@code null}. The day
 * someone maps it, this test reddens and the entry must be removed. A
 * subtraction list that silently absorbs new entries hides its third one; an
 * asserted list cannot.
 *
 * <h2>Its own reddening change</h2>
 * Add a field to a response DTO and to nothing else. This must go red naming
 * that field. Note that adding it to the entity <i>as well</i> does NOT redden
 * it -- MapStruct maps same-named fields implicitly, so that change is
 * genuinely correct and the guard is right to stay green.
 */
public final class MapperContract {

    private MapperContract() {
    }

    /**
     * Asserts every field of {@code targetType} was populated from a populated
     * source.
     *
     * @param sourceType    the entity, populated field-by-field by {@link Synthetic}
     * @param targetType    the DTO. Its fields are the specification
     * @param map           the mapper call under test
     * @param floor         a non-vacuity canary on the target's field count,
     *                      deliberately BELOW the real number. It is not a
     *                      coverage target -- it only proves the walk walked
     * @param knownUnmapped target field name to the written reason it has no
     *                      source. Each is asserted to still be {@code null}
     */
    public static <S, T> void assertEveryTargetFieldArrives(
            Class<S> sourceType,
            Class<T> targetType,
            Function<S, T> map,
            int floor,
            Map<String, String> knownUnmapped) {

        S source = Synthetic.populated(sourceType);
        T target = map.apply(source);

        assertThat(target)
                .as("%s returned null for a fully populated %s",
                        targetType.getSimpleName(), sourceType.getSimpleName())
                .isNotNull();

        List<Field> targetFields = Synthetic.fieldsOf(targetType);
        assertNonVacuousScan(targetFields, floor, targetType.getSimpleName() + " fields");

        Map<String, String> unmapped = new LinkedHashMap<>();
        List<String> checked = new ArrayList<>();

        for (Field field : targetFields) {
            String name = field.getName();
            Object actual = read(target, field);

            if (knownUnmapped.containsKey(name)) {
                assertThat(actual)
                        .as("'%s' is on this mapper's knownUnmapped list -- \"%s\" -- but it now "
                                + "arrives with a value. If it has been mapped deliberately, "
                                + "delete the entry. A subtraction list nobody has to maintain "
                                + "stops describing anything",
                                name, knownUnmapped.get(name))
                        .isNull();
                continue;
            }

            Object expected = resolve(source, name);
            if (expected == null) {
                unmapped.put(name, "no source field or flattened path resolves it");
                continue;
            }

            checked.add(name);
            assertThat(actual)
                    .as("'%s' did not arrive. Every field on %s was populated, so a null here is "
                            + "the mapper dropping it -- a field that reads but does not write. "
                            + "Expected the value of the source path for '%s'",
                            name, sourceType.getSimpleName(), name)
                    .isNotNull();
            assertThat(String.valueOf(actual))
                    .as("'%s' arrived with the wrong value, which is a crossed wire rather than a "
                            + "missing one. Every synthetic value is derived from its own field "
                            + "name, so a mapping pointed at the wrong source shows up here",
                            name)
                    .isEqualTo(String.valueOf(expected));
        }

        if (!unmapped.isEmpty()) {
            fail("%s -> %s leaves %d target field(s) with no source: %s%n%n"
                    + "Either map them, or add each to knownUnmapped with the reason it cannot be "
                    + "mapped. Do not delete this assertion: a DTO field that is always null is "
                    + "invisible to every other test, because the endpoint still answers 200.",
                    sourceType.getSimpleName(), targetType.getSimpleName(),
                    unmapped.size(), unmapped.keySet());
        }

        assertThat(checked)
                .as("no target field on %s was actually checked, which means this guard passed "
                        + "without asserting anything", targetType.getSimpleName())
                .isNotEmpty();
    }

    /** The common case: nothing is expected to be unmapped. */
    public static <S, T> void assertEveryTargetFieldArrives(
            Class<S> sourceType, Class<T> targetType, Function<S, T> map, int floor) {
        assertEveryTargetFieldArrives(sourceType, targetType, map, floor, Map.of());
    }

    /**
     * The value a target field should have come from: a same-named source
     * field, or the camel-case path MapStruct would have flattened.
     *
     * <p>{@code bankName} is tried as {@code bank} then {@code name};
     * {@code provinceCommitteeCode} as {@code province.committeeCode} and
     * {@code provinceCommittee.code}. Returns {@code null} when nothing
     * resolves, which is the caller's signal to fail by name.
     */
    private static Object resolve(Object root, String targetFieldName) {
        Object direct = readByName(root, targetFieldName);
        if (direct != null) {
            return direct;
        }
        for (int i = 1; i < targetFieldName.length(); i++) {
            if (!Character.isUpperCase(targetFieldName.charAt(i))) {
                continue;
            }
            String head = targetFieldName.substring(0, i);
            String tail = Character.toLowerCase(targetFieldName.charAt(i))
                    + targetFieldName.substring(i + 1);
            Object nested = readByName(root, head);
            if (nested != null) {
                Object value = resolve(nested, tail);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static Object readByName(Object owner, String name) {
        for (Field field : Synthetic.fieldsOf(owner.getClass())) {
            if (field.getName().equals(name)) {
                return read(owner, field);
            }
        }
        return null;
    }

    private static Object read(Object owner, Field field) {
        try {
            field.setAccessible(true);
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read " + field.getName(), e);
        }
    }
}

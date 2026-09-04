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
 * <p>The risk is not hypothetical here. {@code MemberMapper} is declared
 * {@code unmappedTargetPolicy = ReportingPolicy.IGNORE}, so MapStruct will not
 * even warn about a dropped field on a 61-field DTO.
 *
 * <p>So this test has <b>no list of its own to forget</b>. It populates every
 * source field by reflection, maps, and then walks the TARGET type demanding
 * that each of its fields arrived. The target type is the specification, which
 * is legitimate here because MapStruct generates the implementation from it:
 * reflecting over a generated mapper is reading the contract, not guessing.
 *
 * <h2>Four things a target field can be, and they are not the same thing</h2>
 * A guard with one escape hatch turns every awkward field into "excluded", and
 * then the list means nothing. These are kept apart deliberately:
 *
 * <table border="1">
 *   <caption>How each declared field is treated</caption>
 *   <tr><th>Declared as</th><th>Asserted</th><th>Means</th></tr>
 *   <tr><td>(nothing)</td><td>arrived, and equals its source</td>
 *       <td>a plain copy, resolved by name or by MapStruct's camel-case
 *           flattening ({@code bankName} against {@code bank.name})</td></tr>
 *   <tr><td>{@code sourcePath}</td><td>arrived, and equals THAT path</td>
 *       <td>an explicit {@code @Mapping} the walker could not have guessed --
 *           transcribed from the mapper, so changing the mapper reddens the
 *           test</td></tr>
 *   <tr><td>{@code computed}</td><td>arrived</td>
 *       <td>built by a {@code qualifiedByName} method rather than copied. The
 *           value is not a source value, but "it arrived" is still checkable
 *           and is what the defect class is about</td></tr>
 *   <tr><td>{@code unfillable}</td><td>nothing</td>
 *       <td>its source is a collection or a self-reference that
 *           {@link Synthetic} deliberately does not populate. A limitation of
 *           the fixture, openly stated -- not a claim about the mapper</td></tr>
 *   <tr><td>{@code unmapped}</td><td>still holds its type's default</td>
 *       <td>a real gap. The day it is mapped this reddens and the entry has to
 *           go, so the list cannot outlive the problem</td></tr>
 * </table>
 *
 * <p>Anything not declared and not resolvable fails <b>by name</b>.
 *
 * <h2>Its own reddening change</h2>
 * Add a field to a response DTO and to nothing else. This must go red naming
 * that field. Adding it to the entity <i>as well</i> does NOT redden it --
 * MapStruct maps same-named fields implicitly, so that change is genuinely
 * correct and the guard is right to stay green.
 */
public final class MapperContract {

    private MapperContract() {
    }

    /** How the caller declares the fields the walker cannot work out alone. */
    public static final class Declared {

        private final Map<String, String> sourcePaths = new LinkedHashMap<>();
        private final Map<String, String> computed = new LinkedHashMap<>();
        private final Map<String, String> unfillable = new LinkedHashMap<>();
        private final Map<String, String> unmapped = new LinkedHashMap<>();

        private Declared() {
        }

        /** An explicit {@code @Mapping}, transcribed from the mapper. */
        public Declared sourcePath(String targetField, String path) {
            this.sourcePaths.put(targetField, path);
            return this;
        }

        /** Built by a custom method. Asserted to arrive, not to equal a source. */
        public Declared computed(String targetField, String how) {
            this.computed.put(targetField, how);
            return this;
        }

        /** Its source is something {@link Synthetic} does not populate. */
        public Declared unfillable(String targetField, String why) {
            this.unfillable.put(targetField, why);
            return this;
        }

        /**
         * A real gap. Asserted to still hold its type's default -- {@code null}
         * for a reference, {@code false} or {@code 0} for a primitive.
         *
         * <p>The primitive case is the nastier one and the reason this is not
         * simply a null check: an unmapped {@code boolean} reads as
         * {@code false}, which looks like an answer rather than an absence.
         */
        public Declared unmapped(String targetField, String why) {
            this.unmapped.put(targetField, why);
            return this;
        }
    }

    /** Nothing declared: every target field is expected to be a plain copy. */
    public static Declared expecting() {
        return new Declared();
    }

    /** The common case, where every target field is a plain copy. */
    public static <S, T> void assertEveryTargetFieldArrives(
            Class<S> sourceType, Class<T> targetType, Function<S, T> map, int floor) {
        assertEveryTargetFieldArrives(sourceType, targetType, map, floor, expecting());
    }

    /**
     * Asserts every field of {@code targetType} was populated from a populated
     * source, treating each declared field as its declaration says.
     *
     * @param floor a non-vacuity canary on the target's field count,
     *              deliberately BELOW the real number. Not a coverage target --
     *              it only proves the walk walked
     */
    public static <S, T> void assertEveryTargetFieldArrives(
            Class<S> sourceType,
            Class<T> targetType,
            Function<S, T> map,
            int floor,
            Declared declared) {

        S source = Synthetic.populated(sourceType);
        T target = map.apply(source);

        assertThat(target)
                .as("%s returned null for a fully populated %s",
                        targetType.getSimpleName(), sourceType.getSimpleName())
                .isNotNull();

        List<Field> targetFields = Synthetic.fieldsOf(targetType);
        assertNonVacuousScan(targetFields, floor, targetType.getSimpleName() + " fields");

        Map<String, String> unresolved = new LinkedHashMap<>();
        List<String> checked = new ArrayList<>();

        for (Field field : targetFields) {
            String name = field.getName();
            Object actual = read(target, field);

            if (declared.unfillable.containsKey(name)) {
                continue;
            }

            if (declared.unmapped.containsKey(name)) {
                assertThat(isDefault(field.getType(), actual))
                        .as("'%s' is declared unmapped -- \"%s\" -- but it now arrives with a "
                                + "value rather than its type's default. If it has been mapped "
                                + "deliberately, delete the entry. A list nobody has to maintain "
                                + "stops describing anything", name, declared.unmapped.get(name))
                        .isTrue();
                continue;
            }

            if (declared.computed.containsKey(name)) {
                checked.add(name);
                assertThat(actual)
                        .as("'%s' is declared computed -- \"%s\" -- and did not arrive. A "
                                + "computed field's value is not a source value, but it still "
                                + "has to be there", name, declared.computed.get(name))
                        .isNotNull();
                continue;
            }

            String path = declared.sourcePaths.get(name);
            Object expected = path != null ? follow(source, path) : resolve(source, name);

            if (expected == null) {
                unresolved.put(name, path != null
                        ? "declared source path '" + path + "' resolves to nothing"
                        : "no source field or flattened path resolves it");
                continue;
            }

            checked.add(name);
            assertThat(actual)
                    .as("'%s' did not arrive. Every field on %s was populated, so a null here is "
                            + "the mapper dropping it -- a field that reads but does not write",
                            name, sourceType.getSimpleName())
                    .isNotNull();
            assertThat(String.valueOf(actual))
                    .as("'%s' arrived with the wrong value, which is a crossed wire rather than a "
                            + "missing one. Every synthetic value is derived from its own field "
                            + "name, so a mapping pointed at the wrong source shows up here", name)
                    .isEqualTo(String.valueOf(expected));
        }

        if (!unresolved.isEmpty()) {
            fail("%s -> %s leaves %d target field(s) unaccounted for: %s%n%n"
                    + "Map them, or declare each one: sourcePath for an explicit @Mapping, "
                    + "computed for a qualifiedByName method, unfillable if the synthetic fixture "
                    + "cannot populate its source, unmapped if it is a real gap. Do not delete "
                    + "this assertion: a DTO field that is always null is invisible to every "
                    + "other test, because the endpoint still answers 200.",
                    sourceType.getSimpleName(), targetType.getSimpleName(),
                    unresolved.size(), unresolved.keySet());
        }

        assertThat(checked)
                .as("no target field on %s was actually checked, which means this guard passed "
                        + "without asserting anything", targetType.getSimpleName())
                .isNotEmpty();
    }

    /**
     * Whether a value is what an unset field of that type holds.
     *
     * <p>A reference is {@code null}. A primitive is {@code false} or zero --
     * and that case is why this method exists rather than a null check: an
     * unmapped {@code boolean} on a DTO reads as a confident {@code false} to
     * every consumer, which is worse than an obvious absence.
     */
    private static boolean isDefault(Class<?> type, Object value) {
        if (!type.isPrimitive()) {
            return value == null;
        }
        if (type == boolean.class) {
            return Boolean.FALSE.equals(value);
        }
        if (type == char.class) {
            return Character.valueOf(' ').equals(value);
        }
        return value instanceof Number number && number.doubleValue() == 0d;
    }

    /** Walks an explicit dotted path, e.g. {@code jobGrade.grade}. */
    private static Object follow(Object root, String path) {
        Object current = root;
        for (String segment : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            current = readByName(current, segment);
        }
        return current;
    }

    /**
     * The value a target field should have come from: a same-named source
     * field, or the camel-case path MapStruct would have flattened.
     *
     * <p>{@code bankName} is tried as {@code bank} then {@code name}. Returns
     * {@code null} when nothing resolves, which is the caller's signal to fail
     * by name.
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

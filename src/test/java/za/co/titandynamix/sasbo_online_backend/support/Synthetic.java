package za.co.titandynamix.sasbo_online_backend.support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fills an object's every field with a distinctive, deterministic value.
 *
 * <p>It exists so that a mapper test does not have to name fields. A test that
 * writes its own field list passes forever after someone adds field 24 and
 * forgets to map it -- which is "a field that reads but does not write", the
 * worst defect class this suite guards, at the layer where it is cheapest to
 * catch. Reflection is how the test is given no list of its own to forget.
 *
 * <h2>Deterministic, never random and never the clock</h2>
 * Every value is derived from the field's own name by hashing, so the same
 * field gets the same value on every run, on every machine, forever. There is
 * no RNG to seed and no wall clock to read: a test whose input changes between
 * runs is a flake waiting for a slow day, and a test that derives an expected
 * value from {@code LocalDate.now()} changes its own answer on 1 January.
 *
 * <p>Values are distinctive per field on purpose. If every String were
 * {@code "x"}, a mapper that wired {@code surname} to the {@code firstNames}
 * source would still pass -- the values would match by accident. Seeding from
 * the field name makes a crossed wire fail.
 *
 * <h2>What it does not touch</h2>
 * Collections and maps are left as the constructor made them. A populated
 * collection means deciding what is inside it, and that is a fixture decision
 * belonging to a domain builder, not to a generic filler.
 *
 * <p>Nested objects of this application's own types ARE built and populated, to
 * a bounded depth and never revisiting a type already on the path. Without
 * that, a flattened mapping such as {@code bankName} from {@code bank.name}
 * could not be checked at all. With it, {@code Bank} holding {@code BankBranch}
 * holding {@code Bank} still terminates.
 */
public final class Synthetic {

    /** Fixed. Never {@code LocalDate.now()} -- see the class comment. */
    private static final LocalDate DATE = LocalDate.of(1994, 4, 27);
    private static final LocalTime TIME = LocalTime.of(10, 30, 0);
    private static final LocalDateTime DATE_TIME = LocalDateTime.of(DATE, TIME);

    /**
     * How deep nested objects of our own types are built. Three is enough for
     * every flattening this application actually performs
     * ({@code member.branch.bank.name} is the deepest) and shallow enough that
     * a mistake here cannot become a slow test.
     */
    private static final int MAX_DEPTH = 3;

    private Synthetic() {
    }

    /** A new instance of {@code type} with every field it can fill, filled. */
    public static <T> T populated(Class<T> type) {
        T instance = instantiate(type);
        populate(instance, 0, new HashSet<>(Set.of(type)));
        return instance;
    }

    /** Fills {@code target} in place. Its existing values are overwritten. */
    public static void populate(Object target) {
        populate(target, 0, new HashSet<>(Set.of(target.getClass())));
    }

    private static void populate(Object target, int depth, Set<Class<?>> onPath) {
        for (Field field : fieldsOf(target.getClass())) {
            Object value = valueFor(field.getType(), field.getName(), depth, onPath);
            if (value == null) {
                continue;
            }
            try {
                field.set(target, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not set " + field.getName(), e);
            }
        }
    }

    /**
     * Every instance field a test may fill, including inherited ones.
     *
     * <p>{@code static} is skipped because it is not per-instance state, and
     * {@code synthetic} because those are the compiler's own fields -- JaCoCo's
     * coverage counter and the outer-class reference on a nested class. Setting
     * either is meaningless, and setting JaCoCo's breaks the run.
     */
    public static List<Field> fieldsOf(Class<?> type) {
        return FIELDS.computeIfAbsent(type, Synthetic::walkFields);
    }

    /**
     * Reads one field, with the checked exception turned into an unchecked one.
     *
     * <p>Here rather than in each caller: three places were doing the same
     * {@code setAccessible} plus {@code try/catch} dance around one line.
     */
    public static Object read(Object owner, Field field) {
        try {
            return field.get(owner);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Could not read " + field.getName(), e);
        }
    }

    /**
     * Cached per class: {@code getDeclaredFields()} clones its array on every
     * call, and the mapper contract asks for a class's fields once per field
     * lookup -- several hundred rebuilds for one 61-field assertion.
     */
    private static final Map<Class<?>, List<Field>> FIELDS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static List<Field> walkFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                    continue;
                }
                // Once, when the list is built, rather than on every read.
                f.setAccessible(true);
                fields.add(f);
            }
        }
        return List.copyOf(fields);
    }

    private static Object valueFor(Class<?> type, String seed, int depth, Set<Class<?>> onPath) {
        if (type == String.class) {
            return seed + "-" + stableInt(seed);
        }
        if (type == Integer.class || type == int.class) {
            return stableInt(seed);
        }
        if (type == Long.class || type == long.class) {
            return (long) stableInt(seed);
        }
        if (type == Double.class || type == double.class) {
            return stableInt(seed) + 0.5d;
        }
        if (type == Float.class || type == float.class) {
            return stableInt(seed) + 0.5f;
        }
        if (type == BigDecimal.class) {
            return BigDecimal.valueOf(stableInt(seed));
        }
        if (type == Boolean.class || type == boolean.class) {
            return Boolean.TRUE;
        }
        if (type == Character.class || type == char.class) {
            return seed.charAt(0);
        }
        if (type == LocalDate.class) {
            return DATE;
        }
        if (type == LocalDateTime.class) {
            return DATE_TIME;
        }
        if (type == LocalTime.class) {
            return TIME;
        }
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            return constants.length == 0 ? null : constants[0];
        }
        if (Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) {
            // Left alone deliberately -- see the class comment.
            return null;
        }
        if (isOurs(type) && depth < MAX_DEPTH && !onPath.contains(type)) {
            Object nested = instantiate(type);
            Set<Class<?>> deeper = new HashSet<>(onPath);
            deeper.add(type);
            populate(nested, depth + 1, deeper);
            return nested;
        }
        return null;
    }

    /**
     * A value derived from the field name alone: same field, same value, every
     * run, every machine. Bounded to keep it inside a {@code length = 6} column
     * when it is stringified, and never zero, so "was it set?" and "is it the
     * default?" are different questions.
     */
    private static int stableInt(String seed) {
        return Math.abs(seed.hashCode() % 9000) + 1000;
    }

    private static boolean isOurs(Class<?> type) {
        return type.getName().startsWith("za.co.titandynamix.");
    }

    private static <T> T instantiate(Class<T> type) {
        try {
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    type.getSimpleName() + " has no usable no-argument constructor, so it cannot be "
                            + "populated by reflection. Every entity and DTO here is a Lombok "
                            + "@NoArgsConstructor or @Data type; if this one is not, it needs a "
                            + "hand-written fixture rather than a change to this helper.", e);
        }
    }
}

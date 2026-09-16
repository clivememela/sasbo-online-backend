package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.Floors;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.TestSourceInventory.TestClassSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * The disabled population, asserted exactly.
 *
 * <h2>Why counting them is a test and not a note</h2>
 * This suite has no flaky list, no quarantine, and nothing skipped for
 * unreliability. {@code @Disabled} means exactly one thing here: a test written
 * to the correct behaviour, against a real defect, left visibly failing by
 * design.
 *
 * <p>Individually each is justified. <b>Collectively, nothing was counting
 * them</b> -- and a suite carrying a dozen disabled tests with no register
 * reads as neglected rather than deliberate, which is how the next one gets
 * added quietly, and how a <i>fixed</i> one sits disabled forever.
 *
 * <h2>What it asserts, in both directions</h2>
 * <ul>
 *   <li>Every {@code @Disabled} cites a {@link KnownDefects} row. Adding one
 *       without a row reddens.
 *   <li>Every {@code KnownDefects} row is cited by at least one disabled test.
 *       <b>Fixing a defect and leaving its test disabled reddens too</b> -- and
 *       that is the half that actually rots, because nothing else in a suite
 *       ever complains about it.
 *   <li>The counts match.
 * </ul>
 *
 * <p>Same discipline as an asserted subtraction list, and for the same reason:
 * a list nobody has to maintain hides its third entry.
 */
@DisplayName("The disabled population")
class DisabledPopulationTest {

    /**
     * Matches the annotation in USE, not in prose. A bare substring search
     * would match this class, its own Javadoc and every comment discussing the
     * subject -- so the guard would convict itself the moment it was written.
     */
    private static final Pattern DISABLED_CITING_A_ROW =
            Pattern.compile("^\\s*@Disabled\\(\\s*KnownDefects\\.(\\w+)", Pattern.MULTILINE);

    private static final Pattern ANY_DISABLED = Pattern.compile("^\\s*@Disabled", Pattern.MULTILINE);

    @Test
    @DisplayName("every disabled test cites a register row")
    void everyDisabledTestCitesARow() {
        List<TestClassSource> classes = TestSourceInventory.all();
        assertNonVacuousScan(classes, Floors.TEST_CLASSES, "test classes");

        List<String> uncited = new ArrayList<>();
        for (TestClassSource source : classes) {
            int total = count(ANY_DISABLED, source.body());
            int cited = count(DISABLED_CITING_A_ROW, source.body());
            if (total > cited) {
                uncited.add(source.name() + " has " + (total - cited)
                        + " @Disabled without a KnownDefects row");
            }
        }

        assertThat(uncited)
                .as("a disabled test with no register row is indistinguishable from a test "
                        + "somebody switched off. Every one of them names the defect it is "
                        + "asserting, in the annotation, where the next reader will find it")
                .isEmpty();
    }

    @Test
    @DisplayName("every register row has a disabled test asserting it")
    void everyRowHasADisabledTest() {
        Set<String> cited = citedRows();
        Set<String> declared = declaredRows();

        // Deliberately below the real count, with headroom, and re-derived
        // against the finished suite when the coverage floors are calibrated.
        // A floor set before the scope is known measures the plan's age, not
        // the suite.
        assertNonVacuousScan(declared, 2, "KnownDefects rows");

        Set<String> orphaned = new TreeSet<>(declared);
        orphaned.removeAll(cited);

        assertThat(orphaned)
                .as("these register rows claim a deliberately-failing assertion and no test "
                        + "cites them. Either the test was deleted, or the defect was fixed and "
                        + "nobody removed the row. This is the half that rots: nothing else in a "
                        + "suite ever complains about a stale entry")
                .isEmpty();
    }

    @Test
    @DisplayName("no test cites a register row that does not exist")
    void noTestCitesAMissingRow() {
        Set<String> unknown = new TreeSet<>(citedRows());
        unknown.removeAll(declaredRows());

        assertThat(unknown)
                .as("cited but not declared. This cannot happen through the compiler, so seeing "
                        + "it means the scan's pattern and the constants have drifted apart")
                .isEmpty();
    }

    @Test
    @DisplayName("the counts match, exactly")
    void theCountsMatch() {
        // The assertion that makes this a register rather than two loose
        // checks. Deliberately exact: a population that is allowed to be
        // "roughly right" is one nobody notices growing.
        assertThat(citedRows())
                .as("the register rows cited by a disabled test, against the rows declared")
                .containsExactlyInAnyOrderElementsOf(declaredRows());
    }

    private static Set<String> citedRows() {
        Set<String> cited = new LinkedHashSet<>();
        for (TestClassSource source : TestSourceInventory.all()) {
            Matcher matcher = DISABLED_CITING_A_ROW.matcher(source.body());
            while (matcher.find()) {
                cited.add(matcher.group(1));
            }
        }
        return cited;
    }

    private static Set<String> declaredRows() {
        Set<String> declared = new LinkedHashSet<>();
        for (Field field : KnownDefects.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers())) {
                declared.add(field.getName());
            }
        }
        return declared;
    }

    private static int count(Pattern pattern, String body) {
        Matcher matcher = pattern.matcher(body);
        int found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }
}

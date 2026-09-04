package za.co.titandynamix.sasbo_online_backend.guards;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.support.KnownDefects;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.Layer;
import za.co.titandynamix.sasbo_online_backend.support.MainSourceInventory.MainClassSource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static za.co.titandynamix.sasbo_online_backend.support.NonVacuousScan.assertNonVacuousScan;

/**
 * A service method that issues more than one repository write owns a
 * transaction boundary.
 *
 * <h2>Why this is not a style rule</h2>
 * Atomicity across aggregates is not negotiable in a system that holds
 * membership records for real people. A method that saves two things without a
 * boundary can half-land: the first write commits, the second throws, and the
 * database is left in a state no code path ever intended. Nothing logs it,
 * nothing fails, and it is found months later by someone reading a row that
 * makes no sense.
 *
 * <h2>Discovered, not listed</h2>
 * The scan counts write calls per method by reading the source, so a new
 * multi-write method is covered the day it is written. Two of nineteen services
 * carry {@code @Transactional} today.
 *
 * <p>The counting is deliberately simple: a repository write is a call to
 * {@code save}, {@code saveAll}, {@code saveAndFlush}, {@code delete},
 * {@code deleteAll}, {@code deleteById}. It is a source scan and not a runtime
 * analysis, so it can be fooled by indirection -- which is why it errs toward
 * reporting: a method it names wrongly costs one comment, and a method it
 * misses costs silent data corruption.
 */
@DisplayName("Transaction boundaries")
class TransactionBoundaryTest {

    private static final Pattern METHOD =
            Pattern.compile("\\n {4}(?:public|protected|private)[^;{]*?\\b(\\w+)\\s*\\([^)]*\\)"
                    + "[^;{]*\\{");

    private static final List<String> WRITE_CALLS = List.of(
            ".save(", ".saveAll(", ".saveAndFlush(", ".delete(", ".deleteAll(", ".deleteById(");

    @Test
    @DisplayName("the scan finds the services and the multi-write methods it is meant to grade")
    void theScanIsNotVacuous() {
        List<MainClassSource> services = MainSourceInventory.inLayer(Layer.SERVICE);
        assertNonVacuousScan(services, 15, "services");

        // The guard's own second half. A walker that parses no methods reports
        // no unprotected ones, and this whole class would pass while grading
        // nothing.
        assertNonVacuousScan(multiWriteMethods(), 1, "multi-write service methods");
    }

    @Test
    @Disabled(KnownDefects.MULTI_WRITE_WITHOUT_A_TRANSACTION)
    @DisplayName("every service method issuing more than one write is transactional")
    void everyMultiWriteMethodIsTransactional() {
        List<String> unprotected = multiWriteMethods().stream()
                .filter(m -> !m.transactional())
                .map(MultiWrite::describe)
                .toList();

        assertThat(unprotected)
                .as("these methods issue more than one repository write with no transaction "
                        + "boundary. A failure between the writes leaves the data half written, "
                        + "with nothing logged and nothing failed")
                .isEmpty();
    }

    @Test
    @DisplayName("the two services that do declare a boundary still declare it")
    void theExistingBoundariesAreStillThere() {
        // The half that can pass today, and the half that catches a removal.
        // Without it, deleting @Transactional from the two services that have
        // one would be invisible: the disabled test above is already red by
        // design, so it cannot report a regression.
        List<String> withBoundary = MainSourceInventory.inLayer(Layer.SERVICE).stream()
                .filter(s -> s.contains("@Transactional"))
                .map(MainClassSource::name)
                .sorted()
                .toList();

        assertThat(withBoundary)
                .as("the services that declare a transaction boundary today. If one disappears "
                        + "from this list, a boundary was removed")
                .containsExactly("DeletionCodeServiceImpl", "RepresentativeServiceImpl");
    }

    /** A service method that writes more than once, and whether it is guarded. */
    private record MultiWrite(String owner, String method, int writes, boolean transactional) {
        String describe() {
            return owner + "." + method + "() issues " + writes + " writes with no boundary";
        }
    }

    private static List<MultiWrite> multiWriteMethods() {
        List<MultiWrite> found = new ArrayList<>();
        for (MainClassSource service : MainSourceInventory.inLayer(Layer.SERVICE)) {
            // A class-level @Transactional covers every method in it, so the
            // check is "is this method inside a boundary", not "does this
            // method carry the annotation".
            boolean classLevel = service.code().contains("@Transactional")
                    && service.code().indexOf("@Transactional")
                            < service.code().indexOf("class " + service.name());

            Matcher matcher = METHOD.matcher(service.code());
            List<int[]> spans = new ArrayList<>();
            List<String> names = new ArrayList<>();
            while (matcher.find()) {
                spans.add(new int[] {matcher.end(), 0});
                names.add(matcher.group(1));
            }
            for (int i = 0; i < spans.size(); i++) {
                int start = spans.get(i)[0];
                int end = i + 1 < spans.size() ? spans.get(i + 1)[0] : service.code().length();
                String body = service.code().substring(start, end);

                int writes = 0;
                for (String call : WRITE_CALLS) {
                    int at = 0;
                    while ((at = body.indexOf(call, at)) >= 0) {
                        writes++;
                        at += call.length();
                    }
                }
                if (writes > 1) {
                    String preceding = service.code().substring(
                            Math.max(0, start - 400), start);
                    boolean guarded = classLevel || preceding.contains("@Transactional");
                    found.add(new MultiWrite(service.name(), names.get(i), writes, guarded));
                }
            }
        }
        return found;
    }
}

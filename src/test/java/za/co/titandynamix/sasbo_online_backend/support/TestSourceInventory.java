package za.co.titandynamix.sasbo_online_backend.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Discovers this module's test classes from source, and says which lane each
 * one belongs to.
 *
 * <p>Source rather than the classpath on purpose: the thing being guarded is
 * what an author WROTE. A classpath scan sees only what compiled and was
 * selected, so a class the lane filter silently dropped is invisible to it --
 * and that dropped class is exactly the failure the partition guard exists to
 * catch.
 *
 * <p>Everything here is plain reading. It starts no context and touches no
 * database, so the guards built on it run in the fast lane and keep working on
 * a machine with no Docker at all -- which matters, because the fast lane is
 * the population they protect.
 */
public final class TestSourceInventory {

    private static final Path TEST_SOURCE_ROOT = Paths.get("src", "test", "java");

    private TestSourceInventory() {
    }

    /** One test class as found on disk. */
    public record TestClassSource(Path path, String name, String body) {

        /**
         * Integration lane membership. A class is in it if it carries the tag
         * itself or inherits it from {@link AbstractPostgresIT}, which is the
         * only thing in this module that owns a container.
         */
        public boolean isIntegration() {
            return body.contains("@Tag(\"integration\")")
                    || body.contains("extends AbstractPostgresIT");
        }

        public boolean isFastLane() {
            return !isIntegration();
        }

        /**
         * True only where {@code @Disabled} is USED as an annotation.
         *
         * <p>A plain substring search matches this scanner's own source, the
         * guard that calls it, and every comment that discusses the subject --
         * so the guard convicts itself the moment it is written. Requiring the
         * annotation to open a line is what separates using it from writing
         * about it.
         */
        public boolean hasDisabledAnnotation() {
            return body.lines().map(String::strip).anyMatch(line -> line.startsWith("@Disabled"));
        }
    }

    /**
     * Every test class in this module.
     *
     * <p>The filename convention is the discovery rule, and it is the same one
     * Gradle's test tasks use, so this inventory and the runner cannot disagree
     * about what a test class is.
     */
    public static List<TestClassSource> all() {
        if (!Files.isDirectory(TEST_SOURCE_ROOT)) {
            throw new IllegalStateException(
                    "No test sources at " + TEST_SOURCE_ROOT.toAbsolutePath()
                            + ". A scan that finds nothing reports no offenders and passes, so "
                            + "this fails loudly instead of quietly succeeding.");
        }
        try (Stream<Path> paths = Files.walk(TEST_SOURCE_ROOT)) {
            List<TestClassSource> found = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith("Test.java") && !fileName.endsWith("IT.java")) {
                    continue;
                }
                found.add(new TestClassSource(
                        path,
                        fileName.substring(0, fileName.length() - ".java".length()),
                        Files.readString(path, StandardCharsets.UTF_8)));
            }
            return List.copyOf(found);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the test sources", e);
        }
    }
}

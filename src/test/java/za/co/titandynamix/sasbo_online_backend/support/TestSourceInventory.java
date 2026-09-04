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

    /**
     * {@code extends Abstract<anything>IT} -- every harness base class ends in
     * IT, so this needs no list to keep up to date.
     */
    private static final java.util.regex.Pattern INHERITS_THE_HARNESS =
            java.util.regex.Pattern.compile("extends\\s+Abstract\\w*IT\\b");

    /** One test class as found on disk. */
    public static final class TestClassSource {

        private final Path path;
        private final String name;
        private final String body;
        private String blankedCode;

        TestClassSource(Path path, String name, String body) {
            this.path = path;
            this.name = name;
            this.body = body;
        }

        public Path path() {
            return this.path;
        }

        public String name() {
            return this.name;
        }

        public String body() {
            return this.body;
        }


        /**
         * Integration lane membership. A class is in it if it carries the tag
         * itself, or extends one of the harness base classes -- which are the
         * only things in this module that own a container.
         *
         * <p>Matched by SHAPE ({@code extends Abstract...IT}) rather than by a
         * list of base-class names, and that is not tidiness. The list version
         * named only {@code AbstractPostgresIT}; when
         * {@code AbstractWritingIT} was extracted on 2026-09-04 and six classes
         * were moved onto it, all six were silently reclassified into the FAST
         * lane -- where they promptly failed, because there is no container
         * there. A rule that needs updating when the harness grows is a rule
         * that will not be.
         */
        /**
         * The source with every comment blanked, line numbers preserved.
         *
         * <p>{@link #body()} is the raw text, and a guard searching it convicts
         * a class for a comment that merely mentions the thing being guarded --
         * including a comment explaining why the class does the right thing.
         * That is not hypothetical: the security-slice guard's first run
         * convicted the reference controller slice for describing the rule, and
         * convicted the security slice itself for saying which annotation it
         * deliberately does not carry.
         *
         * <p>Computed on demand and cached, because most guards never ask.
         */
        public String code() {
            String blanked = this.blankedCode;
            if (blanked == null) {
                blanked = MainSourceInventory.blankComments(this.body);
                this.blankedCode = blanked;
            }
            return blanked;
        }

        public boolean isIntegration() {
            return body.contains("@Tag(\"integration\")")
                    || INHERITS_THE_HARNESS.matcher(body).find();
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
     *
     * <p>Read once and held, for the same reason as the production inventory:
     * three guard classes call this nine times a run, over 57 files, and source
     * does not change while the JVM is running.
     */
    public static List<TestClassSource> all() {
        List<TestClassSource> cached = ALL;
        if (cached == null) {
            synchronized (TestSourceInventory.class) {
                cached = ALL;
                if (cached == null) {
                    cached = read();
                    ALL = cached;
                }
            }
        }
        return cached;
    }

    /**
     * Every test class except the one doing the scanning.
     *
     * <p>A guard that searches source for a pattern <b>contains that pattern</b>,
     * as a string literal in its own file — so it convicts itself the moment it
     * is written. Comment blanking does not help: these are literals, not
     * comments, and blanking literals would hide the SQL in a {@code @Query},
     * which is the thing a repository guard most wants to read.
     *
     * <p>So a scanner excludes itself, explicitly and by name. This is the same
     * trap {@code hasDisabledAnnotation} solved by requiring the annotation to
     * open a line; that trick works for annotations and not for the general
     * case, which is what this method is for.
     *
     * @param scanner the guard doing the scanning
     */
    public static List<TestClassSource> allExcept(Class<?> scanner) {
        return all().stream()
                .filter(c -> !c.name().equals(scanner.getSimpleName()))
                .toList();
    }

    private static volatile List<TestClassSource> ALL;

    private static List<TestClassSource> read() {
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

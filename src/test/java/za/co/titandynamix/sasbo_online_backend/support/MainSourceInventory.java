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
 * Discovers this application's production classes from source, and says which
 * layer each one belongs to.
 *
 * <p>This is what the cross-cutting guards scan. They scan {@code src/main},
 * not the test tree, which is why they land <b>before</b> the domain work: they
 * do not depend on how many tests exist, and their findings shape the domain
 * tests rather than arriving after eight domains are written.
 *
 * <h2>Comments are blanked, line numbers are preserved</h2>
 * A guard searching for {@code repository} would otherwise convict a class for
 * a comment that mentions the word -- including a comment explaining why the
 * class does not use one. So every comment is replaced by spaces of the same
 * length, character for character. Blanking rather than deleting is what keeps
 * a reported line number pointing at the line the reader will open.
 *
 * <p>String literals are left alone. A literal containing {@code "repository"}
 * is rare, and blanking them would hide the SQL in a {@code @Query} -- which is
 * the one thing a repository guard most wants to read.
 *
 * <h2>Source, not the classpath</h2>
 * Same reason {@link TestSourceInventory} reads source: the thing being guarded
 * is what an author wrote. It also means these guards need no Spring context
 * and no Docker, so they run in the fast lane -- the pre-commit loop is the
 * population they protect.
 */
public final class MainSourceInventory {

    private static final Path MAIN_SOURCE_ROOT =
            Paths.get("src", "main", "java", "za", "co", "titandynamix", "sasbo_online_backend");

    private MainSourceInventory() {
    }

    /** The layer a class belongs to, decided by the package it sits in. */
    public enum Layer {
        CONTROLLER, SERVICE, MAPPER, REPOSITORY, ENTITY, DTO, VALIDATOR, EXCEPTION, CONFIG, OTHER
    }

    /**
     * One production class as found on disk.
     *
     * @param path    where it lives, for a failure message someone can act on
     * @param name    the simple class name
     * @param layer   the layer its package puts it in
     * @param code    the source with comments blanked and line numbers intact
     */
    public record MainClassSource(Path path, String name, Layer layer, String code) {

        /** The 1-based line numbers on which {@code needle} appears in code. */
        public List<Integer> linesContaining(String needle) {
            List<Integer> hits = new ArrayList<>();
            String[] lines = code.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains(needle)) {
                    hits.add(i + 1);
                }
            }
            return hits;
        }

        public boolean contains(String needle) {
            return code.contains(needle);
        }

        /** {@code Name.java:12} — clickable, and the reason lines are preserved. */
        public String at(int line) {
            return name + ".java:" + line;
        }
    }

    /** Every production class in the application package. */
    public static List<MainClassSource> all() {
        if (!Files.isDirectory(MAIN_SOURCE_ROOT)) {
            throw new IllegalStateException(
                    "No production sources at " + MAIN_SOURCE_ROOT.toAbsolutePath()
                            + ". A scan that finds nothing reports no offenders and passes, so "
                            + "this fails loudly instead of quietly succeeding.");
        }
        try (Stream<Path> paths = Files.walk(MAIN_SOURCE_ROOT)) {
            List<MainClassSource> found = new ArrayList<>();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String fileName = path.getFileName().toString();
                if (!fileName.endsWith(".java")) {
                    continue;
                }
                found.add(new MainClassSource(
                        path,
                        fileName.substring(0, fileName.length() - ".java".length()),
                        layerOf(path),
                        blankComments(Files.readString(path, StandardCharsets.UTF_8))));
            }
            return List.copyOf(found);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the production sources", e);
        }
    }

    /** Every class in one layer. */
    public static List<MainClassSource> inLayer(Layer layer) {
        return all().stream().filter(c -> c.layer() == layer).toList();
    }

    private static Layer layerOf(Path path) {
        String relative = MAIN_SOURCE_ROOT.relativize(path).toString().replace('\\', '/');
        String pkg = relative.contains("/") ? relative.substring(0, relative.indexOf('/')) : "";
        return switch (pkg) {
            case "controller" -> Layer.CONTROLLER;
            case "services" -> Layer.SERVICE;
            case "mapper" -> Layer.MAPPER;
            case "repository" -> Layer.REPOSITORY;
            case "entity" -> Layer.ENTITY;
            case "dto" -> Layer.DTO;
            case "validator" -> Layer.VALIDATOR;
            case "exception" -> Layer.EXCEPTION;
            case "config" -> Layer.CONFIG;
            default -> Layer.OTHER;
        };
    }

    /**
     * Replaces every comment with spaces, keeping every newline.
     *
     * <p>Deliberately not a regex. Java comments nest inside string literals
     * and string literals inside comments, and a regex that gets that wrong
     * fails in the direction of a guard convicting the wrong line -- which is
     * how a guard gets switched off. This walks the characters instead.
     */
    static String blankComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';

            if (c == '/' && next == '/') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (c == '/' && next == '*') {
                while (i < source.length()
                        && !(source.charAt(i) == '*' && i + 1 < source.length()
                                && source.charAt(i + 1) == '/')) {
                    out.append(source.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                out.append("  ");
                i = Math.min(i + 2, source.length());
            } else if (c == '"' || c == '\'') {
                char quote = c;
                out.append(c);
                i++;
                while (i < source.length()) {
                    char s = source.charAt(i);
                    out.append(s);
                    i++;
                    if (s == '\\' && i < source.length()) {
                        out.append(source.charAt(i));
                        i++;
                    } else if (s == quote) {
                        break;
                    }
                }
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }
}

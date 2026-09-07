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

    /**
     * Every production class in the application package.
     *
     * <p>Read once and held. Five guard classes call this between fifteen and
     * twenty times per run, and each call walked 122 files, read half a megabyte
     * and ran a character-by-character comment blanker over it. Source cannot
     * change while the JVM is running, so re-reading it proved nothing and cost
     * a measurable slice of a fast lane that is already at its budget.
     */
    public static List<MainClassSource> all() {
        List<MainClassSource> cached = ALL;
        if (cached == null) {
            synchronized (MainSourceInventory.class) {
                cached = ALL;
                if (cached == null) {
                    cached = read();
                    ALL = cached;
                }
            }
        }
        return cached;
    }

    /** Every class in one layer. Derived from the cached read, not a fresh one. */
    public static List<MainClassSource> inLayer(Layer layer) {
        return BY_LAYER.computeIfAbsent(layer,
                l -> all().stream().filter(c -> c.layer() == l).toList());
    }

    /**
     * Entities and DTOs together -- the "data types" three separate guards were
     * each assembling by hand, with the same two {@code addAll} calls and the
     * same floor. One definition, so a change to what counts as a data type is
     * one edit.
     */
    public static List<MainClassSource> dataTypes() {
        List<MainClassSource> both = new ArrayList<>(inLayer(Layer.ENTITY));
        both.addAll(inLayer(Layer.DTO));
        return List.copyOf(both);
    }

    private static volatile List<MainClassSource> ALL;

    private static final java.util.Map<Layer, List<MainClassSource>> BY_LAYER =
            new java.util.concurrent.ConcurrentHashMap<>();

    private static List<MainClassSource> read() {
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
    public static String blankComments(String source) {
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
            } else if (c == '"' && i + 2 < source.length()
                    && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                // A TEXT BLOCK. Copied verbatim through its closing delimiter.
                //
                // Without this branch the walker reads `"""` as an empty string
                // followed by an opening quote, so a text block containing an
                // ODD number of quote characters leaves the string state
                // inverted for the rest of the file -- and every `//` comment
                // after it is then treated as string content and survives
                // blanking. Five guards read this output as code, so a word in
                // a comment becomes a conviction: an `@Transactional` mentioned
                // in prose reads as a declared transaction boundary.
                //
                // Latent rather than live when this was added: both files that
                // use text blocks today happen to balance their quotes, and the
                // blanked output of all 201 sources was verified byte-identical
                // before and after this branch (2026-09-07). It is the next text
                // block that would have paid for it.
                out.append("\"\"\"");
                i += 3;
                while (i < source.length()) {
                    if (source.charAt(i) == '\\' && i + 1 < source.length()) {
                        out.append(source.charAt(i)).append(source.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    if (source.charAt(i) == '"' && i + 2 < source.length()
                            && source.charAt(i + 1) == '"' && source.charAt(i + 2) == '"') {
                        out.append("\"\"\"");
                        i += 3;
                        break;
                    }
                    out.append(source.charAt(i));
                    i++;
                }
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

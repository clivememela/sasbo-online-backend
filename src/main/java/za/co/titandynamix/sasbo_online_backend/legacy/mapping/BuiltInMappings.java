package za.co.titandynamix.sasbo_online_backend.legacy.mapping;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The mappings that ship with the application -- resources under {@code migration/mappings/}, read-only, each a
 * provisional layout until the export's real one is known. A built-in that does not parse stops whoever loads it, naming
 * the file: a broken mapping must never be offered for a move.
 */
public final class BuiltInMappings {

    public static final String LOCATION = "classpath*:migration/mappings/*.json";

    private BuiltInMappings() {
    }

    /** Every built-in mapping, by key, in the order of their keys. */
    public static Map<String, MappingDefinition> load(MappingParser parser) {
        Map<String, MappingDefinition> mappings = new TreeMap<>();
        try {
            for (Resource resource : new PathMatchingResourcePatternResolver().getResources(LOCATION)) {
                String text;
                try (InputStream stream = resource.getInputStream()) {
                    text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                MappingDefinition mapping;
                try {
                    mapping = parser.parse(text);
                } catch (MappingInvalid invalid) {
                    throw new IllegalStateException("The built-in mapping " + resource.getFilename() + " does not parse: "
                            + String.join("; ", invalid.problems()), invalid);
                }
                if (mappings.put(mapping.key(), mapping) != null) {
                    throw new IllegalStateException("Two built-in mappings have the key " + mapping.key());
                }
            }
        } catch (IOException unreadable) {
            throw new UncheckedIOException("The built-in mappings could not be read", unreadable);
        }
        return Collections.unmodifiableMap(mappings);
    }
}

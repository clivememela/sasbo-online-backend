package za.co.titandynamix.sasbo_online_backend.payroll;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The layouts of SASBO's employer files ({@value #RESOURCE}), read once. An employer's profile keeps a copy of its
 * layout; these are what the seeder writes there, and the layout typed rows are read by.
 */
public final class PayrollLayouts {

    /** The layouts resource. */
    public static final String RESOURCE = "payroll/layouts.json";

    /** The layout typed rows are read by (PAY-03: GBS's PDF, SA Reserve Bank's picture). */
    public static final String TYPED = "typed";

    /** The layout that finds every field by the vocabulary alone. */
    public static final String GENERAL = "general";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, PayrollLayout> LAYOUTS = read();

    private PayrollLayouts() {
    }

    public static Optional<PayrollLayout> byKey(String key) {
        return Optional.ofNullable(LAYOUTS.get(key));
    }

    public static PayrollLayout typed() {
        return LAYOUTS.get(TYPED);
    }

    public static Map<String, PayrollLayout> all() {
        return LAYOUTS;
    }

    private static Map<String, PayrollLayout> read() {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = JSON.readTree(stream);
            Map<String, PayrollLayout> layouts = new LinkedHashMap<>();
            for (JsonNode node : root.path("layouts")) {
                PayrollLayout layout = PayrollLayout.fromNode(node);
                layouts.put(layout.key(), layout);
            }
            if (!layouts.containsKey(TYPED) || !layouts.containsKey(GENERAL)) {
                throw new IllegalStateException(RESOURCE + " lacks the typed or the general layout");
            }
            return Collections.unmodifiableMap(layouts);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("the payroll layouts could not be read", unreadable);
        }
    }

    /** A column mapping as stored: field to heading as printed. Empty for none. */
    public static Map<String, String> mapping(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException unreadable) {
            return Map.of();
        }
    }

    /** A column mapping as it is stored; {@code null} for none. */
    public static String mappingJson(Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return null;
        }
        Map<String, String> kept = new LinkedHashMap<>();
        mapping.forEach((field, heading) -> {
            if (heading != null && !heading.isBlank()) {
                kept.put(field, heading.trim());
            }
        });
        try {
            return kept.isEmpty() ? null : JSON.writeValueAsString(kept);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A list as stored, as JSON. */
    public static String listJson(java.util.List<String> values) {
        try {
            return JSON.writeValueAsString(values);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** A stored JSON list; empty when there is none. */
    public static java.util.List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return java.util.List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<java.util.List<String>>() { });
        } catch (JsonProcessingException unreadable) {
            return java.util.List.of();
        }
    }
}

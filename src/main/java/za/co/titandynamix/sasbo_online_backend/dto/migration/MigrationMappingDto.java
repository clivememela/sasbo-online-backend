package za.co.titandynamix.sasbo_online_backend.dto.migration;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * One mapping and its whole definition, as JSON -- to read, or to copy and define another from. {@code version} is
 * what a change must name; {@code null} for a built-in one, which is never changed.
 */
public record MigrationMappingDto(String key, String name, boolean builtIn, Integer version, JsonNode definition) {
}

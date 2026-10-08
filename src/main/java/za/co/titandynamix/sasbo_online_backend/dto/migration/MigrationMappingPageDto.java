package za.co.titandynamix.sasbo_online_backend.dto.migration;

import java.util.List;

/** The mappings a file can be read by: the built-in ones first, then those defined here. */
public record MigrationMappingPageDto(List<Summary> items, int page, int size, long total) {

    /** A mapping, without its definition. {@code version} and {@code updatedAt} are {@code null} for a built-in one. */
    public record Summary(String key, String name, String description, String format, boolean builtIn, Integer version,
                          String updatedBy, String updatedAt) {
    }
}

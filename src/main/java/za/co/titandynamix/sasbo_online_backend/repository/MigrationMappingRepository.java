package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The mapping definitions defined here (V80) -- the built-in ones are resources and never rows. A change names the
 * version it was made to, and the statement refuses it where someone else's change came first.
 */
@Repository
public class MigrationMappingRepository {

    /** A mapping as stored. */
    public record Saved(String key, String name, String definition, int version, String createdBy, Instant createdAt,
                        String updatedBy, Instant updatedAt) {
    }

    private static final String COLUMNS = "mapping_key, name, definition, version, created_by, created_at, updated_by,"
            + " updated_at";

    private final NamedParameterJdbcTemplate jdbc;

    public MigrationMappingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Saved> find(String key) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_mappings WHERE mapping_key = :key",
                new MapSqlParameterSource("key", key), MigrationMappingRepository::saved).stream().findFirst();
    }

    /** Every mapping defined here, by key -- a list kept by people, at most a few dozen; bounded all the same. */
    public List<Saved> all(int limit) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_mappings ORDER BY mapping_key LIMIT :limit",
                new MapSqlParameterSource("limit", limit), MigrationMappingRepository::saved);
    }

    public long count() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM migration_mappings", new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    /** A new mapping, version 1. The unique key refuses a second of the same key where two are defined at once. */
    public void insert(String key, String name, String definition, String actor, Instant now) {
        jdbc.update("""
                INSERT INTO migration_mappings (mapping_key, name, definition, version, created_by, created_at,
                  updated_by, updated_at)
                VALUES (:key, :name, :definition, 1, :actor, :now, :actor, :now)
                """, new MapSqlParameterSource("key", key).addValue("name", name).addValue("definition", definition)
                .addValue("actor", actor).addValue("now", utc(now)));
    }

    /** A change to the version it was made to. False where that is no longer the version on record. */
    public boolean update(String key, int version, String name, String definition, String actor, Instant now) {
        return jdbc.update("""
                UPDATE migration_mappings SET name = :name, definition = :definition, version = version + 1,
                  updated_by = :actor, updated_at = :now
                WHERE mapping_key = :key AND version = :version
                """, new MapSqlParameterSource("key", key).addValue("version", version).addValue("name", name)
                .addValue("definition", definition).addValue("actor", actor).addValue("now", utc(now))) == 1;
    }

    private static Saved saved(ResultSet rs, int n) throws SQLException {
        return new Saved(rs.getString("mapping_key"), rs.getString("name"), rs.getString("definition"),
                rs.getInt("version"), rs.getString("created_by"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(), rs.getString("updated_by"),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

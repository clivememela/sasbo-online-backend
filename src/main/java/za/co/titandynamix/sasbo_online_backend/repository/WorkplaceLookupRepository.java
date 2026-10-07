package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * One workplace, and WHERE it is -- its area and that area's region -- by its id or by its R/A number (October build).
 * What a caller's region or area is held to: the members listed at a workplace, and the visits captured to one, are
 * the caller's to see only where {@code CallerScope.allows(area, region)}. One statement each, straight into a row.
 */
@Repository
public class WorkplaceLookupRepository {

    private static final String SELECT = "SELECT w.id, w.ra_number, w.name, a.letter AS area, r.code AS region"
            + " FROM workplaces w"
            + " JOIN sasbo_areas a ON a.id = w.area_id"
            + " LEFT JOIN sasbo_regions r ON r.id = a.region_id";

    private final NamedParameterJdbcTemplate jdbc;

    public WorkplaceLookupRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A workplace and where it is.
     *
     * @param area   its area's letter
     * @param region its area's region code, or {@code null} for an area no region claims
     */
    public record Place(long id, int raNumber, String name, String area, String region) {
    }

    public Optional<Place> byId(long id) {
        return jdbc.query(SELECT + " WHERE w.id = :id", new MapSqlParameterSource("id", id), (rs, n) -> place(rs))
                .stream().findFirst();
    }

    /** The workplaces of the ids asked for, and where each is -- a batch print's branches, in ONE statement. */
    public java.util.List<Place> byIds(java.util.Collection<Long> ids) {
        if (ids.isEmpty()) {
            return java.util.List.of();
        }
        return jdbc.query(SELECT + " WHERE w.id IN (:ids)", new MapSqlParameterSource("ids", ids), (rs, n) -> place(rs));
    }

    public Optional<Place> byRaNumber(int raNumber) {
        return jdbc.query(SELECT + " WHERE w.ra_number = :number", new MapSqlParameterSource("number", raNumber),
                (rs, n) -> place(rs)).stream().findFirst();
    }

    private static Place place(ResultSet rs) throws SQLException {
        return new Place(rs.getLong("id"), rs.getInt("ra_number"), rs.getString("name"), rs.getString("area"),
                rs.getString("region"));
    }
}

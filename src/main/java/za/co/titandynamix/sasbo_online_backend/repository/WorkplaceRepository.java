package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceDto;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads SASBO's workplaces a page at a time (01.2).
 *
 * <h2>One statement a page</h2>
 * The page and the number of rows it is a page of come back from ONE statement -- {@code count(*) OVER ()} beside the
 * rows -- so a page costs one round trip and its total cannot disagree with its rows. The rows are answered straight
 * into their DTO, with their lineage joined in the same statement: no entity is loaded, and no association is read
 * later from a closed session.
 *
 * <h2>The order is the server's (MR-SEC-22)</h2>
 * The workplace's name, then its R/A number -- the tie-breaker, because names repeat across employers, so two pages
 * never share a row or skip one. No request names a column: every fragment of the statement is written here, and a
 * filter only chooses which fixed fragments are joined in.
 *
 * <h2>A search is literal</h2>
 * A term is escaped as ACB's member search escapes its own, with {@code !}: a {@code %} or an {@code _} typed is only
 * itself. It finds a workplace by its name, or by its R/A number where the term is a number. Both databases accept
 * every statement here (BR65).
 */
@Repository
public class WorkplaceRepository {

    /** What a workplace is answered with: itself, and its lineage. */
    private static final String COLUMNS = "w.id, w.ra_number, w.name, w.employer_printed, e.id AS employer_id,"
            + " e.name AS employer, g.name AS group_name, t.code AS table_code, w.table_code_by, a.letter AS area,"
            + " r.code AS region, w.committee_code, c.name AS committee_name, w.staff_complement, w.closed_on";

    private static final String FROM = " FROM workplaces w"
            + " JOIN sasbo_areas a ON a.id = w.area_id"
            + " LEFT JOIN sasbo_regions r ON r.id = a.region_id"
            + " LEFT JOIN table_codes t ON t.id = w.table_code_id"
            + " LEFT JOIN employers e ON e.id = t.employer_id"
            + " LEFT JOIN employer_groups g ON g.id = e.group_id"
            + " LEFT JOIN committees c ON c.id = w.committee_id";

    private final NamedParameterJdbcTemplate jdbc;

    public WorkplaceRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** What a page may be narrowed by. Every field is optional. {@code search} arrives trimmed, 2 to 100 characters. */
    public record Filter(Long employerId, Integer tableCode, String area, String committee, String search) {
    }

    /** A page, and how many rows it is a page of. The total is 0 where the page came back empty. */
    public record Slice(List<WorkplaceDto> rows, long total) {
    }

    /**
     * One page, in the server's order. {@code withComplement} decides whether the staff complement is answered: only
     * where the switch says {@code all} (MD62).
     */
    public Slice page(Filter filter, int page, int size, boolean withComplement) {
        MapSqlParameterSource args = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (filter.employerId() != null) {
            where.add("e.id = :employer");
            args.addValue("employer", filter.employerId());
        }
        if (filter.tableCode() != null) {
            where.add("t.code = :tableCode");
            args.addValue("tableCode", filter.tableCode());
        }
        if (filter.area() != null) {
            where.add("a.letter = :area");
            args.addValue("area", filter.area());
        }
        if (filter.committee() != null) {
            where.add("w.committee_code = :committee");
            args.addValue("committee", filter.committee());
        }
        if (filter.search() != null) {
            String term = filter.search();
            if (term.chars().allMatch(Character::isDigit) && term.length() <= 9) {
                where.add("(lower(w.name) LIKE :pattern ESCAPE '!' OR w.ra_number = :number)");
                args.addValue("number", Integer.parseInt(term));
            } else {
                where.add("lower(w.name) LIKE :pattern ESCAPE '!'");
            }
            args.addValue("pattern", like(term));
        }
        args.addValue("limit", size);
        args.addValue("offset", (long) page * size);
        String sql = "SELECT " + COLUMNS + ", count(*) OVER () AS total"
                + FROM
                + (where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where))
                + " ORDER BY w.name, w.ra_number"
                + " LIMIT :limit OFFSET :offset";
        long[] total = {0};
        List<WorkplaceDto> rows = jdbc.query(sql, args, (rs, n) -> {
            total[0] = rs.getLong("total");
            return row(rs, withComplement);
        });
        return new Slice(rows, total[0]);
    }

    /**
     * One workplace, with its lineage, by its id on record -- the lineage a member's answer carries (01.3). Empty where
     * there is no such workplace. The same statement and the same row as a page's: one shape, read one way.
     */
    public Optional<WorkplaceDto> one(long id, boolean withComplement) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE w.id = :id",
                new MapSqlParameterSource("id", id), (rs, n) -> row(rs, withComplement)).stream().findFirst();
    }

    private static WorkplaceDto row(ResultSet rs, boolean withComplement) throws SQLException {
        long employerId = rs.getLong("employer_id");
        boolean noEmployer = rs.wasNull();
        int tableCode = rs.getInt("table_code");
        boolean noTableCode = rs.wasNull();
        Date closed = rs.getDate("closed_on");
        return new WorkplaceDto(
                rs.getLong("id"),
                rs.getInt("ra_number"),
                rs.getString("name"),
                rs.getString("employer_printed"),
                noEmployer ? null : employerId,
                rs.getString("employer"),
                rs.getString("group_name"),
                noTableCode ? null : tableCode,
                rs.getString("table_code_by"),
                rs.getString("area"),
                rs.getString("region"),
                rs.getString("committee_code"),
                rs.getString("committee_name"),
                withComplement ? rs.getInt("staff_complement") : null,
                closed == null ? null : closed.toLocalDate().toString());
    }

    /** How many workplaces are on record -- asked only to say why a page came back empty. */
    public long count() {
        Long found = jdbc.getJdbcTemplate().queryForObject("SELECT count(*) FROM workplaces", Long.class);
        return found == null ? 0 : found;
    }

    /** A literal pattern: lower-cased, its {@code !}, {@code %} and {@code _} escaped with {@code !}, and wrapped. */
    static String like(String term) {
        String escaped = term.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }
}

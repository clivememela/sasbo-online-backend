package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.recruitment.ChannelMemberDto;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * What the Recruiters screen reads of the members, a statement at a time (RC15).
 *
 * <h2>Why it is here and not in the browser</h2>
 * The screen loaded every member through {@code GET /api/members} and counted them in JavaScript -- the whole register
 * to draw six rows, and it joined each channel to the members whose RECRUITER id happened to equal the channel's id.
 * The members recruited through a channel are those whose {@code recruitment_channel_id} is the channel's, and the
 * database counts them: one grouped statement for the figures, one paged statement for a channel's list, one bounded
 * statement for a picker. Each is served by {@code members_recruitment_channel} (V69).
 *
 * <h2>Within the caller's scope, in SQL</h2>
 * A region or area limit is a {@code WHERE} on the member's workplace's area or region (contract, permissions.md); a
 * member with no workplace is seen only by a caller who sees everything, because the inner joins drop them.
 *
 * <h2>Literal, and time-boxed</h2>
 * A typed term is escaped with {@code !} by {@link WorkplaceRepository#like} -- one rule for every search -- and every
 * statement carries a query timeout, as the figures' do (MD47). No request names a column: every fragment is written
 * here. Both databases accept every statement (H2's PostgreSQL mode in the fast lane).
 */
@Repository
public class RecruitmentChannelMembersRepository {

    /** How long any of these statements may run. */
    public static final int TIMEOUT_SECONDS = 4;

    /** What a member is listed with -- never an identity number or a bank account. */
    private static final String MEMBER_COLUMNS = "m.id, m.membership_number, m.title, m.initials, m.first_names, "
            + "m.surname, m.bank_name, m.branch, m.status";

    /** A member's name as the screen searches it: the initials, a space, the surname. */
    private static final String NAME = "lower(coalesce(m.initials, '') || ' ' || coalesce(m.surname, ''))";

    private static final String ORDER = " ORDER BY m.surname, m.initials, m.id";

    private final NamedParameterJdbcTemplate jdbc;

    public RecruitmentChannelMembersRepository(DataSource dataSource) {
        JdbcTemplate bounded = new JdbcTemplate(dataSource);
        bounded.setQueryTimeout(TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }

    /**
     * One channel's members at one bank and branch -- or, for a channel nobody in scope was recruited through, the
     * channel alone with no bank, no branch and none counted.
     */
    public record SummaryRow(long channelId, String name, boolean enabled, String bankName, String branch, long members) {
    }

    /** A page of rows, and how many rows it is a page of (0 where the page came back empty). */
    public record Slice(List<ChannelMemberDto> rows, long total) {
    }

    /**
     * Every channel, with its members grouped by bank and branch: ONE statement, however many members there are -- the
     * rows grow with the distinct banks and branches, never with the members.
     */
    public List<SummaryRow> summary(CallerScope scope) {
        MapSqlParameterSource args = new MapSqlParameterSource();
        return jdbc.query(summaryStatement(scope, args), args, (rs, n) -> new SummaryRow(rs.getLong("id"),
                rs.getString("name"), rs.getBoolean("is_enabled"), rs.getString("bank_name"), rs.getString("branch"),
                rs.getLong("member_count")));
    }

    /** The figures' one statement, its scope's values put in {@code args} -- read by the plan test as well. */
    static String summaryStatement(CallerScope scope, MapSqlParameterSource args) {
        return "SELECT c.id, c.name, c.is_enabled, s.bank_name, s.branch, s.member_count"
                + " FROM recruitment_channel c"
                + " LEFT JOIN (SELECT m.recruitment_channel_id AS channel_id, m.bank_name, m.branch,"
                + " count(*) AS member_count FROM members m" + join(scope)
                + " WHERE m.recruitment_channel_id IS NOT NULL" + where(scope, args)
                + " GROUP BY m.recruitment_channel_id, m.bank_name, m.branch) s ON s.channel_id = c.id"
                + " ORDER BY c.id";
    }

    /**
     * One page of a channel's members, in the order of their surnames. Each criterion is optional, already trimmed and
     * bounded by the service; each narrows by "contains", ignoring case, as the screen's own filters did.
     */
    public Slice members(long channelId, String number, String name, String bank, String branch, String status,
                         CallerScope scope, int page, int size) {
        MapSqlParameterSource args = new MapSqlParameterSource("channel", channelId);
        StringBuilder where = new StringBuilder(" WHERE m.recruitment_channel_id = :channel").append(where(scope, args));
        contains(where, args, "lower(m.membership_number)", "number", number);
        contains(where, args, NAME, "name", name);
        contains(where, args, "lower(m.bank_name)", "bank", bank);
        contains(where, args, "lower(m.branch)", "branch", branch);
        contains(where, args, "lower(m.status)", "status", status);
        args.addValue("limit", size);
        args.addValue("offset", (long) page * size);
        String sql = "SELECT " + MEMBER_COLUMNS + ", count(*) OVER () AS total FROM members m" + join(scope) + where
                + ORDER + " LIMIT :limit OFFSET :offset";
        long[] total = {0};
        List<ChannelMemberDto> rows = jdbc.query(sql, args, (rs, n) -> {
            total[0] = rs.getLong("total");
            return member(rs);
        });
        return new Slice(rows, total[0]);
    }

    /**
     * At most {@code limit} members for a picker: matching {@code query} by membership number, name, first names or
     * bank (none given matches everyone), and not recruited through {@code excludeChannel} when one is named.
     */
    public List<ChannelMemberDto> candidates(String query, Long excludeChannel, CallerScope scope, int limit) {
        MapSqlParameterSource args = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1").append(where(scope, args));
        if (excludeChannel != null) {
            where.append(" AND (m.recruitment_channel_id IS NULL OR m.recruitment_channel_id <> :exclude)");
            args.addValue("exclude", excludeChannel);
        }
        if (query != null) {
            where.append(" AND (lower(m.membership_number) LIKE :q ESCAPE '!' OR ").append(NAME)
                    .append(" LIKE :q ESCAPE '!' OR lower(m.first_names) LIKE :q ESCAPE '!'")
                    .append(" OR lower(m.bank_name) LIKE :q ESCAPE '!')");
            args.addValue("q", WorkplaceRepository.like(query));
        }
        args.addValue("limit", limit);
        return jdbc.query("SELECT " + MEMBER_COLUMNS + " FROM members m" + join(scope) + where + ORDER
                + " LIMIT :limit", args, (rs, n) -> member(rs));
    }

    private static void contains(StringBuilder where, MapSqlParameterSource args, String column, String name,
                                 String term) {
        if (term != null) {
            where.append(" AND ").append(column).append(" LIKE :").append(name).append(" ESCAPE '!'");
            args.addValue(name, WorkplaceRepository.like(term));
        }
    }

    /** The joins a limited scope needs: the member's workplace, its area, and for a region that area's region. */
    private static String join(CallerScope scope) {
        if (scope.isAll()) {
            return "";
        }
        String areas = " JOIN workplaces w ON w.id = m.workplace_id JOIN sasbo_areas a ON a.id = w.area_id";
        return scope.type() == CallerScope.Type.REGION ? areas + " JOIN sasbo_regions r ON r.id = a.region_id" : areas;
    }

    /** The limit itself, on the codes the caller holds (already upper-case: {@code CallerScope} normalises them). */
    private static String where(CallerScope scope, MapSqlParameterSource args) {
        if (scope.isAll()) {
            return "";
        }
        if (scope.type() == CallerScope.Type.REGION) {
            args.addValue("scope", scope.regionCodes());
            return " AND upper(r.code) IN (:scope)";
        }
        args.addValue("scope", scope.areaLetters());
        return " AND upper(a.letter) IN (:scope)";
    }

    private static ChannelMemberDto member(ResultSet rs) throws SQLException {
        return new ChannelMemberDto(rs.getInt("id"), rs.getString("membership_number"), rs.getString("title"),
                rs.getString("initials"), rs.getString("first_names"), rs.getString("surname"),
                rs.getString("bank_name"), rs.getString("branch"), rs.getString("status"));
    }
}

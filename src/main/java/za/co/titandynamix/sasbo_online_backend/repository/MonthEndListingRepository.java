package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * The members a month's listing names (month end 04.3, 04.7, 04.8), a page at a time -- read by the rules the month's
 * figures are read by (rule N for the new, rule L for the leavers, a status's kind for who is on record), and LIMITED
 * IN SQL to the caller's scope: a {@code WHERE} on the workplace's area or region, so a member outside it is never
 * read, and a member with no workplace is read only for everything (contracts/permissions.md).
 *
 * <p>Bounded as the figures are: every statement carries the figures' query timeout, and a page is at most what the
 * service asks for.
 */
@Repository
public class MonthEndListingRepository {

    /** The four listings, by their route's word. */
    public enum Kind { NEW_MEMBERS, DELETED_MEMBERS, RECRUITED, REPRESENTATIVES }

    /** One member a listing names, as read. */
    public record Listed(String membershipNumber, String initials, String surname, LocalDate joined, Instant left,
                         Integer code, String reason, Integer raNumber, String workplace, String area, String region,
                         String recruiter, boolean shopSteward, boolean committeeMember) {
    }

    private static final String PLACE = """
            LEFT JOIN workplaces w ON w.id = m.workplace_id
            LEFT JOIN sasbo_areas a ON a.id = w.area_id
            LEFT JOIN sasbo_regions r ON r.id = a.region_id
            LEFT JOIN recruiters rc ON rc.id = m.recruiter_id
            """;

    private final NamedParameterJdbcTemplate bounded;

    public MonthEndListingRepository(DataSource dataSource) {
        JdbcTemplate limited = new JdbcTemplate(dataSource);
        limited.setQueryTimeout(MemberSummaryRepository.TIMEOUT_SECONDS);
        this.bounded = new NamedParameterJdbcTemplate(limited);
    }

    /** How many members the listing names in the scope. */
    public long count(Kind kind, MonthRange month, CallerScope scope, String area, String region) {
        MapSqlParameterSource args = arguments(month);
        Long n = bounded.queryForObject("SELECT COUNT(*) " + from(kind) + where(kind, scope, area, region, args), args,
                Long.class);
        return n == null ? 0 : n;
    }

    /** One page of the listing in the scope, in its order. */
    public List<Listed> page(Kind kind, MonthRange month, CallerScope scope, String area, String region, int offset,
                             int limit) {
        MapSqlParameterSource args = arguments(month).addValue("offset", offset).addValue("limit", limit);
        String sql = "SELECT " + columns(kind) + " " + from(kind) + where(kind, scope, area, region, args)
                + " ORDER BY " + order(kind) + " LIMIT :limit OFFSET :offset";
        return bounded.query(sql, args, LISTED);
    }

    private static String columns(Kind kind) {
        String member = "m.membership_number, m.initials, m.surname, m.join_date, w.ra_number, w.name AS workplace, "
                + "a.letter AS area, r.code AS region, rc.name AS recruiter, m.is_shop_steward, m.is_bec";
        if (kind == Kind.DELETED_MEMBERS) {
            return member + ", c.changed_at AS left_at, COALESCE(p.sasbo_code, d.sasbo_code) AS code, "
                    + "CASE WHEN d.id IS NULL THEN CAST(c.reason AS VARCHAR(255)) "
                    + "ELSE CAST(COALESCE(p.name, d.name) AS VARCHAR(255)) END AS reason";
        }
        return member + ", CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS left_at, CAST(NULL AS INTEGER) AS code, "
                + "CAST(NULL AS VARCHAR(255)) AS reason";
    }

    private static String from(Kind kind) {
        if (kind == Kind.DELETED_MEMBERS) {
            // Rule L, as the month's leavers by code read it: a change INTO a leaving status in the month, by SASBO's
            // number -- A.C.B.'s SUBS_IN_ARREARS read as code 5 (MO23) -- with the reason as stored where there is none.
            return "FROM member_status_changes c JOIN members m ON m.id = c.member_id "
                    + "LEFT JOIN deletion_codes d ON d.id = c.deletion_code_id "
                    + "OR (c.deletion_code_id IS NULL AND CAST(c.reason AS VARCHAR(32)) = 'SUBS_IN_ARREARS' "
                    + "AND d.sasbo_code = " + MonthEndFactsRepository.ARREARS_DELETION_CODE + ") "
                    + "LEFT JOIN deletion_codes p ON p.id = d.parent_id " + PLACE;
        }
        return "FROM members m " + PLACE;
    }

    private static String where(Kind kind, CallerScope scope, String area, String region, MapSqlParameterSource args) {
        StringBuilder where = new StringBuilder(" WHERE ");
        switch (kind) {
            case NEW_MEMBERS -> where.append(MemberSummaryRepository.joined("m", "firstDay"));
            case RECRUITED -> where.append(MemberSummaryRepository.joined("m", "firstDay"))
                    .append(" AND m.recruiter_id IS NOT NULL");
            case DELETED_MEMBERS -> where.append("c.changed_at >= :start AND c.changed_at < :next AND ")
                    .append(MemberSummaryRepository.intoLeaving(MemberSummaryRepository.kind("c.to_status"),
                            MemberSummaryRepository.kind("c.from_status")));
            case REPRESENTATIVES -> where.append("(m.is_shop_steward = TRUE OR m.is_bec = TRUE) AND ")
                    .append(MemberSummaryRepository.kind("m.status")).append(" IN ('N', 'S')");
        }
        // The caller's scope, in SQL: a member with no workplace has no area and no region, so only everything reads them.
        if (!scope.isAll()) {
            where.append(scope.type() == CallerScope.Type.REGION ? " AND r.code IN (:scopeCodes)"
                    : " AND a.letter IN (:scopeCodes)");
            args.addValue("scopeCodes", scope.codes());
        }
        if (area != null) {
            where.append(" AND a.letter = :area");
            args.addValue("area", area);
        }
        if (region != null) {
            where.append(" AND r.code = :region");
            args.addValue("region", region);
        }
        return where.toString();
    }

    private static String order(Kind kind) {
        return switch (kind) {
            case DELETED_MEMBERS -> "c.changed_at, m.surname, m.initials, c.id";
            case RECRUITED -> "rc.name, m.surname, m.initials, m.id";
            default -> "m.surname, m.initials, m.membership_number, m.id";
        };
    }

    private static MapSqlParameterSource arguments(MonthRange month) {
        return new MapSqlParameterSource()
                .addValue("start", OffsetDateTime.ofInstant(month.start(), ZoneOffset.UTC))
                .addValue("next", OffsetDateTime.ofInstant(month.next(), ZoneOffset.UTC))
                .addValue("firstDay", month.firstDay())
                .addValue("firstDayOfNext", month.firstDayOfNext());
    }

    private static final RowMapper<Listed> LISTED = (rs, n) -> {
        OffsetDateTime left = rs.getObject("left_at", OffsetDateTime.class);
        Number code = (Number) rs.getObject("code");
        Number ra = (Number) rs.getObject("ra_number");
        return new Listed(rs.getString("membership_number"), rs.getString("initials"), rs.getString("surname"),
                rs.getObject("join_date", LocalDate.class), left == null ? null : left.toInstant(),
                code == null ? null : code.intValue(), rs.getString("reason"), ra == null ? null : ra.intValue(),
                rs.getString("workplace"), rs.getString("area"), rs.getString("region"), rs.getString("recruiter"),
                rs.getBoolean("is_shop_steward"), rs.getBoolean("is_bec"));
    };
}

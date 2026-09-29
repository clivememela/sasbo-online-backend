package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.enums.SummaryGrouping;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The figures of one month, in ONE STATEMENT (02.2, rule A, MR-ROB-18): the rows of the grouping asked for, the staff
 * complement of the workplaces in each, the statuses nobody knows, and one check row -- so that the totals a screen
 * shows are the sum of the rows the same statement returned, whatever is captured meanwhile. Never entities loaded and
 * counted.
 *
 * <h2>What each figure reads</h2>
 * <ul>
 *   <li>A member's status AT AN INSTANT is the status their first change at or after it moved them FROM -- or the one
 *       on record, where nothing has changed since. So a month's end reads the members as they stood then (rule S), and
 *       a member who joined later is not one of them;</li>
 *   <li><em>on record</em> is a status the vocabulary knows and not of kind leaving (rule V, rule X); <em>suspended</em>
 *       of kind suspended (rule U); <em>paying</em>, the difference, is worked out by the service;</li>
 *   <li><em>joined</em> is a Date Joined in the month, whatever the member's status now (rule N); <em>left</em> a change
 *       INTO a status of kind leaving in the month (rule L); <em>reinstated</em> a change out of one (rule R); the year
 *       to date is the sum of the year's months (rule Y);</li>
 *   <li>a member is placed THROUGH THEIR WORKPLACE (rule G) -- where the switch serves workplaces at all -- and one
 *       with none is a row of their own, as is one at a workplace with no table code in a grouping that needs one.</li>
 * </ul>
 *
 * <h2>Bounded</h2>
 * Every figure query carries a query timeout of {@value #TIMEOUT_SECONDS} seconds (MD47, MR-SEC-12): the read is open,
 * and the hosted database is a tenth of a processor. The statement is one both databases accept (BR65); every fragment
 * of it is written here, and a request only chooses which fixed fragments are joined in and binds its values.
 */
@Repository
public class MemberSummaryRepository {

    /** The ceiling on one figure query -- below the browser's slow notice at five seconds (ME-15). */
    public static final int TIMEOUT_SECONDS = 4;

    /** The keys of the rows that are not a grouping's own -- they sort last, and they are never guarded. */
    public static final String NO_WORKPLACE = "~NO_WORKPLACE";
    public static final String NO_TABLE_CODE = "~NO_TABLE_CODE";
    public static final String NO_EMPLOYER = "~NO_EMPLOYER";
    public static final String NO_REGION = "~NO_REGION";

    /** The parts of the statement's answer. */
    public static final String ROW = "ROW";
    public static final String COMPLEMENT = "COMPLEMENT";
    public static final String UNKNOWN_STATUS = "UNKNOWN_STATUS";
    public static final String CHECK = "CHECK";

    private final NamedParameterJdbcTemplate jdbc;

    public MemberSummaryRepository(DataSource dataSource) {
        JdbcTemplate bounded = new JdbcTemplate(dataSource);
        bounded.setQueryTimeout(TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }

    /**
     * What one request asks.
     *
     * @param grouped         whether members are placed through their workplace at all -- only where the switch
     *                        says {@code all} (MD62); otherwise every member is one row
     * @param complementServed whether the staff complement is read -- nothing made from a staff number is, below
     *                        {@code all}
     * @param seeded          the membership numbers the seed files list, to count the seed's members on record
     */
    public record Ask(MonthRange month, SummaryGrouping by, boolean grouped, boolean complementServed, String area,
                      String region, Collection<String> seeded) {
    }

    /** One line of the statement's answer: a row's figures, a row's complement, a status nobody knows, or the check. */
    public record Line(String part, String key, String label, boolean core, Integer order, long onRecord,
                       long suspended, long opening, long joined, long left, long reinstated, long joinedYearToDate,
                       long leftYearToDate, long seeded, long noTableCode, LocalDate membersFrom, long complement,
                       String status, long known, Instant leaversFrom) {
    }

    /** The one statement. */
    public List<Line> figures(Ask ask) {
        MapSqlParameterSource args = new MapSqlParameterSource()
                .addValue("start", utc(ask.month().start()))
                .addValue("next", utc(ask.month().next()))
                .addValue("yearStart", utc(ask.month().firstDayOfYear().atStartOfDay(MonthRange.ZONE).toInstant()))
                .addValue("firstDay", ask.month().firstDay())
                .addValue("firstDayOfNext", ask.month().firstDayOfNext())
                .addValue("firstDayOfYear", ask.month().firstDayOfYear())
                .addValue("seeded", ask.seeded().isEmpty() ? List.of("~none") : ask.seeded())
                .addValue("area", ask.area())
                .addValue("region", ask.region());
        return jdbc.query(statement(ask), args, MemberSummaryRepository::line);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** The statement for this request -- its fragments chosen from fixed text, nothing of the request's in it. */
    static String statement(Ask ask) {
        String workplaceJoins = """
                  LEFT JOIN table_codes t ON t.id = w.table_code_id
                  LEFT JOIN employers e ON e.id = t.employer_id
                  LEFT JOIN employer_groups g ON g.id = e.group_id
                  LEFT JOIN sasbo_areas a ON a.id = w.area_id
                  LEFT JOIN sasbo_regions r ON r.id = a.region_id
                  LEFT JOIN committees cm ON cm.id = w.committee_id""";
        String where = ask.area() != null ? " WHERE a.letter = :area" : ask.region() != null ? " WHERE r.code = :region" : "";
        String memberWorkplace = ask.grouped()
                ? "LEFT JOIN workplaces w ON w.id = m.workplace_id"
                // Below "all" no member is placed through a workplace: every one is the row of those with none.
                : "LEFT JOIN workplaces w ON 1 = 0";
        String key = key(ask.by(), ask.grouped());
        String label = ask.grouped() ? label(ask.by()) : "CAST(NULL AS VARCHAR(100))";
        String order = ask.grouped() ? order(ask.by()) : "CAST(NULL AS INTEGER)";
        String core = ask.grouped() && ask.by() == SummaryGrouping.GROUP ? "CASE WHEN g.core THEN 1 ELSE 0 END" : "0";
        String endKind = kind("end_status");
        String startKind = kind("start_status");
        String toKind = kind("c.to_status");
        String fromKind = kind("c.from_status");
        String complement = ask.complementServed() && ask.grouped() ? """
                UNION ALL
                SELECT 'COMPLEMENT', %s, MAX(%s), MAX(%s), MAX(%s), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE),
                       SUM(w.staff_complement), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS TIMESTAMP WITH TIME ZONE)
                FROM workplaces w
                %s
                %s%s
                GROUP BY %s
                """.formatted(key, label, core, order, workplaceJoins,
                        where.isEmpty() ? " WHERE " : where + " AND ", "w.closed_on IS NULL", key) : "";
        return """
                WITH after_end AS (
                  SELECT c.member_id, c.from_status FROM member_status_changes c
                  WHERE c.changed_at >= :next AND NOT EXISTS (SELECT 1 FROM member_status_changes x
                    WHERE x.member_id = c.member_id AND x.changed_at >= :next
                      AND (x.changed_at < c.changed_at OR (x.changed_at = c.changed_at AND x.id < c.id)))
                ), after_start AS (
                  SELECT c.member_id, c.from_status FROM member_status_changes c
                  WHERE c.changed_at >= :start AND NOT EXISTS (SELECT 1 FROM member_status_changes x
                    WHERE x.member_id = c.member_id AND x.changed_at >= :start
                      AND (x.changed_at < c.changed_at OR (x.changed_at = c.changed_at AND x.id < c.id)))
                ), moved AS (
                  SELECT c.member_id,
                    SUM(CASE WHEN c.changed_at >= :start AND %1$s = 'L' AND COALESCE(%2$s, '?') <> 'L' THEN 1 ELSE 0 END) AS left_n,
                    SUM(CASE WHEN c.changed_at >= :start AND %2$s = 'L' AND COALESCE(%1$s, '?') <> 'L' THEN 1 ELSE 0 END) AS reinstated_n,
                    SUM(CASE WHEN %1$s = 'L' AND COALESCE(%2$s, '?') <> 'L' THEN 1 ELSE 0 END) AS left_ytd
                  FROM member_status_changes c
                  WHERE c.changed_at >= :yearStart AND c.changed_at < :next
                  GROUP BY c.member_id
                ), placed AS (
                  SELECT %3$s AS gkey, %4$s AS glabel, %5$s AS gcore, %6$s AS gorder,
                    CASE WHEN m.join_date IS NULL OR m.join_date < :firstDayOfNext
                         THEN COALESCE(ae.from_status, m.status) END AS end_status,
                    CASE WHEN m.join_date IS NULL OR m.join_date < :firstDay
                         THEN COALESCE(ast.from_status, m.status) END AS start_status,
                    CASE WHEN m.join_date >= :firstDay AND m.join_date < :firstDayOfNext THEN 1 ELSE 0 END AS joined,
                    CASE WHEN m.join_date >= :firstDayOfYear AND m.join_date < :firstDayOfNext THEN 1 ELSE 0 END AS joined_ytd,
                    COALESCE(mv.left_n, 0) AS left_n, COALESCE(mv.reinstated_n, 0) AS reinstated_n,
                    COALESCE(mv.left_ytd, 0) AS left_ytd,
                    CASE WHEN m.membership_number IN (:seeded) THEN 1 ELSE 0 END AS seeded,
                    CASE WHEN w.id IS NOT NULL AND w.table_code_id IS NULL THEN 1 ELSE 0 END AS no_table_code,
                    m.join_date AS join_date
                  FROM members m
                  %7$s
                  %8$s
                  LEFT JOIN after_end ae ON ae.member_id = m.id
                  LEFT JOIN after_start ast ON ast.member_id = m.id
                  LEFT JOIN moved mv ON mv.member_id = m.id
                  %9$s
                )
                SELECT 'ROW' AS part, gkey AS row_key, MAX(glabel) AS label, MAX(gcore) AS core, MAX(gorder) AS row_order,
                  SUM(CASE WHEN %10$s IN ('N', 'S') THEN 1 ELSE 0 END) AS on_record,
                  SUM(CASE WHEN %10$s = 'S' THEN 1 ELSE 0 END) AS suspended,
                  SUM(CASE WHEN %11$s IN ('N', 'S') THEN 1 ELSE 0 END) AS opening,
                  SUM(joined) AS joined, SUM(left_n) AS left_n, SUM(reinstated_n) AS reinstated,
                  SUM(joined_ytd) AS joined_ytd, SUM(left_ytd) AS left_ytd,
                  SUM(CASE WHEN %10$s IN ('N', 'S') THEN seeded ELSE 0 END) AS seeded,
                  SUM(CASE WHEN %10$s IN ('N', 'S') THEN no_table_code ELSE 0 END) AS no_table_code,
                  MIN(join_date) AS members_from, 0 AS complement, CAST(NULL AS VARCHAR(100)) AS status_value, 0 AS known,
                  CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS leavers_from
                FROM placed
                GROUP BY gkey
                %12$s
                UNION ALL
                SELECT 'UNKNOWN_STATUS', CAST(NULL AS VARCHAR(100)), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE), 0,
                       end_status, COUNT(*), CAST(NULL AS TIMESTAMP WITH TIME ZONE)
                FROM placed
                WHERE end_status IS NOT NULL AND %10$s IS NULL
                GROUP BY end_status
                UNION ALL
                SELECT 'CHECK', CAST(NULL AS VARCHAR(100)), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE), 0,
                       CAST(NULL AS VARCHAR(100)),
                       %13$s,
                       (SELECT MIN(changed_at) FROM member_status_changes)
                FROM (SELECT 1 AS one) single_row
                """.formatted(toKind, fromKind, key, label, core, order, memberWorkplace, workplaceJoins, where,
                endKind, startKind, complement, known(ask));
    }

    /** Whether the area or the region a request names is on record -- counted in the same statement. */
    private static String known(Ask ask) {
        if (ask.area() != null) {
            return "(SELECT COUNT(*) FROM sasbo_areas WHERE letter = :area)";
        }
        if (ask.region() != null) {
            return "(SELECT COUNT(*) FROM sasbo_regions WHERE code = :region)";
        }
        return "1";
    }

    /** The key a member's row is found by, through the workplace (rule G). */
    static String key(SummaryGrouping by, boolean grouped) {
        if (!grouped) {
            return "'" + NO_WORKPLACE + "'";
        }
        String named = switch (by) {
            case GROUP -> "g.name";
            case EMPLOYER -> "CAST(e.ordinal AS VARCHAR(12))";
            case TABLE_CODE -> "CAST(t.code AS VARCHAR(12))";
            case AREA -> "a.letter";
            case REGION -> "r.code";
            case COMMITTEE -> "w.committee_code";
        };
        StringBuilder sql = new StringBuilder("CASE WHEN w.id IS NULL THEN '" + NO_WORKPLACE + "'");
        if (by.throughTableCode()) {
            sql.append(" WHEN t.id IS NULL THEN '").append(NO_TABLE_CODE).append("'");
        }
        if (by == SummaryGrouping.GROUP || by == SummaryGrouping.EMPLOYER) {
            sql.append(" WHEN e.id IS NULL THEN '").append(NO_EMPLOYER).append("'");
        }
        if (by == SummaryGrouping.REGION) {
            sql.append(" WHEN r.id IS NULL THEN '").append(NO_REGION).append("'");
        }
        return sql.append(" ELSE ").append(named).append(" END").toString();
    }

    private static String label(SummaryGrouping by) {
        return switch (by) {
            case GROUP -> "g.name";
            case EMPLOYER -> "e.name";
            case TABLE_CODE -> "t.name";
            case AREA -> "a.letter";
            case REGION -> "r.name";
            case COMMITTEE -> "cm.name";
        };
    }

    private static String order(SummaryGrouping by) {
        return switch (by) {
            case GROUP -> "g.ordinal";
            case EMPLOYER -> "e.ordinal";
            case TABLE_CODE -> "t.code";
            default -> "CAST(NULL AS INTEGER)";
        };
    }

    /**
     * A status's kind, as the vocabulary publishes it ({@link MemberStatuses#KINDS}) -- {@code 'N'}, {@code 'S'} or
     * {@code 'L'}, and NULL for a status nobody knows (rule X). Written from the server's own table, never typed out.
     */
    static String kind(String column) {
        StringBuilder sql = new StringBuilder("CASE");
        for (MemberStatusKind kind : MemberStatusKind.values()) {
            List<String> statuses = MemberStatuses.KINDS.entrySet().stream()
                    .filter(e -> e.getValue() == kind).map(e -> e.getKey()).toList();
            if (statuses.isEmpty()) {
                continue;
            }
            sql.append(" WHEN ").append(column).append(" IN (")
                    .append(statuses.stream().map(s -> "'" + s.replace("'", "''") + "'").collect(Collectors.joining(", ")))
                    .append(") THEN '").append(letter(kind)).append("'");
        }
        return sql.append(" END").toString();
    }

    private static String letter(MemberStatusKind kind) {
        return switch (kind) {
            case NEITHER -> "N";
            case SUSPENDED -> "S";
            case LEAVING -> "L";
        };
    }

    private static Line line(ResultSet rs, int n) throws SQLException {
        Integer order = rs.getObject("row_order") == null ? null : rs.getInt("row_order");
        Date from = rs.getDate("members_from");
        OffsetDateTime leavers = rs.getObject("leavers_from", OffsetDateTime.class);
        return new Line(rs.getString("part"), rs.getString("row_key"), rs.getString("label"), rs.getInt("core") == 1,
                order, rs.getLong("on_record"), rs.getLong("suspended"), rs.getLong("opening"), rs.getLong("joined"),
                rs.getLong("left_n"), rs.getLong("reinstated"), rs.getLong("joined_ytd"), rs.getLong("left_ytd"),
                rs.getLong("seeded"), rs.getLong("no_table_code"), from == null ? null : from.toLocalDate(),
                rs.getLong("complement"), rs.getString("status_value"), rs.getLong("known"),
                leavers == null ? null : leavers.toInstant());
    }
}

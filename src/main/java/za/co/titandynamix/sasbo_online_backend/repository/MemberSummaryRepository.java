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
 * <h2>Which members it reads (MR-PERF-10, MV-26)</h2>
 * After a migration nine members in ten are leavers, and a leaver who left before the year began and joined before it
 * counts in no figure of any month of the year. So the statement reads only the members a figure can count -- those on
 * record now, through the partial index {@code members_on_record}; those whose status changed since the year began;
 * those who joined in it -- and never the rest in sequence. What the rest still give the answer is given without
 * reading them: the rows of the groups they stand in (each a row of noughts, as the month's facts have it once taken),
 * and the earliest Date Joined of all, which the check line carries. Proved by the plan's shape on 550,000 generated
 * members ({@code MemberSummaryPlanIT}), never by a time.
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
                       String status, long known, Instant leaversFrom, Instant takenAt, String takenBy) {
    }

    /** The one statement. */
    public List<Line> figures(Ask ask) {
        return jdbc.query(statement(ask), parameters(ask), MemberSummaryRepository::line);
    }

    /**
     * The values {@link #statement} is bound with for this request. Public, with the statement, so that the proof of
     * the plan's shape (MV-26) explains exactly what a request issues -- never a copy of it typed out again.
     */
    public static MapSqlParameterSource parameters(Ask ask) {
        return new MapSqlParameterSource()
                .addValue("start", utc(ask.month().start()))
                .addValue("next", utc(ask.month().next()))
                .addValue("yearStart", utc(ask.month().firstDayOfYear().atStartOfDay(MonthRange.ZONE).toInstant()))
                .addValue("firstDay", ask.month().firstDay())
                .addValue("firstDayOfNext", ask.month().firstDayOfNext())
                .addValue("firstDayOfYear", ask.month().firstDayOfYear())
                .addValue("seeded", ask.seeded().isEmpty() ? List.of("~none") : ask.seeded())
                .addValue("area", ask.area())
                .addValue("region", ask.region());
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /**
     * The statement for this request -- its fragments chosen from fixed text, nothing of the request's in it.
     *
     * <p>RULE F (04.2): ONE statement still, whether the month is taken or not. The half that works the figures out is
     * asked only where the month has NOT been taken; the half that reads them is the month's facts -- one row a
     * workplace, with its lineage as it stood -- and the year's taken months for the year to date (rule Y). Where the
     * month is taken, the first half's one-time condition is false and PostgreSQL never runs it.
     *
     * <p>Public, with {@link #parameters}, for MV-26 alone: the plan's shape is proved on the text a request sends.
     */
    public static String statement(Ask ask) {
        String taken = "EXISTS (SELECT 1 FROM month_end_months tk WHERE tk.month_start = :firstDay "
                + "AND tk.replaces_id IS NULL)";
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
        String noTaking = ", CAST(NULL AS TIMESTAMP WITH TIME ZONE), CAST(NULL AS VARCHAR(100))";
        String complement = ask.complementServed() && ask.grouped() ? """
                UNION ALL
                SELECT 'COMPLEMENT', %s, MAX(%s), MAX(%s), MAX(%s), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE),
                       SUM(w.staff_complement), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS TIMESTAMP WITH TIME ZONE)%s
                FROM workplaces w
                %s
                %s%s AND NOT %s
                GROUP BY %s
                """.formatted(key, label, core, order, noTaking, workplaceJoins,
                        where.isEmpty() ? " WHERE " : where + " AND ", "w.closed_on IS NULL", taken, key) : "";
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
                  FROM %17$s m
                  %7$s
                  %8$s
                  LEFT JOIN after_end ae ON ae.member_id = m.id
                  LEFT JOIN after_start ast ON ast.member_id = m.id
                  LEFT JOIN moved mv ON mv.member_id = m.id
                  %9$s
                  %18$s
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
                  CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS leavers_from,
                  CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS taken_at, CAST(NULL AS VARCHAR(100)) AS taken_by
                FROM placed
                WHERE NOT %14$s
                GROUP BY gkey
                %12$s
                UNION ALL
                SELECT 'UNKNOWN_STATUS', CAST(NULL AS VARCHAR(100)), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE), 0,
                       end_status, COUNT(*), CAST(NULL AS TIMESTAMP WITH TIME ZONE)%15$s
                FROM placed
                WHERE end_status IS NOT NULL AND %10$s IS NULL AND NOT %14$s
                GROUP BY end_status
                %16$s
                UNION ALL
                SELECT 'CHECK', CAST(NULL AS VARCHAR(100)), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, %19$s, 0,
                       CAST(NULL AS VARCHAR(100)),
                       %13$s,
                       CASE WHEN %14$s
                            THEN (SELECT tk.changes_from FROM month_end_months tk
                                  WHERE tk.month_start = :firstDay AND tk.replaces_id IS NULL)
                            ELSE (SELECT MIN(changed_at) FROM member_status_changes) END,
                       (SELECT tk.taken_at FROM month_end_months tk WHERE tk.month_start = :firstDay AND tk.replaces_id IS NULL),
                       (SELECT tk.taken_by FROM month_end_months tk WHERE tk.month_start = :firstDay AND tk.replaces_id IS NULL)
                FROM (SELECT 1 AS one) single_row
                """.formatted(toKind, fromKind, key, label, core, order, memberWorkplace, workplaceJoins, where,
                endKind, startKind, complement, known(ask), taken, noTaking, takenHalf(ask, taken), counted(ask),
                present(ask, key, label, core, order, workplaceJoins), membersFrom(ask, taken));
    }

    /**
     * The members a figure can count (MR-PERF-10, MV-26), in three parts that never share a member: those on record now
     * -- the partial index {@code members_on_record}, whose predicate is word for word this leaving kind, so the planner
     * proves it -- those whose status changed since the year began ({@code member_status_changes (changed_at)}), and those
     * who joined in the year ({@code members (join_date)}). A member in none of them left before the year began and
     * joined before it, and counts nought in every figure of every month of the year; the diagnosis of 2026-09-30 held
     * the lines of the two statements equal on 550,000 members. Within an area or a region the members on record are
     * asked for through the area's workplaces, so the index is read from its leading column.
     */
    private static String counted(Ask ask) {
        String leaving = MemberStatuses.ofKind(MemberStatusKind.LEAVING).stream()
                .map(status -> "'" + status.replace("'", "''") + "'").collect(Collectors.joining(", "));
        String within = !ask.grouped() ? ""
                : ask.area() != null ? " AND m1.workplace_id IN (SELECT ws.id FROM workplaces ws"
                        + " JOIN sasbo_areas sa ON sa.id = ws.area_id WHERE sa.letter = :area)"
                : ask.region() != null ? " AND m1.workplace_id IN (SELECT ws.id FROM workplaces ws"
                        + " JOIN sasbo_areas sa ON sa.id = ws.area_id JOIN sasbo_regions sr ON sr.id = sa.region_id"
                        + " WHERE sr.code = :region)"
                : "";
        return """
                (SELECT m1.id, m1.status, m1.join_date, m1.workplace_id, m1.membership_number FROM members m1
                   WHERE m1.status NOT IN (%1$s)%2$s
                 UNION ALL
                 SELECT m2.id, m2.status, m2.join_date, m2.workplace_id, m2.membership_number FROM members m2
                   WHERE (m2.status IS NULL OR m2.status IN (%1$s))
                     AND m2.id IN (SELECT c0.member_id FROM member_status_changes c0 WHERE c0.changed_at >= :yearStart)
                 UNION ALL
                 SELECT m3.id, m3.status, m3.join_date, m3.workplace_id, m3.membership_number FROM members m3
                   WHERE (m3.status IS NULL OR m3.status IN (%1$s))
                     AND m3.join_date >= :firstDayOfYear AND m3.join_date < :firstDayOfNext
                     AND NOT EXISTS (SELECT 1 FROM member_status_changes c1
                                     WHERE c1.member_id = m3.id AND c1.changed_at >= :yearStart))""".formatted(leaving,
                within);
    }

    /**
     * The groups somebody stands in who counts in no figure are rows still -- of noughts, as they are once the month is
     * taken, whose facts are a row for every workplace anybody stands at (MD19). An open workplace's group is a row
     * through its complement, so these are the groups of the CLOSED workplaces anybody stands at, and the row of those
     * with no workplace -- each asked of an index, never by reading the leavers: the workplace's own, and the first
     * member by number with none. Below "all" the one row is there while anybody is; within an area, nobody is.
     */
    private static String present(Ask ask, String key, String label, String core, String order, String workplaceJoins) {
        String noughts = "CAST(NULL AS VARCHAR(255)), CAST(NULL AS VARCHAR(255)), 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE)";
        boolean within = ask.area() != null || ask.region() != null;
        if (!ask.grouped()) {
            return within ? "" : """
                    UNION ALL
                    SELECT %s, CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), %s
                    FROM (SELECT 1 AS one) anybody WHERE (SELECT MIN(z.id) FROM members z) IS NOT NULL"""
                    .formatted(key, noughts);
        }
        String closed = """
                UNION ALL
                SELECT %1$s, %2$s, %3$s, %4$s, %5$s
                FROM workplaces w
                %6$s
                WHERE %7$sEXISTS (SELECT 1 FROM members z WHERE z.workplace_id = w.id)%8$s"""
                .formatted(key, label, core, order, noughts, workplaceJoins,
                        ask.complementServed() ? "w.closed_on IS NOT NULL AND " : "",
                        ask.area() != null ? " AND a.letter = :area" : ask.region() != null ? " AND r.code = :region" : "");
        return within ? closed : closed + """

                UNION ALL
                SELECT '%s', CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), %s
                FROM (SELECT 1 AS one) nobody
                WHERE (SELECT MIN(z.id) FROM members z WHERE z.workplace_id IS NULL) IS NOT NULL"""
                .formatted(NO_WORKPLACE, noughts);
    }

    /**
     * The earliest Date Joined of every member in the request's reach, whoever they are now -- how far back the figures
     * can see ({@code membersFrom}) -- read from {@code members (join_date)}, or through the area's workplaces. The rows
     * no longer read the members who count in no figure, so this is not the rows' earliest; on the check line, for a
     * month worked out now. A month taken answers its facts' own.
     */
    private static String membersFrom(Ask ask, String taken) {
        String earliest;
        if (ask.area() == null && ask.region() == null) {
            earliest = "(SELECT MIN(mf.join_date) FROM members mf)";
        } else if (!ask.grouped()) {
            earliest = "CAST(NULL AS DATE)";
        } else {
            earliest = "(SELECT MIN(mf.join_date) FROM members mf JOIN workplaces wf ON wf.id = mf.workplace_id"
                    + " JOIN sasbo_areas af ON af.id = wf.area_id"
                    + (ask.area() != null ? " WHERE af.letter = :area)"
                            : " JOIN sasbo_regions rf ON rf.id = af.region_id WHERE rf.code = :region)");
        }
        return "CASE WHEN " + taken + " THEN CAST(NULL AS DATE) ELSE " + earliest + " END";
    }

    /**
     * Rule F: the figures of a TAKEN month, read from its facts in the same statement -- the rows, their complement and
     * the statuses nobody knows, each part the live half's shape. The year to date is the sum of the year's taken
     * months up to this one (rule Y), each by the lineage it was taken with (MV-30).
     */
    private static String takenHalf(Ask ask, String taken) {
        String key = factKey(ask.by(), ask.grouped());
        String label = ask.grouped() ? factLabel(ask.by()) : "CAST(NULL AS VARCHAR(100))";
        String order = ask.grouped() ? factOrder(ask.by()) : "CAST(NULL AS INTEGER)";
        String core = ask.grouped() && ask.by() == SummaryGrouping.GROUP ? "CASE WHEN f.group_core THEN 1 ELSE 0 END"
                : "0";
        // Below "all" nothing is placed by where it works, so an area or a region asked for holds nobody -- as live.
        String scope = (ask.area() != null || ask.region() != null) && !ask.grouped() ? " AND 1 = 0"
                : ask.area() != null ? " AND f.area_letter = :area"
                : ask.region() != null ? " AND f.region_code = :region" : "";
        String thisMonth = "f.month_start = :firstDay";
        String complement = ask.complementServed() && ask.grouped() ? """
                UNION ALL
                SELECT 'COMPLEMENT', %1$s, MAX(%2$s), MAX(%3$s), MAX(%4$s), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE),
                       SUM(f.complement), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS TIMESTAMP WITH TIME ZONE),
                       CAST(NULL AS TIMESTAMP WITH TIME ZONE), CAST(NULL AS VARCHAR(100))
                FROM month_end_workplaces f
                JOIN month_end_months mm ON mm.id = f.month_id AND mm.replaces_id IS NULL
                WHERE %5$s AND f.workplace_key <> 0 AND %6$s%7$s
                GROUP BY %1$s
                """.formatted(key, label, core, order, thisMonth, taken, scope) : "";
        return """
                UNION ALL
                SELECT 'ROW', %1$s, MAX(%2$s), MAX(%3$s), MAX(%4$s),
                  SUM(CASE WHEN %5$s THEN f.on_record ELSE 0 END), SUM(CASE WHEN %5$s THEN f.suspended ELSE 0 END),
                  SUM(CASE WHEN %5$s THEN f.opening ELSE 0 END), SUM(CASE WHEN %5$s THEN f.joined ELSE 0 END),
                  SUM(CASE WHEN %5$s THEN f.left_count ELSE 0 END), SUM(CASE WHEN %5$s THEN f.reinstated ELSE 0 END),
                  SUM(f.joined), SUM(f.left_count), SUM(CASE WHEN %5$s THEN f.seeded ELSE 0 END),
                  SUM(CASE WHEN %5$s AND f.workplace_key <> 0 AND f.table_code IS NULL THEN f.on_record ELSE 0 END),
                  MIN(CASE WHEN %5$s THEN f.members_from END), 0, CAST(NULL AS VARCHAR(100)), 0,
                  CAST(NULL AS TIMESTAMP WITH TIME ZONE), CAST(NULL AS TIMESTAMP WITH TIME ZONE),
                  CAST(NULL AS VARCHAR(100))
                FROM month_end_workplaces f
                JOIN month_end_months mm ON mm.id = f.month_id AND mm.replaces_id IS NULL
                WHERE f.month_start >= :firstDayOfYear AND f.month_start <= :firstDay AND %6$s%7$s
                GROUP BY %1$s
                %8$s
                UNION ALL
                SELECT 'UNKNOWN_STATUS', CAST(NULL AS VARCHAR(100)), CAST(NULL AS VARCHAR(100)), 0, CAST(NULL AS INTEGER), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(NULL AS DATE), 0,
                       c.name, SUM(c.members), CAST(NULL AS TIMESTAMP WITH TIME ZONE),
                       CAST(NULL AS TIMESTAMP WITH TIME ZONE), CAST(NULL AS VARCHAR(100))
                FROM month_end_counts c
                JOIN month_end_months mm ON mm.id = c.month_id AND mm.replaces_id IS NULL
                LEFT JOIN month_end_workplaces f ON f.month_id = c.month_id AND f.workplace_key = c.workplace_key
                WHERE c.month_start = :firstDay AND c.measure = 'UNKNOWN_STATUS' AND %6$s%7$s
                GROUP BY c.name
                """.formatted(key, label, core, order, thisMonth, taken, scope, complement);
    }

    /** {@link #key}, over a taken month's facts: the lineage as it stood. */
    static String factKey(SummaryGrouping by, boolean grouped) {
        if (!grouped) {
            // A CAST, never the bare literal: the taken half GROUPS BY this, and PostgreSQL refuses a literal there --
            // "non-integer constant in GROUP BY" -- where H2 takes it. Found by MV-26's probe, below "all" (MD62).
            return "CAST('" + NO_WORKPLACE + "' AS VARCHAR(20))";
        }
        String named = switch (by) {
            case GROUP -> "f.group_name";
            case EMPLOYER -> "CAST(f.employer_ordinal AS VARCHAR(12))";
            case TABLE_CODE -> "CAST(f.table_code AS VARCHAR(12))";
            case AREA -> "f.area_letter";
            case REGION -> "f.region_code";
            case COMMITTEE -> "f.committee_code";
        };
        StringBuilder sql = new StringBuilder("CASE WHEN f.workplace_key = 0 THEN '" + NO_WORKPLACE + "'");
        if (by.throughTableCode()) {
            sql.append(" WHEN f.table_code IS NULL THEN '").append(NO_TABLE_CODE).append("'");
        }
        if (by == SummaryGrouping.GROUP || by == SummaryGrouping.EMPLOYER) {
            sql.append(" WHEN f.employer_ordinal IS NULL THEN '").append(NO_EMPLOYER).append("'");
        }
        if (by == SummaryGrouping.REGION) {
            sql.append(" WHEN f.region_code IS NULL THEN '").append(NO_REGION).append("'");
        }
        return sql.append(" ELSE ").append(named).append(" END").toString();
    }

    private static String factLabel(SummaryGrouping by) {
        return switch (by) {
            case GROUP -> "f.group_name";
            case EMPLOYER -> "f.employer_name";
            case TABLE_CODE -> "f.table_code_name";
            case AREA -> "f.area_letter";
            case REGION -> "f.region_name";
            case COMMITTEE -> "f.committee_name";
        };
    }

    private static String factOrder(SummaryGrouping by) {
        return switch (by) {
            case GROUP -> "f.group_ordinal";
            case EMPLOYER -> "f.employer_ordinal";
            case TABLE_CODE -> "f.table_code";
            default -> "CAST(NULL AS INTEGER)";
        };
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
        OffsetDateTime takenAt = rs.getObject("taken_at", OffsetDateTime.class);
        return new Line(rs.getString("part"), rs.getString("row_key"), rs.getString("label"), rs.getInt("core") == 1,
                order, rs.getLong("on_record"), rs.getLong("suspended"), rs.getLong("opening"), rs.getLong("joined"),
                rs.getLong("left_n"), rs.getLong("reinstated"), rs.getLong("joined_ytd"), rs.getLong("left_ytd"),
                rs.getLong("seeded"), rs.getLong("no_table_code"), from == null ? null : from.toLocalDate(),
                rs.getLong("complement"), rs.getString("status_value"), rs.getLong("known"),
                leavers == null ? null : leavers.toInstant(), takenAt == null ? null : takenAt.toInstant(),
                rs.getString("taken_by"));
    }
}

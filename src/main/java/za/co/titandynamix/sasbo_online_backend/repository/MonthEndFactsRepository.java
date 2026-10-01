package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.MonthEndCount;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A month taken, and read back (month end 04.2, MD43): the month's record, then the month frozen as FACTS -- one row a
 * workplace with its lineage as it stood, then what has a grain of its own -- each by ONE statement that both
 * databases accept (BR65), written here and nowhere else. The service runs them inside one snapshot (MD64); nothing
 * here opens a transaction of its own.
 *
 * <h2>What a workplace's figures read</h2>
 * Exactly what the figures of {@link MemberSummaryRepository} read, for one month (02.2): a member's status at an
 * instant is the one their first change at or after it moved them from (rule S); <em>on record</em> a status the
 * vocabulary knows that is not of kind leaving; <em>suspended</em> of kind suspended; <em>paying</em> the rest;
 * <em>joined</em> a Date Joined in the month (rule N); <em>left</em> a change into a leaving status in the month
 * (rule L); <em>reinstated</em> a change out of one (rule R). A status's kind is written from the server's own table,
 * never typed here ({@link MemberSummaryRepository#kind}). A member is placed through their workplace (rule G).
 *
 * <p>Beside them, what the statistics read of the paying: how each pays, AS IT IS STORED and adding no way of paying
 * (BR41) -- {@code Debit Order}, {@code Payroll Deduction}, and every other value as other -- and what they pay; and of
 * everyone on record, how many are elected representatives ({@code is_shop_steward}) and committee members
 * ({@code is_bec}) -- a reading SASBO is asked to confirm.
 */
@Repository
public class MonthEndFactsRepository {

    /** How the paying pay, as the member record stores it (BR41). Anything else is other. */
    public static final String DEBIT_ORDER = "Debit Order";
    public static final String PAYROLL_DEDUCTION = "Payroll Deduction";

    /**
     * A.C.B.'s two reasons, read in SASBO's codes -- the owner's decision of 2026-10-01 (MO23), as A.C.B.'s owner: a
     * termination A.C.B. writes for {@code SUBS_IN_ARREARS}, with no deletion code of its own, is a leaver under SASBO's
     * deletion code 5, <em>Subscription in Arrears</em>; a suspension it writes for {@code UNPAID_SUSPENDED} is
     * suspended under suspension code 13, <em>Debit returned</em>. Only month end's counts read it so: A.C.B.'s own
     * code, and its count of terminations by {@code SUBS_IN_ARREARS} (BR39), are untouched. Where the code is not on
     * the list, the change is counted as before, under its reason.
     */
    static final int ARREARS_DELETION_CODE = 5;
    static final int DEBIT_RETURNED_SUSPENSION_CODE = 13;

    /** The take's: a month is taken whole, however long the database takes over it. */
    private final NamedParameterJdbcTemplate jdbc;

    /** Every read's: bounded like every figure query, below the browser's slow notice (MD47, ME-15). */
    private final NamedParameterJdbcTemplate bounded;

    public MonthEndFactsRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        JdbcTemplate limited = new JdbcTemplate(dataSource);
        limited.setQueryTimeout(MemberSummaryRepository.TIMEOUT_SECONDS);
        this.bounded = new NamedParameterJdbcTemplate(limited);
    }

    /** A taken month's record. */
    public record TakenMonth(long id, LocalDate monthStart, String takenBy, Instant takenAt, Instant changesFrom) {
    }

    // ------------------------------------------------------------------------------------------------ the take

    /**
     * The month's record -- the take's first statement, and the one the partial unique index refuses a second time
     * (MD46). The earliest status change on record is read in the same statement.
     *
     * @return the record's id
     */
    public long insertMonth(LocalDate monthStart, String takenBy, Instant takenAt) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_months (month_start, taken_by, taken_at, changes_from)
                SELECT :monthStart, :takenBy, :takenAt, MIN(changed_at) FROM member_status_changes
                """, new MapSqlParameterSource()
                .addValue("monthStart", monthStart)
                .addValue("takenBy", takenBy)
                .addValue("takenAt", utc(takenAt)), key, new String[] {"id"});
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("the month's record was written and no id came back");
        }
        return id.longValue();
    }

    /** The month frozen as facts: one row a workplace, one more for the members with none -- ONE statement. */
    public int insertFacts(long monthId, MonthRange month, Collection<String> seeded) {
        return jdbc.update("""
                INSERT INTO month_end_workplaces (month_id, month_start, workplace_key, workplace_id, ra_number,
                  table_code, table_code_name, employer_ordinal, employer_name, group_name, group_ordinal, group_core,
                  area_letter, region_code, region_name, committee_code, committee_name, complement,
                  on_record, suspended, paying, opening, joined, left_count, reinstated,
                  by_debit_order, by_deduction, by_other, debit_order_rand, deduction_rand, other_rand,
                  representatives, committee_members, seeded, members_from)
                SELECT :monthId, :firstDay, f.workplace_key, f.workplace_id, f.ra_number,
                  f.table_code, f.table_code_name, f.employer_ordinal, f.employer_name, f.group_name, f.group_ordinal,
                  f.group_core, f.area_letter, f.region_code, f.region_name, f.committee_code, f.committee_name,
                  f.complement, f.on_record, f.suspended, f.paying, f.opening, f.joined, f.left_count, f.reinstated,
                  f.by_debit_order, f.by_deduction, f.by_other, f.debit_order_rand, f.deduction_rand, f.other_rand,
                  f.representatives, f.committee_members, f.seeded, f.members_from
                FROM (""" + factsStatement() + ") f", arguments(month, seeded).addValue("monthId", monthId));
    }

    /** The month's leavers, by SASBO's deletion code and the reason under it -- one statement. */
    public int insertLeftByCode(long monthId, MonthRange month) {
        return insertCounts(monthId, month, MonthEndCount.Measure.LEFT_BY_CODE);
    }

    /** The suspended at the month's end, by the suspension code of the change that suspended them -- one statement. */
    public int insertSuspendedByCode(long monthId, MonthRange month) {
        return insertCounts(monthId, month, MonthEndCount.Measure.SUSPENDED_BY_CODE);
    }

    /** The month's recruits, by recruiter and channel -- one statement. */
    public int insertRecruited(long monthId, MonthRange month) {
        return insertCounts(monthId, month, MonthEndCount.Measure.RECRUITED_BY);
    }

    /** The members whose status at the month's end nobody knows (rule X), by the status as stored -- one statement. */
    public int insertUnknownStatuses(long monthId, MonthRange month) {
        return insertCounts(monthId, month, MonthEndCount.Measure.UNKNOWN_STATUS);
    }

    private int insertCounts(long monthId, MonthRange month, MonthEndCount.Measure measure) {
        return jdbc.update("""
                INSERT INTO month_end_counts (month_id, month_start, workplace_key, measure, code, name, detail, members)
                SELECT :monthId, :firstDay, x.workplace_key, x.measure, x.code, x.name, x.detail, x.members
                FROM (""" + countsStatement(measure) + ") x", arguments(month, List.of()).addValue("monthId", monthId));
    }

    /**
     * A count at its own grain, for one month -- the columns {@code workplace_key, measure, code, name, detail,
     * members}, one row a workplace and a value:
     * <ul>
     *   <li>{@code LEFT_BY_CODE}: the month's changes INTO a leaving status (rule L), by SASBO's number -- the code's
     *       own, or the one it is a reason under -- with the reason under it as the detail. A.C.B.'s
     *       {@code SUBS_IN_ARREARS} is read as code 5 (MO23, {@link #ARREARS_DELETION_CODE}); any other leaver with no
     *       code is counted under the reason as stored;</li>
     *   <li>{@code SUSPENDED_BY_CODE}: the suspended at the month's end, by the code of the last change into a
     *       suspended status before it ended -- A.C.B.'s {@code UNPAID_SUSPENDED} read as code 13 (MO23,
     *       {@link #DEBIT_RETURNED_SUSPENSION_CODE}). Suspended with no change on record (captured so): no code;</li>
     *   <li>{@code RECRUITED_BY}: a Date Joined in the month, whatever the status now (rule N), by recruiter and
     *       channel;</li>
     *   <li>{@code UNKNOWN_STATUS}: a status at the month's end nobody knows (rule X), as stored.</li>
     * </ul>
     * 🔴 Every text is CAST: H2 builds {@code member_status_changes.reason} from the entity as an ENUM, and a
     * {@code CASE} that mixes it with a code's name took the ENUM's type and refused the name (run 36627331294).
     */
    static String countsStatement(MonthEndCount.Measure measure) {
        String toKind = MemberSummaryRepository.kind("c.to_status");
        String fromKind = MemberSummaryRepository.kind("c.from_status");
        return switch (measure) {
            case LEFT_BY_CODE -> """
                    SELECT l.workplace_key AS workplace_key, CAST('LEFT_BY_CODE' AS VARCHAR(20)) AS measure,
                      l.code AS code, l.name AS name, l.detail AS detail, COUNT(*) AS members
                    FROM (
                      SELECT COALESCE(m.workplace_id, 0) AS workplace_key,
                        COALESCE(p.sasbo_code, d.sasbo_code) AS code,
                        CASE WHEN d.id IS NULL THEN CAST(c.reason AS VARCHAR(255))
                             ELSE CAST(COALESCE(p.name, d.name) AS VARCHAR(255)) END AS name,
                        CASE WHEN p.id IS NOT NULL THEN CAST(d.name AS VARCHAR(255)) END AS detail
                      FROM member_status_changes c
                      JOIN members m ON m.id = c.member_id
                      LEFT JOIN deletion_codes d ON d.id = c.deletion_code_id
                        OR (c.deletion_code_id IS NULL AND CAST(c.reason AS VARCHAR(32)) = 'SUBS_IN_ARREARS'
                            AND d.sasbo_code = %3$d)
                      LEFT JOIN deletion_codes p ON p.id = d.parent_id
                      WHERE c.changed_at >= :start AND c.changed_at < :next
                        AND %1$s = 'L' AND COALESCE(%2$s, '?') <> 'L'
                    ) l
                    GROUP BY l.workplace_key, l.code, l.name, l.detail
                    """.formatted(toKind, fromKind, ARREARS_DELETION_CODE);
            case SUSPENDED_BY_CODE -> """
                    SELECT s.workplace_key AS workplace_key, CAST('SUSPENDED_BY_CODE' AS VARCHAR(20)) AS measure,
                      s.code AS code, s.name AS name, CAST(NULL AS VARCHAR(255)) AS detail, COUNT(*) AS members
                    FROM (
                      SELECT COALESCE(k.workplace_id, 0) AS workplace_key, sc.code AS code,
                        CASE WHEN sc.id IS NOT NULL THEN CAST(sc.name AS VARCHAR(255))
                             ELSE CAST(last_in.reason AS VARCHAR(255)) END AS name
                      FROM (%1$s) k
                      LEFT JOIN (
                        SELECT c.member_id, c.suspension_code_id, c.reason FROM member_status_changes c
                        WHERE c.changed_at < :next AND %2$s = 'S'
                          AND NOT EXISTS (SELECT 1 FROM member_status_changes y WHERE y.member_id = c.member_id
                            AND y.changed_at < :next AND %3$s = 'S'
                            AND (y.changed_at > c.changed_at OR (y.changed_at = c.changed_at AND y.id > c.id)))
                      ) last_in ON last_in.member_id = k.member_id
                      LEFT JOIN suspension_codes sc ON sc.id = last_in.suspension_code_id
                        OR (last_in.suspension_code_id IS NULL
                            AND CAST(last_in.reason AS VARCHAR(32)) = 'UNPAID_SUSPENDED' AND sc.code = %4$d)
                      WHERE k.end_kind = 'S'
                    ) s
                    GROUP BY s.workplace_key, s.code, s.name
                    """.formatted(kinds(), toKind, MemberSummaryRepository.kind("y.to_status"),
                    DEBIT_RETURNED_SUSPENSION_CODE);
            case RECRUITED_BY -> """
                    SELECT r.workplace_key AS workplace_key, CAST('RECRUITED_BY' AS VARCHAR(20)) AS measure,
                      r.code AS code, r.name AS name, r.detail AS detail, COUNT(*) AS members
                    FROM (
                      SELECT COALESCE(m.workplace_id, 0) AS workplace_key, m.recruiter_id AS code,
                        CAST(rc.name AS VARCHAR(255)) AS name, CAST(ch.name AS VARCHAR(255)) AS detail
                      FROM members m
                      LEFT JOIN recruiters rc ON rc.id = m.recruiter_id
                      LEFT JOIN recruitment_channel ch ON ch.id = m.recruitment_channel_id
                      WHERE m.join_date >= :firstDay AND m.join_date < :firstDayOfNext
                    ) r
                    GROUP BY r.workplace_key, r.code, r.name, r.detail
                    """;
            case UNKNOWN_STATUS -> """
                    SELECT u.workplace_key AS workplace_key, CAST('UNKNOWN_STATUS' AS VARCHAR(20)) AS measure,
                      CAST(NULL AS INTEGER) AS code, u.name AS name, CAST(NULL AS VARCHAR(255)) AS detail,
                      COUNT(*) AS members
                    FROM (
                      SELECT COALESCE(k.workplace_id, 0) AS workplace_key, CAST(k.end_status AS VARCHAR(255)) AS name
                      FROM (%s) k
                      WHERE k.end_status IS NOT NULL AND k.end_kind IS NULL
                    ) u
                    GROUP BY u.workplace_key, u.name
                    """.formatted(kinds());
        };
    }

    // ------------------------------------------------------------------------------------------------ the open month

    /**
     * The open month's rows, worked out now by the very statement a take freezes -- so a statistic of the open month
     * and the same statistic of the month once taken are read the same way (MD19). Bounded like every figure query.
     */
    public List<Fact> liveFacts(MonthRange month, Collection<String> seeded) {
        return bounded.query("SELECT f.* FROM (" + factsStatement() + ") f ORDER BY f.workplace_key",
                arguments(month, seeded), MonthEndFactsRepository::fact);
    }

    /** The open month's counts, every grain, in one statement. */
    public List<Count> liveCounts(MonthRange month) {
        String all = String.join(" UNION ALL ", countsStatement(MonthEndCount.Measure.LEFT_BY_CODE),
                countsStatement(MonthEndCount.Measure.SUSPENDED_BY_CODE),
                countsStatement(MonthEndCount.Measure.RECRUITED_BY),
                countsStatement(MonthEndCount.Measure.UNKNOWN_STATUS));
        return bounded.query("SELECT x.* FROM (" + all + ") x ORDER BY x.workplace_key, x.measure",
                arguments(month, List.of()), MonthEndFactsRepository::count);
    }

    // ------------------------------------------------------------------------------------------------ the year to date

    /**
     * The movement of a year's taken months from {@code yearStart} up to, not including, {@code until}: joined and
     * left, summed by the lineage each month stood in -- the year to date is the sum of the taken months (rule Y), and
     * a month is read as it was taken (MV-30).
     */
    public record YearMovement(boolean noWorkplace, String areaLetter, String regionCode, String regionName,
                               Boolean groupCore, Integer tableCode, String tableCodeName, long joined, long left) {
    }

    /** {@link YearMovement}'s rows, in one statement. */
    public List<YearMovement> yearMovement(LocalDate yearStart, LocalDate until) {
        return bounded.query("""
                SELECT CASE WHEN f.workplace_key = 0 THEN 1 ELSE 0 END AS no_workplace, f.area_letter, f.region_code,
                  f.region_name, f.group_core, f.table_code, f.table_code_name, SUM(f.joined) AS joined,
                  SUM(f.left_count) AS left_count
                FROM month_end_workplaces f
                JOIN month_end_months mm ON mm.id = f.month_id AND mm.replaces_id IS NULL
                WHERE f.month_start >= :yearStart AND f.month_start < :until
                GROUP BY CASE WHEN f.workplace_key = 0 THEN 1 ELSE 0 END, f.area_letter, f.region_code, f.region_name,
                  f.group_core, f.table_code, f.table_code_name
                """, new MapSqlParameterSource("yearStart", yearStart).addValue("until", until), (rs, n) ->
                new YearMovement(rs.getInt("no_workplace") == 1, rs.getString("area_letter"), rs.getString("region_code"),
                        rs.getString("region_name"), orNull(rs, rs.getBoolean("group_core")), orNull(rs, rs.getInt("table_code")),
                        rs.getString("table_code_name"), rs.getLong("joined"), rs.getLong("left_count")));
    }

    /** The recruits of a year's taken months before {@code until}, by recruiter and channel and the lineage they stood in. */
    public record YearRecruits(Integer code, String name, String detail, boolean noWorkplace, String areaLetter,
                               String regionCode, Boolean groupCore, Integer tableCode, String tableCodeName,
                               long members) {
    }

    /** {@link YearRecruits}' rows, in one statement. */
    public List<YearRecruits> yearRecruits(LocalDate yearStart, LocalDate until) {
        return bounded.query("""
                SELECT c.code, c.name, c.detail, CASE WHEN c.workplace_key = 0 THEN 1 ELSE 0 END AS no_workplace,
                  f.area_letter, f.region_code, f.group_core, f.table_code, f.table_code_name, SUM(c.members) AS members
                FROM month_end_counts c
                JOIN month_end_months mm ON mm.id = c.month_id AND mm.replaces_id IS NULL
                LEFT JOIN month_end_workplaces f ON f.month_id = c.month_id AND f.workplace_key = c.workplace_key
                WHERE c.measure = 'RECRUITED_BY' AND c.month_start >= :yearStart AND c.month_start < :until
                GROUP BY c.code, c.name, c.detail, CASE WHEN c.workplace_key = 0 THEN 1 ELSE 0 END, f.area_letter,
                  f.region_code, f.group_core, f.table_code, f.table_code_name
                """, new MapSqlParameterSource("yearStart", yearStart).addValue("until", until), (rs, n) ->
                new YearRecruits(orNull(rs, rs.getInt("code")), rs.getString("name"), rs.getString("detail"),
                        rs.getInt("no_workplace") == 1, rs.getString("area_letter"), rs.getString("region_code"),
                        orNull(rs, rs.getBoolean("group_core")), orNull(rs, rs.getInt("table_code")),
                        rs.getString("table_code_name"), rs.getLong("members")));
    }

    /** How many members were captured with a Date Joined in the month -- as the records stand now. */
    public long joinedNow(MonthRange month) {
        Long n = bounded.queryForObject("SELECT COUNT(*) FROM members WHERE join_date >= :firstDay "
                + "AND join_date < :firstDayOfNext", arguments(month, List.of()), Long.class);
        return n == null ? 0 : n;
    }

    /** Whether an area letter is on record. */
    public boolean areaOnRecord(String letter) {
        Long n = bounded.queryForObject("SELECT COUNT(*) FROM sasbo_areas WHERE letter = :letter",
                new MapSqlParameterSource("letter", letter), Long.class);
        return n != null && n > 0;
    }

    /** A workplace's name, and its last visit by a month's end -- the first day of that visit's month. */
    public record LastVisit(String name, LocalDate lastVisited) {
    }

    /**
     * 04.9: every workplace's name and its newest visit before the month ends (A.1) -- by workplace id, one statement.
     * For a TAKEN month, only the visits on record when it was taken ({@code captured_at} at or before the take), so the
     * month reads as it stood then (MD19): the visits are append-only, and nothing captured later moves it. A visit's
     * day is its month here: SASBO's own history prints a month and no day.
     */
    public Map<Long, LastVisit> lastVisits(MonthRange month, Instant takenAt) {
        Map<Long, LastVisit> found = new HashMap<>();
        bounded.query("""
                SELECT w.id AS id, w.name AS name, MAX(v.visited_on) AS last_visited
                FROM workplaces w
                LEFT JOIN workplace_visits v ON v.workplace_id = w.id AND v.visited_on < :firstDayOfNext
                  AND (CAST(:takenAt AS TIMESTAMP WITH TIME ZONE) IS NULL OR v.captured_at <= :takenAt)
                GROUP BY w.id, w.name
                """, arguments(month, List.of()).addValue("takenAt", takenAt == null ? null : utc(takenAt)), rs -> {
            Date last = rs.getDate("last_visited");
            found.put(rs.getLong("id"), new LastVisit(rs.getString("name"),
                    last == null ? null : last.toLocalDate().withDayOfMonth(1)));
        });
        return found;
    }

    /** Whether a region code is on record. */
    public boolean regionOnRecord(String code) {
        Long n = bounded.queryForObject("SELECT COUNT(*) FROM sasbo_regions WHERE code = :code",
                new MapSqlParameterSource("code", code), Long.class);
        return n != null && n > 0;
    }

    // ------------------------------------------------------------------------------------------------ reads

    /** The month's record, where it has been taken (the first take; nothing replaces one yet). */
    public Optional<TakenMonth> taken(LocalDate monthStart) {
        return jdbc.query("""
                SELECT id, month_start, taken_by, taken_at, changes_from FROM month_end_months
                WHERE month_start = :monthStart AND replaces_id IS NULL
                """, new MapSqlParameterSource("monthStart", monthStart), MonthEndFactsRepository::takenMonth)
                .stream().findFirst();
    }

    /** Every month taken, the newest first. */
    public List<TakenMonth> takenMonths() {
        return jdbc.query("""
                SELECT id, month_start, taken_by, taken_at, changes_from FROM month_end_months
                WHERE replaces_id IS NULL ORDER BY month_start DESC
                """, new MapSqlParameterSource(), MonthEndFactsRepository::takenMonth);
    }

    /** How many workplaces are on record -- what a month needs before it can be taken (MD54). */
    public long workplacesOnRecord() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM workplaces", new MapSqlParameterSource(), Long.class);
        return n == null ? 0 : n;
    }

    /** One workplace's row of a month -- frozen, or the open month's worked out now. */
    public record Fact(long workplaceKey, Long workplaceId, Integer raNumber, Integer tableCode, String tableCodeName,
                       Integer employerOrdinal, String employerName, String groupName, Integer groupOrdinal,
                       Boolean groupCore, String areaLetter, String regionCode, String regionName,
                       String committeeCode, String committeeName, Integer complement, long onRecord, long suspended,
                       long paying, long opening, long joined, long left, long reinstated, long byDebitOrder,
                       long byDeduction, long byOther, BigDecimal debitOrderRand, BigDecimal deductionRand,
                       BigDecimal otherRand, long representatives, long committeeMembers, long seeded,
                       LocalDate membersFrom) {
    }

    /** One count of a month, at its own grain. */
    public record Count(long workplaceKey, MonthEndCount.Measure measure, Integer code, String name, String detail,
                        long members) {
    }

    /** A taken month's rows, by the workplace. */
    public List<Fact> facts(long monthId) {
        return bounded.query("""
                SELECT workplace_key, workplace_id, ra_number, table_code, table_code_name, employer_ordinal,
                  employer_name, group_name, group_ordinal, group_core, area_letter, region_code, region_name,
                  committee_code, committee_name, complement, on_record, suspended, paying, opening, joined, left_count,
                  reinstated, by_debit_order, by_deduction, by_other, debit_order_rand, deduction_rand, other_rand,
                  representatives, committee_members, seeded, members_from
                FROM month_end_workplaces WHERE month_id = :monthId ORDER BY workplace_key
                """, new MapSqlParameterSource("monthId", monthId), MonthEndFactsRepository::fact);
    }

    /** A taken month's counts. */
    public List<Count> counts(long monthId) {
        return bounded.query("""
                SELECT workplace_key, measure, code, name, detail, members FROM month_end_counts
                WHERE month_id = :monthId ORDER BY workplace_key, measure, code, name, detail
                """, new MapSqlParameterSource("monthId", monthId), MonthEndFactsRepository::count);
    }

    // ------------------------------------------------------------------------------------------------ the statement

    /**
     * The month's rows, one a workplace: the members' figures, and a row of noughts for every open workplace so that
     * a workplace with nobody on record is still a row with its complement -- summed a workplace, with its lineage.
     * The members with no workplace are the row whose workplace is NULL, key 0. A closed workplace keeps its members
     * and has no complement.
     */
    static String factsStatement() {
        return """
                SELECT COALESCE(p.workplace_id, 0) AS workplace_key, p.workplace_id AS workplace_id,
                  w.ra_number AS ra_number, t.code AS table_code, t.name AS table_code_name,
                  e.ordinal AS employer_ordinal, e.name AS employer_name, g.name AS group_name,
                  g.ordinal AS group_ordinal, g.core AS group_core, a.letter AS area_letter, r.code AS region_code,
                  r.name AS region_name, w.committee_code AS committee_code, cm.name AS committee_name,
                  CASE WHEN w.id IS NULL THEN CAST(NULL AS INTEGER) WHEN w.closed_on IS NULL THEN w.staff_complement
                       ELSE 0 END AS complement,
                  SUM(p.on_record) AS on_record, SUM(p.suspended) AS suspended, SUM(p.paying) AS paying,
                  SUM(p.opening) AS opening, SUM(p.joined) AS joined, SUM(p.left_n) AS left_count,
                  SUM(p.reinstated_n) AS reinstated, SUM(p.by_debit_order) AS by_debit_order,
                  SUM(p.by_deduction) AS by_deduction, SUM(p.by_other) AS by_other,
                  SUM(p.debit_order_rand) AS debit_order_rand, SUM(p.deduction_rand) AS deduction_rand,
                  SUM(p.other_rand) AS other_rand, SUM(p.representatives) AS representatives,
                  SUM(p.committee_members) AS committee_members, SUM(p.seeded) AS seeded,
                  MIN(p.join_date) AS members_from
                FROM (
                  SELECT k.workplace_id AS workplace_id,
                    CASE WHEN k.end_kind IN ('N', 'S') THEN 1 ELSE 0 END AS on_record,
                    CASE WHEN k.end_kind = 'S' THEN 1 ELSE 0 END AS suspended,
                    CASE WHEN k.end_kind = 'N' THEN 1 ELSE 0 END AS paying,
                    CASE WHEN k.start_kind IN ('N', 'S') THEN 1 ELSE 0 END AS opening,
                    k.joined AS joined, k.left_n AS left_n, k.reinstated_n AS reinstated_n,
                    CASE WHEN k.end_kind = 'N' AND k.payment_type = '%1$s' THEN 1 ELSE 0 END AS by_debit_order,
                    CASE WHEN k.end_kind = 'N' AND k.payment_type = '%2$s' THEN 1 ELSE 0 END AS by_deduction,
                    CASE WHEN k.end_kind = 'N' AND (k.payment_type IS NULL OR k.payment_type NOT IN ('%1$s', '%2$s'))
                         THEN 1 ELSE 0 END AS by_other,
                    CASE WHEN k.end_kind = 'N' AND k.payment_type = '%1$s' THEN k.subscription
                         ELSE CAST(0 AS NUMERIC(14, 2)) END AS debit_order_rand,
                    CASE WHEN k.end_kind = 'N' AND k.payment_type = '%2$s' THEN k.subscription
                         ELSE CAST(0 AS NUMERIC(14, 2)) END AS deduction_rand,
                    CASE WHEN k.end_kind = 'N' AND (k.payment_type IS NULL OR k.payment_type NOT IN ('%1$s', '%2$s'))
                         THEN k.subscription ELSE CAST(0 AS NUMERIC(14, 2)) END AS other_rand,
                    CASE WHEN k.end_kind IN ('N', 'S') AND k.shop_steward = TRUE THEN 1 ELSE 0 END AS representatives,
                    CASE WHEN k.end_kind IN ('N', 'S') AND k.bec = TRUE THEN 1 ELSE 0 END AS committee_members,
                    CASE WHEN k.end_kind IN ('N', 'S') AND k.seed = 1 THEN 1 ELSE 0 END AS seeded,
                    k.join_date AS join_date
                  FROM (%3$s) k
                  UNION ALL
                  SELECT w0.id, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, CAST(0 AS NUMERIC(14, 2)), CAST(0 AS NUMERIC(14, 2)),
                    CAST(0 AS NUMERIC(14, 2)), 0, 0, 0, CAST(NULL AS DATE)
                  FROM workplaces w0 WHERE w0.closed_on IS NULL
                ) p
                LEFT JOIN workplaces w ON w.id = p.workplace_id
                LEFT JOIN table_codes t ON t.id = w.table_code_id
                LEFT JOIN employers e ON e.id = t.employer_id
                LEFT JOIN employer_groups g ON g.id = e.group_id
                LEFT JOIN sasbo_areas a ON a.id = w.area_id
                LEFT JOIN sasbo_regions r ON r.id = a.region_id
                LEFT JOIN committees cm ON cm.id = w.committee_id
                GROUP BY p.workplace_id, w.id, w.ra_number, t.code, t.name, e.ordinal, e.name, g.name, g.ordinal,
                  g.core, a.letter, r.code, r.name, w.committee_code, cm.name, w.closed_on, w.staff_complement
                """.formatted(DEBIT_ORDER, PAYROLL_DEDUCTION, kinds());
    }

    /**
     * Each member, with the kind of their status at the month's end and at its start -- NULL where they had not
     * joined yet, or where nobody knows the status -- and what the month's changes did to them.
     */
    static String kinds() {
        return """
                SELECT s.member_id AS member_id, s.workplace_id AS workplace_id, s.end_status AS end_status,
                  %1$s AS end_kind, %2$s AS start_kind, s.joined AS joined, s.left_n AS left_n,
                  s.reinstated_n AS reinstated_n, s.payment_type AS payment_type, s.subscription AS subscription,
                  s.shop_steward AS shop_steward, s.bec AS bec, s.seed AS seed, s.join_date AS join_date
                FROM (%3$s) s
                """.formatted(MemberSummaryRepository.kind("s.end_status"),
                MemberSummaryRepository.kind("s.start_status"), statuses());
    }

    /** Each member's status at the month's end and at its start (rule S), and the month's changes counted. */
    private static String statuses() {
        String toKind = MemberSummaryRepository.kind("c.to_status");
        String fromKind = MemberSummaryRepository.kind("c.from_status");
        return """
                SELECT m.id AS member_id, m.workplace_id AS workplace_id,
                  CASE WHEN m.join_date IS NULL OR m.join_date < :firstDayOfNext
                       THEN COALESCE(ae.from_status, m.status) END AS end_status,
                  CASE WHEN m.join_date IS NULL OR m.join_date < :firstDay
                       THEN COALESCE(ast.from_status, m.status) END AS start_status,
                  CASE WHEN m.join_date >= :firstDay AND m.join_date < :firstDayOfNext THEN 1 ELSE 0 END AS joined,
                  COALESCE(mv.left_n, 0) AS left_n, COALESCE(mv.reinstated_n, 0) AS reinstated_n,
                  m.payment_type AS payment_type,
                  COALESCE(m.monthly_subscription_numeric, CAST(0 AS NUMERIC(14, 2))) AS subscription,
                  m.is_shop_steward AS shop_steward, m.is_bec AS bec,
                  CASE WHEN m.membership_number IN (:seeded) THEN 1 ELSE 0 END AS seed,
                  m.join_date AS join_date
                FROM members m
                LEFT JOIN (
                  SELECT c.member_id, c.from_status FROM member_status_changes c
                  WHERE c.changed_at >= :next AND NOT EXISTS (SELECT 1 FROM member_status_changes x
                    WHERE x.member_id = c.member_id AND x.changed_at >= :next
                      AND (x.changed_at < c.changed_at OR (x.changed_at = c.changed_at AND x.id < c.id)))
                ) ae ON ae.member_id = m.id
                LEFT JOIN (
                  SELECT c.member_id, c.from_status FROM member_status_changes c
                  WHERE c.changed_at >= :start AND NOT EXISTS (SELECT 1 FROM member_status_changes x
                    WHERE x.member_id = c.member_id AND x.changed_at >= :start
                      AND (x.changed_at < c.changed_at OR (x.changed_at = c.changed_at AND x.id < c.id)))
                ) ast ON ast.member_id = m.id
                LEFT JOIN (
                  SELECT c.member_id,
                    SUM(CASE WHEN %1$s = 'L' AND COALESCE(%2$s, '?') <> 'L' THEN 1 ELSE 0 END) AS left_n,
                    SUM(CASE WHEN %2$s = 'L' AND COALESCE(%1$s, '?') <> 'L' THEN 1 ELSE 0 END) AS reinstated_n
                  FROM member_status_changes c
                  WHERE c.changed_at >= :start AND c.changed_at < :next
                  GROUP BY c.member_id
                ) mv ON mv.member_id = m.id
                """.formatted(toKind, fromKind);
    }

    private static MapSqlParameterSource arguments(MonthRange month, Collection<String> seeded) {
        return new MapSqlParameterSource()
                .addValue("start", utc(month.start()))
                .addValue("next", utc(month.next()))
                .addValue("firstDay", month.firstDay())
                .addValue("firstDayOfNext", month.firstDayOfNext())
                .addValue("seeded", seeded.isEmpty() ? List.of("~none") : seeded);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static TakenMonth takenMonth(ResultSet rs, int n) throws SQLException {
        OffsetDateTime from = rs.getObject("changes_from", OffsetDateTime.class);
        return new TakenMonth(rs.getLong("id"), rs.getDate("month_start").toLocalDate(), rs.getString("taken_by"),
                rs.getObject("taken_at", OffsetDateTime.class).toInstant(), from == null ? null : from.toInstant());
    }

    static Fact fact(ResultSet rs, int n) throws SQLException {
        Date from = rs.getDate("members_from");
        return new Fact(rs.getLong("workplace_key"), orNull(rs, rs.getLong("workplace_id")),
                orNull(rs, rs.getInt("ra_number")), orNull(rs, rs.getInt("table_code")),
                rs.getString("table_code_name"), orNull(rs, rs.getInt("employer_ordinal")),
                rs.getString("employer_name"), rs.getString("group_name"), orNull(rs, rs.getInt("group_ordinal")),
                orNull(rs, rs.getBoolean("group_core")), rs.getString("area_letter"), rs.getString("region_code"),
                rs.getString("region_name"), rs.getString("committee_code"), rs.getString("committee_name"),
                orNull(rs, rs.getInt("complement")), rs.getLong("on_record"), rs.getLong("suspended"),
                rs.getLong("paying"), rs.getLong("opening"), rs.getLong("joined"), rs.getLong("left_count"),
                rs.getLong("reinstated"), rs.getLong("by_debit_order"), rs.getLong("by_deduction"),
                rs.getLong("by_other"), rs.getBigDecimal("debit_order_rand"), rs.getBigDecimal("deduction_rand"),
                rs.getBigDecimal("other_rand"), rs.getLong("representatives"), rs.getLong("committee_members"),
                rs.getLong("seeded"), from == null ? null : from.toLocalDate());
    }

    /**
     * {@code value}, or {@code null} where the column just read was NULL: the getter is the argument, so it has run
     * when this asks, which is when JDBC can say.
     */
    private static <T> T orNull(ResultSet rs, T value) throws SQLException {
        return rs.wasNull() ? null : value;
    }

    private static Count count(ResultSet rs, int n) throws SQLException {
        int code = rs.getInt("code");
        Integer read = rs.wasNull() ? null : code;
        return new Count(rs.getLong("workplace_key"), MonthEndCount.Measure.valueOf(rs.getString("measure")), read,
                rs.getString("name"), rs.getString("detail"), rs.getLong("members"));
    }
}

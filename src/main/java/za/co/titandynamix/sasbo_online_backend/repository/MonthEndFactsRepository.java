package za.co.titandynamix.sasbo_online_backend.repository;

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
import java.util.List;
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

    private final NamedParameterJdbcTemplate jdbc;

    public MonthEndFactsRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
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
        String toKind = MemberSummaryRepository.kind("c.to_status");
        String fromKind = MemberSummaryRepository.kind("c.from_status");
        return jdbc.update("""
                INSERT INTO month_end_counts (month_id, month_start, workplace_key, measure, code, name, detail, members)
                SELECT :monthId, :firstDay, l.workplace_key, 'LEFT_BY_CODE', l.code, l.name, l.detail, COUNT(*)
                FROM (
                  SELECT COALESCE(m.workplace_id, 0) AS workplace_key,
                    COALESCE(p.sasbo_code, d.sasbo_code) AS code,
                    CASE WHEN d.id IS NULL THEN c.reason ELSE COALESCE(p.name, d.name) END AS name,
                    CASE WHEN p.id IS NOT NULL THEN d.name END AS detail
                  FROM member_status_changes c
                  JOIN members m ON m.id = c.member_id
                  LEFT JOIN deletion_codes d ON d.id = c.deletion_code_id
                  LEFT JOIN deletion_codes p ON p.id = d.parent_id
                  WHERE c.changed_at >= :start AND c.changed_at < :next
                    AND %1$s = 'L' AND COALESCE(%2$s, '?') <> 'L'
                ) l
                GROUP BY l.workplace_key, l.code, l.name, l.detail
                """.formatted(toKind, fromKind), arguments(month, List.of()).addValue("monthId", monthId));
    }

    /**
     * The suspended at the month's end, by the suspension code of the change that suspended them -- the last change
     * into a suspended status before the month ended. A member suspended with no change on record (captured so) has
     * none, and is counted without a code -- one statement.
     */
    public int insertSuspendedByCode(long monthId, MonthRange month) {
        String toKind = MemberSummaryRepository.kind("c.to_status");
        String laterKind = MemberSummaryRepository.kind("y.to_status");
        return jdbc.update("""
                INSERT INTO month_end_counts (month_id, month_start, workplace_key, measure, code, name, detail, members)
                SELECT :monthId, :firstDay, s.workplace_key, 'SUSPENDED_BY_CODE', s.code, s.name, CAST(NULL AS VARCHAR(255)),
                  COUNT(*)
                FROM (
                  SELECT COALESCE(k.workplace_id, 0) AS workplace_key, sc.code AS code,
                    CASE WHEN sc.id IS NOT NULL THEN sc.name ELSE last_in.reason END AS name
                  FROM (%1$s) k
                  LEFT JOIN (
                    SELECT c.member_id, c.suspension_code_id, c.reason FROM member_status_changes c
                    WHERE c.changed_at < :next AND %2$s = 'S'
                      AND NOT EXISTS (SELECT 1 FROM member_status_changes y WHERE y.member_id = c.member_id
                        AND y.changed_at < :next AND %3$s = 'S'
                        AND (y.changed_at > c.changed_at OR (y.changed_at = c.changed_at AND y.id > c.id)))
                  ) last_in ON last_in.member_id = k.member_id
                  LEFT JOIN suspension_codes sc ON sc.id = last_in.suspension_code_id
                  WHERE k.end_kind = 'S'
                ) s
                GROUP BY s.workplace_key, s.code, s.name
                """.formatted(kinds(), toKind, laterKind), arguments(month, List.of()).addValue("monthId", monthId));
    }

    /** The month's recruits -- a Date Joined in the month, whatever their status now (rule N) -- by recruiter and channel. */
    public int insertRecruited(long monthId, MonthRange month) {
        return jdbc.update("""
                INSERT INTO month_end_counts (month_id, month_start, workplace_key, measure, code, name, detail, members)
                SELECT :monthId, :firstDay, r.workplace_key, 'RECRUITED_BY', r.code, r.name, r.detail, COUNT(*)
                FROM (
                  SELECT COALESCE(m.workplace_id, 0) AS workplace_key, m.recruiter_id AS code, rc.name AS name,
                    ch.name AS detail
                  FROM members m
                  LEFT JOIN recruiters rc ON rc.id = m.recruiter_id
                  LEFT JOIN recruitment_channel ch ON ch.id = m.recruitment_channel_id
                  WHERE m.join_date >= :firstDay AND m.join_date < :firstDayOfNext
                ) r
                GROUP BY r.workplace_key, r.code, r.name, r.detail
                """, arguments(month, List.of()).addValue("monthId", monthId));
    }

    /** The members whose status at the month's end nobody knows (rule X), by the status as stored -- one statement. */
    public int insertUnknownStatuses(long monthId, MonthRange month) {
        return jdbc.update("""
                INSERT INTO month_end_counts (month_id, month_start, workplace_key, measure, code, name, detail, members)
                SELECT :monthId, :firstDay, u.workplace_key, 'UNKNOWN_STATUS', CAST(NULL AS INTEGER), u.name,
                  CAST(NULL AS VARCHAR(255)), COUNT(*)
                FROM (
                  SELECT COALESCE(k.workplace_id, 0) AS workplace_key, k.end_status AS name
                  FROM (%s) k
                  WHERE k.end_status IS NOT NULL AND k.end_kind IS NULL
                ) u
                GROUP BY u.workplace_key, u.name
                """.formatted(kinds()), arguments(month, List.of()).addValue("monthId", monthId));
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
        return jdbc.query("""
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
        return jdbc.query("""
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

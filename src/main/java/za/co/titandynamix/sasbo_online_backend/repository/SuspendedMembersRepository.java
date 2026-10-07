package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The members suspended now (Stephne, MEN-18: "We should also know who the suspended members are name, mem number
 * etc."), a page at a time -- and how many have lapsed -- in ONE statement each, filtered in SQL by the caller's
 * region or area ({@code CallerScope}), never by loading everyone.
 *
 * <h2>What each row reads</h2>
 * The member, through the partial index {@code members_suspended} (V79) -- the suspended kind's statuses written from
 * the server's own table, so the planner proves the index; their workplace's employer, area and region; and the
 * member's LATEST change into a suspended status, through {@code member_status_changes_member} -- its instant is the
 * day the suspension began, its code SASBO's (A.C.B.'s {@code UNPAID_SUSPENDED} read as 13, MO23), its reason for no
 * payroll deduction the one chosen (MEN-17). A member suspended with no change on record has none of the three.
 *
 * <h2>Bounded</h2>
 * A query timeout of {@value MemberSummaryRepository#TIMEOUT_SECONDS} seconds, as every figure query has (MD47); the
 * page is at most a hundred rows. Every fragment is written here; a request binds values and chooses fixed fragments.
 */
@Repository
public class SuspendedMembersRepository {

    /** A.C.B.'s suspension, read in SASBO's code (MO23) -- as month end's counts read it. */
    static final int DEBIT_RETURNED = 13;

    private final NamedParameterJdbcTemplate jdbc;

    public SuspendedMembersRepository(DataSource dataSource) {
        JdbcTemplate bounded = new JdbcTemplate(dataSource);
        bounded.setQueryTimeout(MemberSummaryRepository.TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }

    /**
     * What a request asks.
     *
     * @param scope        the caller's reach -- a member with no workplace only for ALL
     * @param employerId   an employer's id, through the workplace; {@code null} for every one
     * @param code         SASBO's suspension code; {@code null} for every one
     * @param lapsedOnly   only the suspensions begun before {@code lapsedBefore}
     * @param lapsedBefore a suspension begun before this instant has lapsed (SuspensionLapse)
     */
    public record Ask(CallerScope scope, Long employerId, Integer code, boolean lapsedOnly, Instant lapsedBefore) {
    }

    /** One member suspended now, as the list answers them. */
    public record Row(int memberId, String membershipNumber, String initials, String surname, String bankName,
                      String paymentType, Integer raNumber, String workplaceName, String areaLetter,
                      String employerName, Instant suspendedAt, Integer suspensionCode, String suspensionName,
                      String noDeductionReason) {
    }

    /** How many members the ask matches, how many of them have lapsed, and how many have no date on record. */
    public record Counts(long total, long lapsed, long undated) {
    }

    /** A page of the members the ask matches: the oldest suspension first, the undated first of all. */
    public List<Row> page(Ask ask, int page, int size) {
        return jdbc.query("SELECT m.id AS member_id, m.membership_number, m.initials, m.surname, m.bank_name,"
                        + " m.payment_type, w.ra_number, w.name AS workplace_name, a.letter AS area_letter,"
                        + " e.name AS employer_name, s.changed_at AS suspended_at, sc.code AS suspension_code,"
                        + " sc.name AS suspension_name, ndr.name AS no_deduction_reason"
                        + from(ask)
                        + " ORDER BY s.changed_at ASC NULLS FIRST, m.membership_number ASC, m.id ASC"
                        + " LIMIT :size OFFSET :offset",
                parameters(ask).addValue("size", size).addValue("offset", (long) page * size),
                SuspendedMembersRepository::row);
    }

    /** The counts of the same ask, in one statement. */
    public Counts counts(Ask ask) {
        return jdbc.queryForObject("SELECT COUNT(*) AS total,"
                        + " SUM(CASE WHEN s.changed_at < :lapsedBefore THEN 1 ELSE 0 END) AS lapsed,"
                        + " SUM(CASE WHEN s.changed_at IS NULL THEN 1 ELSE 0 END) AS undated" + from(ask),
                parameters(ask), (rs, n) -> new Counts(rs.getLong("total"), rs.getLong("lapsed"),
                        rs.getLong("undated")));
    }

    /**
     * The statement's body for an ask -- public, with {@link #parameters}, so the proof of its plan reads the text a
     * request sends and never a copy.
     */
    public static String from(Ask ask) {
        String suspended = MemberStatuses.ofKind(MemberStatusKind.SUSPENDED).stream()
                .map(status -> "'" + status.replace("'", "''") + "'").collect(Collectors.joining(", "));
        StringBuilder sql = new StringBuilder("""
                 FROM members m
                 LEFT JOIN workplaces w ON w.id = m.workplace_id
                 LEFT JOIN table_codes t ON t.id = w.table_code_id
                 LEFT JOIN employers e ON e.id = t.employer_id
                 LEFT JOIN sasbo_areas a ON a.id = w.area_id
                 LEFT JOIN sasbo_regions r ON r.id = a.region_id
                 LEFT JOIN member_status_changes s ON s.member_id = m.id AND s.to_status IN (%1$s)
                   AND NOT EXISTS (SELECT 1 FROM member_status_changes y WHERE y.member_id = m.id
                     AND y.to_status IN (%1$s)
                     AND (y.changed_at > s.changed_at OR (y.changed_at = s.changed_at AND y.id > s.id)))
                 LEFT JOIN suspension_codes sc ON sc.id = s.suspension_code_id
                   OR (s.suspension_code_id IS NULL AND CAST(s.reason AS VARCHAR(32)) = 'UNPAID_SUSPENDED'
                       AND sc.code = %2$d)
                 LEFT JOIN no_deduction_reasons ndr ON ndr.id = s.no_deduction_reason_id
                 WHERE m.status IN (%1$s)""".formatted(suspended, DEBIT_RETURNED));
        switch (ask.scope().type()) {
            case REGION -> sql.append(" AND r.code IN (:scopeCodes)");
            case AREA -> sql.append(" AND a.letter IN (:scopeCodes)");
            default -> {
            }
        }
        if (ask.employerId() != null) {
            sql.append(" AND e.id = :employerId");
        }
        if (ask.code() != null) {
            sql.append(" AND sc.code = :code");
        }
        if (ask.lapsedOnly()) {
            sql.append(" AND s.changed_at < :lapsedBefore");
        }
        return sql.toString();
    }

    /** The values the statement is bound with. */
    public static MapSqlParameterSource parameters(Ask ask) {
        return new MapSqlParameterSource()
                .addValue("scopeCodes", ask.scope().isAll() ? List.of("~none") : List.copyOf(ask.scope().codes()))
                .addValue("employerId", ask.employerId())
                .addValue("code", ask.code())
                .addValue("lapsedBefore", OffsetDateTime.ofInstant(ask.lapsedBefore(), ZoneOffset.UTC));
    }

    private static Row row(ResultSet rs, int n) throws SQLException {
        int ra = rs.getInt("ra_number");
        Integer raNumber = rs.wasNull() ? null : ra;
        int code = rs.getInt("suspension_code");
        Integer suspensionCode = rs.wasNull() ? null : code;
        OffsetDateTime at = rs.getObject("suspended_at", OffsetDateTime.class);
        return new Row(rs.getInt("member_id"), rs.getString("membership_number"), rs.getString("initials"),
                rs.getString("surname"), rs.getString("bank_name"), rs.getString("payment_type"), raNumber,
                rs.getString("workplace_name"), rs.getString("area_letter"), rs.getString("employer_name"),
                at == null ? null : at.toInstant(), suspensionCode, rs.getString("suspension_name"),
                rs.getString("no_deduction_reason"));
    }
}

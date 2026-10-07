package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;

/**
 * The members at one workplace, a page at a time -- what a location listing prints under its branch (5 Oct Show &amp;
 * Tell: "the Location Listing showing the members at each branch").
 *
 * <h2>One statement a page, scoped in SQL</h2>
 * The page and its total come back together ({@code count(*) OVER ()}), driven by {@code members_workplace} (V57).
 * The caller's region or area is a {@code WHERE} on the workplace's area and that area's region, in the same statement
 * -- never rows loaded and filtered afterwards -- so a limited caller is never handed a row they may not see, even if
 * the service's own check were ever bypassed.
 *
 * <h2>Who is at a branch</h2>
 * The members on record there: every status but the leaving ones ({@link MemberStatuses#ofKind}), so the suspended are
 * listed beside the paying, as the legacy listing's {@code Susp} column did.
 *
 * <h2>🔒 The numbers are masked IN THE DATABASE</h2>
 * The employee number leaves as its last four digits only, and the identity number, the e-mail addresses and the cell
 * number leave as whether there is one -- the legacy listing's {@code ID · Email · Cell} columns were {@code Y} or
 * blank. So no whole number is ever read into the application to be masked there.
 */
@Repository
public class WorkplaceMemberRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkplaceMemberRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One member as a listing prints them.
     *
     * @param employeeNumberTail the employee number's last four digits; empty where it has four or fewer; {@code null}
     *                           where there is none
     */
    public record Row(int id, String membershipNumber, String title, String initials, String surname, String status,
                      Date joinDate, String employeeNumberTail, BigDecimal monthlySubscription, boolean email,
                      boolean cell, boolean identity) {

        @Override
        public String toString() {
            return "WorkplaceMemberRepository.Row[id=" + id + "]";
        }
    }

    /** A page, and how many rows it is a page of. The total is 0 where the page came back empty. */
    public record Slice(List<Row> rows, long total) {
    }

    /** One page of the members on record at a workplace, as far as the caller's scope reaches, in a listing's order. */
    public Slice page(long workplaceId, CallerScope scope, int page, int size) {
        MapSqlParameterSource args = new MapSqlParameterSource("workplace", workplaceId)
                .addValue("leaving", MemberStatuses.ofKind(MemberStatusKind.LEAVING))
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        String sql = """
                SELECT m.id, m.membership_number, m.title, m.initials, m.surname, m.status, m.join_date,
                  CASE WHEN COALESCE(TRIM(m.employee_number), '') = '' THEN NULL
                       WHEN LENGTH(TRIM(m.employee_number)) <= 4 THEN ''
                       ELSE RIGHT(TRIM(m.employee_number), 4) END AS employee_tail,
                  m.monthly_subscription_numeric,
                  CASE WHEN COALESCE(TRIM(m.personal_email), '') <> '' OR COALESCE(TRIM(m.work_email), '') <> ''
                       THEN TRUE ELSE FALSE END AS has_email,
                  CASE WHEN COALESCE(TRIM(m.cell_tel), '') <> '' THEN TRUE ELSE FALSE END AS has_cell,
                  CASE WHEN COALESCE(TRIM(m.identity_number), '') <> '' OR COALESCE(TRIM(m.passport_number), '') <> ''
                       THEN TRUE ELSE FALSE END AS has_identity,
                  count(*) OVER () AS total
                FROM members m
                JOIN workplaces w ON w.id = m.workplace_id
                JOIN sasbo_areas a ON a.id = w.area_id
                LEFT JOIN sasbo_regions r ON r.id = a.region_id
                WHERE m.workplace_id = :workplace AND m.status NOT IN (:leaving)
                """ + scoped(scope, args) + """
                 ORDER BY m.surname, m.initials, m.membership_number, m.id
                LIMIT :limit OFFSET :offset
                """;
        long[] total = {0};
        List<Row> rows = jdbc.query(sql, args, (rs, n) -> {
            total[0] = rs.getLong("total");
            return new Row(rs.getInt("id"), rs.getString("membership_number"), rs.getString("title"),
                    rs.getString("initials"), rs.getString("surname"), rs.getString("status"),
                    rs.getDate("join_date"), rs.getString("employee_tail"),
                    rs.getBigDecimal("monthly_subscription_numeric"), rs.getBoolean("has_email"),
                    rs.getBoolean("has_cell"), rs.getBoolean("has_identity"));
        });
        return new Slice(rows, total[0]);
    }

    /** One member of a batch's branch, with the branch it stands at and how many members that branch has in all. */
    public record BranchRow(long workplaceId, Row member, long branchTotal) {
    }

    /**
     * The members on record at each of the workplaces asked for -- a batch print's branches, in ONE statement (October
     * build review: one read for the batch, not one a branch) -- {@code perBranch} at most a branch, each branch in a
     * listing's order, numbered within it by a window, with the branch's whole count beside each row. Scoped in SQL by the
     * same fragment as a page of one branch, and masked in the database the same way.
     */
    public List<BranchRow> forWorkplaces(java.util.Collection<Long> workplaceIds, CallerScope scope, int perBranch) {
        if (workplaceIds.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource args = new MapSqlParameterSource("workplaces", workplaceIds)
                .addValue("leaving", MemberStatuses.ofKind(MemberStatusKind.LEAVING))
                .addValue("perBranch", perBranch);
        String sql = """
                SELECT * FROM (
                  SELECT m.workplace_id, m.id, m.membership_number, m.title, m.initials, m.surname, m.status, m.join_date,
                    CASE WHEN COALESCE(TRIM(m.employee_number), '') = '' THEN NULL
                         WHEN LENGTH(TRIM(m.employee_number)) <= 4 THEN ''
                         ELSE RIGHT(TRIM(m.employee_number), 4) END AS employee_tail,
                    m.monthly_subscription_numeric,
                    CASE WHEN COALESCE(TRIM(m.personal_email), '') <> '' OR COALESCE(TRIM(m.work_email), '') <> ''
                         THEN TRUE ELSE FALSE END AS has_email,
                    CASE WHEN COALESCE(TRIM(m.cell_tel), '') <> '' THEN TRUE ELSE FALSE END AS has_cell,
                    CASE WHEN COALESCE(TRIM(m.identity_number), '') <> '' OR COALESCE(TRIM(m.passport_number), '') <> ''
                         THEN TRUE ELSE FALSE END AS has_identity,
                    COUNT(*) OVER (PARTITION BY m.workplace_id) AS branch_total,
                    ROW_NUMBER() OVER (PARTITION BY m.workplace_id
                      ORDER BY m.surname, m.initials, m.membership_number, m.id) AS n
                  FROM members m
                  JOIN workplaces w ON w.id = m.workplace_id
                  JOIN sasbo_areas a ON a.id = w.area_id
                  LEFT JOIN sasbo_regions r ON r.id = a.region_id
                  WHERE m.workplace_id IN (:workplaces) AND m.status NOT IN (:leaving)
                """ + scoped(scope, args) + """
                ) listed WHERE n <= :perBranch ORDER BY workplace_id, n
                """;
        return jdbc.query(sql, args, (rs, i) -> new BranchRow(rs.getLong("workplace_id"),
                new Row(rs.getInt("id"), rs.getString("membership_number"), rs.getString("title"),
                        rs.getString("initials"), rs.getString("surname"), rs.getString("status"),
                        rs.getDate("join_date"), rs.getString("employee_tail"),
                        rs.getBigDecimal("monthly_subscription_numeric"), rs.getBoolean("has_email"),
                        rs.getBoolean("has_cell"), rs.getBoolean("has_identity")),
                rs.getLong("branch_total")));
    }

    /**
     * The caller's scope as a fragment of the statement: nothing for a scope of everything; the workplace's area, or
     * its area's region, among the caller's codes otherwise. Every value is bound -- the fragment is fixed text.
     */
    static String scoped(CallerScope scope, MapSqlParameterSource args) {
        if (scope.isAll()) {
            return "";
        }
        if (!scope.areaLetters().isEmpty()) {
            args.addValue("scopeAreas", scope.areaLetters());
            return " AND UPPER(a.letter) IN (:scopeAreas)";
        }
        if (!scope.regionCodes().isEmpty()) {
            args.addValue("scopeRegions", scope.regionCodes());
            return " AND UPPER(r.code) IN (:scopeRegions)";
        }
        return " AND 1 = 0";
    }
}

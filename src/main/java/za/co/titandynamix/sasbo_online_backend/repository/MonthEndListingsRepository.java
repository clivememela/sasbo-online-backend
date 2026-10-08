package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
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
import java.util.function.Consumer;

/**
 * The members the files that leave SASBO name (October build) -- each list ONE statement both databases accept, built
 * from month end's own rules and never a copy of them: a member's status at the month's end is
 * {@link MonthEndFactsRepository#kinds()} (rule S), <em>joined</em> is rule N, <em>left</em> rule L with SASBO's deletion
 * code read as the leavers' count reads it, <em>suspended</em> the suspension code of the last change into a suspended
 * status, as the suspended's count reads it. So a pack's listing and the statistic beside it count the same members.
 * The {@link MonthRange} each list is given is the WINDOW the month counts (Stephne, MEN-14: "After month end is done -
 * all capturing and changes move to the next month"), from the take before to the month's own -- never the calendar
 * month, or a change saved after a take on the month's last day would reach no month's file.
 *
 * <ul>
 *   <li>A REGION'S LISTINGS are cut by where each member works, IN SQL -- the areas of the pack set, or its region
 *       (MEN-08: each region gets "only there regions info"). A member with no workplace is in no region's pack;</li>
 *   <li>A BANK'S ROWS are the members the bank deducts for -- at its table codes, paying by payroll deduction
 *       (Stephne, MEN-32: "Only payroll deduction instructions are send to the Bank"): the full list is everyone on
 *       record at the month's end, the suspended included as the whole-membership extracts counted them; new and
 *       deleted are the month's joined and left;</li>
 *   <li>A PROVIDER'S ROWS are every member paying at the month's end, wherever they work -- provisional (MEN-35: the
 *       August funeral list carried "every member").</li>
 * </ul>
 * The long lists -- a bank's, a provider's -- are STREAMED a row at a time to the writer, inside the caller's
 * read-only transaction so the driver reads them by a cursor, and never gathered whole.
 */
@Repository
public class MonthEndListingsRepository {

    /** How a member paying by payroll deduction is stored (BR41). */
    static final String PAYROLL = MonthEndFactsRepository.PAYROLL_DEDUCTION;

    /** How long a listing may take: a region's is small, a bank's or a provider's every member of a group or more. */
    static final int LISTING_TIMEOUT_SECONDS = 30;
    static final int FILE_TIMEOUT_SECONDS = 120;

    /** Rows the driver brings at a time when a list is streamed. */
    static final int FETCH_SIZE = 500;

    private final NamedParameterJdbcTemplate listings;
    private final NamedParameterJdbcTemplate files;

    public MonthEndListingsRepository(DataSource dataSource) {
        JdbcTemplate listing = new JdbcTemplate(dataSource);
        listing.setQueryTimeout(LISTING_TIMEOUT_SECONDS);
        this.listings = new NamedParameterJdbcTemplate(listing);
        JdbcTemplate streaming = new JdbcTemplate(dataSource);
        streaming.setQueryTimeout(FILE_TIMEOUT_SECONDS);
        streaming.setFetchSize(FETCH_SIZE);
        this.files = new NamedParameterJdbcTemplate(streaming);
    }

    /**
     * A member as a file names them. {@code className} is NEW or DEL in a new-and-deleted list, SUS in the suspended's;
     * {@code code} the deletion or suspension code, {@code codeName} its name and {@code codeDetail} the reason under
     * it; {@code changedAt} the change that made them a leaver or suspended them.
     */
    public record MemberLine(long memberId, String membershipNumber, String title, String initials, String firstNames,
                             String surname, String employeeNumber, String identityNumber, String passportNumber,
                             String address, LocalDate joinDate, String paymentType, String subscriptionFrequency,
                             BigDecimal subscription, Integer raNumber, String workplaceName, String areaLetter,
                             String regionCode, Integer tableCode, String employerName, String className, Integer code,
                             String codeName, String codeDetail, Instant changedAt) {

        /** The identity number where one is on record, else the passport number. */
        public String identity() {
            return identityNumber != null && !identityNumber.isBlank() ? identityNumber : passportNumber;
        }

        /** Never the identity or passport number: a line printed by accident prints who, not what. */
        @Override
        public String toString() {
            return "MemberLine[" + memberId + ", " + className + "]";
        }
    }

    /** Where a region's pack reaches: its areas, or -- naming none -- its whole region. */
    public record PackScope(String regionCode, List<String> areaLetters) {

        String predicate() {
            return areaLetters.isEmpty() ? "r.code = :region" : "a.letter IN (:areas)";
        }

        void bind(MapSqlParameterSource arguments) {
            arguments.addValue("region", regionCode);
            arguments.addValue("areas", areaLetters.isEmpty() ? List.of("~") : areaLetters);
        }
    }

    /** How many rows a bank's or a provider's file would hold, for one table code -- {@code null}: no table code. */
    public record TableCodeRows(Integer tableCode, long fullRows, long newRows, long deletedRows, long paying) {
    }

    // ------------------------------------------------------------------------------------------------ the columns

    private static final String MEMBER_COLUMNS = """
            m.id AS member_id, m.membership_number, m.title, m.initials, m.first_names, m.surname, m.employee_number,
              m.identity_number, m.passport_number, m.address, m.join_date, m.payment_type, m.subscription_frequency,
              COALESCE(m.monthly_subscription_numeric, CAST(0 AS NUMERIC(14, 2))) AS subscription,
              w.ra_number AS ra_number, w.name AS workplace_name, a.letter AS area_letter, r.code AS region_code,
              t.code AS table_code, COALESCE(e.name, t.name, w.employer_printed) AS employer_name""";

    /** Where a member works, and the lineage of it -- a member with no workplace is not reached by these. */
    private static final String PLACED = """
            JOIN workplaces w ON w.id = m.workplace_id
            JOIN sasbo_areas a ON a.id = w.area_id
            LEFT JOIN sasbo_regions r ON r.id = a.region_id
            LEFT JOIN table_codes t ON t.id = w.table_code_id
            LEFT JOIN employers e ON e.id = t.employer_id
            """;

    /** The same, reaching a member with no workplace as well -- a provider's list is everyone's. */
    private static final String ANYWHERE = """
            LEFT JOIN workplaces w ON w.id = m.workplace_id
            LEFT JOIN sasbo_areas a ON a.id = w.area_id
            LEFT JOIN sasbo_regions r ON r.id = a.region_id
            LEFT JOIN table_codes t ON t.id = w.table_code_id
            LEFT JOIN employers e ON e.id = t.employer_id
            """;

    private static final String NO_CHANGE = """
            CAST(NULL AS INTEGER) AS code, CAST(NULL AS VARCHAR(255)) AS code_name,
              CAST(NULL AS VARCHAR(255)) AS code_detail, CAST(NULL AS TIMESTAMP WITH TIME ZONE) AS changed_at""";

    // ------------------------------------------------------------------------------------------------ a region's

    /** RULE N: the members who joined in the month, where the pack reaches -- by area, employer and surname. */
    public List<MemberLine> joined(MonthRange month, PackScope scope) {
        MapSqlParameterSource arguments = arguments(month, List.of());
        scope.bind(arguments);
        return listings.query("SELECT " + MEMBER_COLUMNS + ", CAST('NEW' AS VARCHAR(3)) AS class_name, " + NO_CHANGE
                + " FROM members m " + PLACED + " WHERE " + MemberSummaryRepository.joined("m", "start", "firstDay") + " AND "
                + scope.predicate() + " ORDER BY a.letter, employer_name, m.surname, m.membership_number", arguments,
                MonthEndListingsRepository::line);
    }

    /** RULE L: the month's changes into a leaving status, with SASBO's deletion code, where the pack reaches. */
    public List<MemberLine> left(MonthRange month, PackScope scope) {
        MapSqlParameterSource arguments = arguments(month, List.of());
        scope.bind(arguments);
        return listings.query(leftStatement(scope.predicate()) + " ORDER BY a.letter, employer_name, m.surname, "
                + "m.membership_number, changed_at", arguments, MonthEndListingsRepository::line);
    }

    /** RULE U: the members suspended at the month's end, with the code of the change that suspended them. */
    public List<MemberLine> suspended(MonthRange month, PackScope scope, Collection<String> seeded) {
        MapSqlParameterSource arguments = arguments(month, seeded);
        scope.bind(arguments);
        String toKind = MemberSummaryRepository.kind("c.to_status");
        String laterKind = MemberSummaryRepository.kind("y.to_status");
        return listings.query("""
                SELECT %1$s, CAST('SUS' AS VARCHAR(3)) AS class_name, sc.code AS code,
                  CASE WHEN sc.id IS NOT NULL THEN CAST(sc.name AS VARCHAR(255))
                       ELSE CAST(last_in.reason AS VARCHAR(255)) END AS code_name,
                  CAST(NULL AS VARCHAR(255)) AS code_detail, last_in.changed_at AS changed_at
                FROM (%2$s) k
                JOIN members m ON m.id = k.member_id
                %3$s
                LEFT JOIN (
                  SELECT c.member_id, c.suspension_code_id, c.reason, c.changed_at FROM member_status_changes c
                  WHERE c.changed_at < :next AND %4$s = 'S'
                    AND NOT EXISTS (SELECT 1 FROM member_status_changes y WHERE y.member_id = c.member_id
                      AND y.changed_at < :next AND %5$s = 'S'
                      AND (y.changed_at > c.changed_at OR (y.changed_at = c.changed_at AND y.id > c.id)))
                ) last_in ON last_in.member_id = k.member_id
                LEFT JOIN suspension_codes sc ON sc.id = last_in.suspension_code_id
                  OR (last_in.suspension_code_id IS NULL
                      AND CAST(last_in.reason AS VARCHAR(32)) = 'UNPAID_SUSPENDED' AND sc.code = %6$d)
                WHERE k.end_kind = 'S' AND %7$s
                ORDER BY a.letter, employer_name, m.surname, m.membership_number
                """.formatted(MEMBER_COLUMNS, MonthEndFactsRepository.kinds(), PLACED, toKind, laterKind,
                MonthEndFactsRepository.DEBIT_RETURNED_SUSPENSION_CODE, scope.predicate()), arguments,
                MonthEndListingsRepository::line);
    }

    // ------------------------------------------------------------------------------------------------ a bank's

    /**
     * A bank's full list: every member on record at the month's end at its table codes who pays by payroll deduction,
     * by employee number -- streamed to {@code each}.
     */
    public void fullList(MonthRange month, Collection<Integer> tableCodes, Collection<String> seeded,
                         Consumer<MemberLine> each) {
        MapSqlParameterSource arguments = arguments(month, seeded).addValue("codes", tableCodes)
                .addValue("payroll", PAYROLL);
        files.query("SELECT " + MEMBER_COLUMNS + ", CAST(NULL AS VARCHAR(3)) AS class_name, " + NO_CHANGE
                + " FROM (" + MonthEndFactsRepository.kinds() + ") k JOIN members m ON m.id = k.member_id " + PLACED
                + " WHERE k.end_kind IN ('N', 'S') AND m.payment_type = :payroll AND t.code IN (:codes)"
                + " ORDER BY m.employee_number, m.surname, m.membership_number", arguments,
                rs -> {
                    each.accept(line(rs, 0));
                });
    }

    /** A bank's new members of the month (rule N) who pay by payroll deduction, streamed to {@code each}. */
    public void newRows(MonthRange month, Collection<Integer> tableCodes, Consumer<MemberLine> each) {
        MapSqlParameterSource arguments = arguments(month, List.of()).addValue("codes", tableCodes)
                .addValue("payroll", PAYROLL);
        files.query("SELECT " + MEMBER_COLUMNS + ", CAST('NEW' AS VARCHAR(3)) AS class_name, " + NO_CHANGE
                + " FROM members m " + PLACED + " WHERE " + MemberSummaryRepository.joined("m", "start", "firstDay")
                + " AND m.payment_type = :payroll AND t.code IN (:codes) ORDER BY m.surname, m.initials, "
                + "m.membership_number", arguments, rs -> {
                    each.accept(line(rs, 0));
                });
    }

    /** A bank's members who left in the month (rule L) and paid by payroll deduction, streamed to {@code each}. */
    public void deletedRows(MonthRange month, Collection<Integer> tableCodes, Consumer<MemberLine> each) {
        MapSqlParameterSource arguments = arguments(month, List.of()).addValue("codes", tableCodes)
                .addValue("payroll", PAYROLL);
        files.query(leftStatement("m.payment_type = :payroll AND t.code IN (:codes)")
                + " ORDER BY m.surname, m.initials, m.membership_number, changed_at", arguments, rs -> {
                    each.accept(line(rs, 0));
                });
    }

    // ------------------------------------------------------------------------------------------------ a provider's

    /** Every member paying at the month's end, wherever they work, by surname -- streamed to {@code each}. */
    public void paying(MonthRange month, Collection<String> seeded, Consumer<MemberLine> each) {
        files.query("SELECT " + MEMBER_COLUMNS + ", CAST(NULL AS VARCHAR(3)) AS class_name, " + NO_CHANGE
                + " FROM (" + MonthEndFactsRepository.kinds() + ") k JOIN members m ON m.id = k.member_id " + ANYWHERE
                + " WHERE k.end_kind = 'N' ORDER BY m.surname, m.initials, m.membership_number",
                arguments(month, seeded), rs -> {
                    each.accept(line(rs, 0));
                });
    }

    // ------------------------------------------------------------------------------------------------ the counts

    /**
     * How many rows each table code would give a file of the month -- full list, new, deleted, paying -- in ONE
     * statement, so the screen's preview of twenty-odd banks is one read of the month, not twenty.
     */
    public List<TableCodeRows> rowsByTableCode(MonthRange month, Collection<String> seeded) {
        return files.query("""
                SELECT t.code AS table_code,
                  SUM(CASE WHEN k.end_kind IN ('N', 'S') AND k.payment_type = :payroll THEN 1 ELSE 0 END) AS full_rows,
                  SUM(CASE WHEN k.joined = 1 AND k.payment_type = :payroll THEN 1 ELSE 0 END) AS new_rows,
                  SUM(CASE WHEN k.payment_type = :payroll THEN k.left_n ELSE 0 END) AS deleted_rows,
                  SUM(CASE WHEN k.end_kind = 'N' THEN 1 ELSE 0 END) AS paying
                FROM (%s) k
                LEFT JOIN workplaces w ON w.id = k.workplace_id
                LEFT JOIN table_codes t ON t.id = w.table_code_id
                GROUP BY t.code
                """.formatted(MonthEndFactsRepository.kinds()), arguments(month, seeded).addValue("payroll", PAYROLL),
                (rs, n) -> {
                    int code = rs.getInt("table_code");
                    Integer tableCode = rs.wasNull() ? null : code;
                    return new TableCodeRows(tableCode, rs.getLong("full_rows"), rs.getLong("new_rows"),
                            rs.getLong("deleted_rows"), rs.getLong("paying"));
                });
    }

    // ------------------------------------------------------------------------------------------------ statements

    /**
     * RULE L's leavers as a month counts them, as the leavers' count reads their code (MO23 included), where
     * {@code where} holds: a leaving undone by a rejoin in the same window is none (Stephne, MEN-23: "We would like to
     * retain current membership as it happened in the same month") -- never sent to a bank as DEL, never listed deleted.
     */
    private static String leftStatement(String where) {
        return """
                SELECT %1$s, CAST('DEL' AS VARCHAR(3)) AS class_name, COALESCE(p.sasbo_code, d.sasbo_code) AS code,
                  CASE WHEN d.id IS NULL THEN CAST(c.reason AS VARCHAR(255))
                       ELSE CAST(COALESCE(p.name, d.name) AS VARCHAR(255)) END AS code_name,
                  CASE WHEN p.id IS NOT NULL THEN CAST(d.name AS VARCHAR(255)) END AS code_detail,
                  c.changed_at AS changed_at
                FROM member_status_changes c
                JOIN members m ON m.id = c.member_id
                %2$s
                LEFT JOIN deletion_codes d ON d.id = c.deletion_code_id
                  OR (c.deletion_code_id IS NULL AND CAST(c.reason AS VARCHAR(32)) = 'SUBS_IN_ARREARS'
                      AND d.sasbo_code = %3$d)
                LEFT JOIN deletion_codes p ON p.id = d.parent_id
                WHERE c.changed_at >= :start AND c.changed_at < :next AND %4$s AND %5$s
                """.formatted(MEMBER_COLUMNS, PLACED, MonthEndFactsRepository.ARREARS_DELETION_CODE,
                MemberSummaryRepository.leaving("c", "start"), where);
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

    private static MemberLine line(ResultSet rs, int n) throws SQLException {
        Date joined = rs.getDate("join_date");
        int ra = rs.getInt("ra_number");
        Integer raNumber = rs.wasNull() ? null : ra;
        int tc = rs.getInt("table_code");
        Integer tableCode = rs.wasNull() ? null : tc;
        int c = rs.getInt("code");
        Integer code = rs.wasNull() ? null : c;
        OffsetDateTime changed = rs.getObject("changed_at", OffsetDateTime.class);
        return new MemberLine(rs.getLong("member_id"), rs.getString("membership_number"), rs.getString("title"),
                rs.getString("initials"), rs.getString("first_names"), rs.getString("surname"),
                rs.getString("employee_number"), rs.getString("identity_number"), rs.getString("passport_number"),
                rs.getString("address"), joined == null ? null : joined.toLocalDate(), rs.getString("payment_type"),
                rs.getString("subscription_frequency"), rs.getBigDecimal("subscription"), raNumber,
                rs.getString("workplace_name"), rs.getString("area_letter"), rs.getString("region_code"), tableCode,
                rs.getString("employer_name"), rs.getString("class_name"), code, rs.getString("code_name"),
                rs.getString("code_detail"), changed == null ? null : changed.toInstant());
    }
}

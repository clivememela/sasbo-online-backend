package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberRegisterPageDto;
import za.co.titandynamix.sasbo_online_backend.security.CallerScope;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the member register a page at a time (register row A10), and the figures its strip shows.
 *
 * <h2>Two statements a page</h2>
 * How many members the question matches, then the page of them -- read by the service inside one read-only
 * transaction at {@code REPEATABLE READ}, so the total and the rows come from the same snapshot and cannot disagree.
 * The rows are answered straight into their DTO: no entity is loaded, no association is read later, and the number of
 * statements does not grow with the page ({@code MemberRegisterRepositoryTest}).
 *
 * <h2>Every statement is written here (MR-SEC-22)</h2>
 * No request names a column. A criterion only chooses which of the fixed fragments below is joined in, and its value
 * is always bound. A search term is literal: escaped with {@code !} as ACB's member search and the workplaces escape
 * theirs, so a {@code %} or an {@code _} typed is only itself. Both databases accept every statement (BR65), and each
 * way it can read the register has an index of V68 -- proved by the plan in {@code MemberRegisterPlanIT}, which reads
 * these statements through {@link #pageStatement} and the others, never a copy typed out again.
 *
 * <h2>Scoped in the statement (contracts/permissions.md)</h2>
 * A caller limited to regions or areas reads only the members whose workplace is in one of them; a member with no
 * workplace only a caller who sees everything. A {@code WHERE} on the workplace, never everything read and filtered.
 *
 * <h2>Bounded</h2>
 * Every read carries a query timeout of {@value #TIMEOUT_SECONDS} seconds, below the browser's slow notice at five
 * (ME-15): the hosted database is a tenth of a processor, and a page must never hang a screen.
 */
@Repository
public class MemberRegisterRepository {

    /** The ceiling on one read of the register. */
    public static final int TIMEOUT_SECONDS = 4;

    /** What a row is answered with: the register's six columns, and the id that opens the record. */
    private static final String COLUMNS = "m.id, m.membership_number, m.employee_number, m.title, m.initials,"
            + " m.surname, m.first_names, m.bank_name, m.branch, m.status";

    private final NamedParameterJdbcTemplate jdbc;

    public MemberRegisterRepository(DataSource dataSource) {
        JdbcTemplate bounded = new JdbcTemplate(dataSource);
        bounded.setQueryTimeout(TIMEOUT_SECONDS);
        this.jdbc = new NamedParameterJdbcTemplate(bounded);
    }

    /**
     * The orders a page is read in, each the order of an index of V68, and each ending with the id -- so two pages
     * never share a member or skip one. Descending reads the same index backwards.
     */
    public enum Order {
        SURNAME("surname", "lower(m.surname)", "lower(m.first_names)", "m.id"),
        MEMBERSHIP_NUMBER("membershipNumber", "m.membership_number", "m.id"),
        BANK("bank", "m.bank_name", "lower(m.surname)", "lower(m.first_names)", "m.id"),
        BRANCH("branch", "m.branch", "lower(m.surname)", "lower(m.first_names)", "m.id"),
        STATUS("status", "m.status", "lower(m.surname)", "lower(m.first_names)", "m.id");

        private final String key;
        private final List<String> columns;

        Order(String key, String... columns) {
            this.key = key;
            this.columns = List.of(columns);
        }

        /** The order's name on the wire -- the register's column it sorts by. */
        public String key() {
            return key;
        }

        String orderBy(boolean descending) {
            String direction = descending ? " DESC" : " ASC";
            return String.join(direction + ", ", columns) + direction;
        }

        public static Optional<Order> byKey(String key) {
            return Arrays.stream(values()).filter(order -> order.key.equals(key)).findFirst();
        }
    }

    /**
     * What a page may be narrowed by. Every field is trimmed and checked by the service, and null where it is not
     * asked; how each is matched is the service's {@code MemberRegisterQuery} contract.
     */
    public record Filter(String q, String status, String bank, String branch, String title, String initials,
                         String surname, String employeeNumber, String membershipNumber, String identityType,
                         String identityNumber, String bankAccountNumber, String phone, String personalEmail,
                         String workEmail, String race) {

        /** Nothing asked: the whole register. */
        public static final Filter NONE = new Filter(null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);

        /** 🔒 Never the values: they are a member's name and numbers. */
        @Override
        public String toString() {
            return "Filter[...]";
        }
    }

    /** A statement and the values it is bound with -- what the plan proof explains. */
    public record Statement(String sql, MapSqlParameterSource arguments) {
    }

    /** How many members the question matches, in the caller's scope. */
    public long count(Filter filter, CallerScope scope) {
        Statement count = countStatement(filter, scope);
        Long found = jdbc.queryForObject(count.sql(), count.arguments(), Long.class);
        return found == null ? 0 : found;
    }

    /** One page of them, in the order asked. */
    public List<MemberRegisterPageDto.Row> page(Filter filter, CallerScope scope, Order order, boolean descending,
                                                int page, int size) {
        Statement rows = pageStatement(filter, scope, order, descending, page, size);
        return jdbc.query(rows.sql(), rows.arguments(), MemberRegisterRepository::row);
    }

    /** The members in scope, counted by status -- every status as it is held, an unknown one and none included. */
    public Map<String, Long> countsByStatus(CallerScope scope) {
        Statement counts = statusCountsStatement(scope);
        Map<String, Long> found = new LinkedHashMap<>();
        jdbc.query(counts.sql(), counts.arguments(), rs -> {
            found.put(rs.getString("status"), rs.getLong("members"));
        });
        return found;
    }

    /** The members in scope whose Date Joined is in [{@code from}, {@code until}), and how many of them pay by debit order. */
    public long[] joined(CallerScope scope, LocalDate from, LocalDate until, String debitOrder) {
        Statement joined = joinedStatement(scope, from, until, debitOrder);
        return jdbc.queryForObject(joined.sql(), joined.arguments(),
                (rs, n) -> new long[] {rs.getLong("joined"), rs.getLong("by_debit_order")});
    }

    /** The members in scope whose record was saved in [{@code from}, {@code until}) -- the stored time's own zone. */
    public long updated(CallerScope scope, LocalDateTime from, LocalDateTime until) {
        Statement updated = updatedStatement(scope, from, until);
        Long found = jdbc.queryForObject(updated.sql(), updated.arguments(), Long.class);
        return found == null ? 0 : found;
    }

    /** Every branch the members in scope are at, once, in order -- at most {@code limit}. */
    public List<String> branches(CallerScope scope, int limit) {
        Statement branches = branchesStatement(scope, limit);
        return jdbc.queryForList(branches.sql(), branches.arguments(), String.class);
    }

    // ── the statements ────────────────────────────────────────────────────────────────────────────────────────

    public static Statement countStatement(Filter filter, CallerScope scope) {
        MapSqlParameterSource arguments = new MapSqlParameterSource();
        return new Statement("SELECT count(*) FROM members m" + where(filter, scope, arguments), arguments);
    }

    public static Statement pageStatement(Filter filter, CallerScope scope, Order order, boolean descending, int page,
                                          int size) {
        MapSqlParameterSource arguments = new MapSqlParameterSource();
        String sql = "SELECT " + COLUMNS + " FROM members m" + where(filter, scope, arguments)
                + " ORDER BY " + order.orderBy(descending)
                + " LIMIT :limit OFFSET :offset";
        arguments.addValue("limit", size);
        arguments.addValue("offset", (long) page * size);
        return new Statement(sql, arguments);
    }

    public static Statement statusCountsStatement(CallerScope scope) {
        MapSqlParameterSource arguments = new MapSqlParameterSource();
        return new Statement("SELECT m.status, count(*) AS members FROM members m"
                + where(Filter.NONE, scope, arguments) + " GROUP BY m.status", arguments);
    }

    public static Statement joinedStatement(CallerScope scope, LocalDate from, LocalDate until, String debitOrder) {
        MapSqlParameterSource arguments = new MapSqlParameterSource()
                .addValue("from", Date.valueOf(from))
                .addValue("until", Date.valueOf(until))
                .addValue("debitOrder", debitOrder);
        List<String> where = new ArrayList<>(List.of("m.join_date >= :from", "m.join_date < :until"));
        inScope(scope, where, arguments);
        return new Statement("SELECT count(*) AS joined,"
                + " coalesce(sum(CASE WHEN m.payment_type = :debitOrder THEN 1 ELSE 0 END), 0) AS by_debit_order"
                + " FROM members m WHERE " + String.join(" AND ", where), arguments);
    }

    public static Statement updatedStatement(CallerScope scope, LocalDateTime from, LocalDateTime until) {
        MapSqlParameterSource arguments = new MapSqlParameterSource()
                .addValue("from", Timestamp.valueOf(from))
                .addValue("until", Timestamp.valueOf(until));
        List<String> where = new ArrayList<>(List.of("m.updated_at >= :from", "m.updated_at < :until"));
        inScope(scope, where, arguments);
        return new Statement("SELECT count(*) FROM members m WHERE " + String.join(" AND ", where), arguments);
    }

    public static Statement branchesStatement(CallerScope scope, int limit) {
        MapSqlParameterSource arguments = new MapSqlParameterSource("limit", limit);
        List<String> where = new ArrayList<>(List.of("m.branch IS NOT NULL", "m.branch <> ''"));
        inScope(scope, where, arguments);
        return new Statement("SELECT DISTINCT m.branch FROM members m WHERE " + String.join(" AND ", where)
                + " ORDER BY m.branch LIMIT :limit", arguments);
    }

    // ── the fragments ─────────────────────────────────────────────────────────────────────────────────────────

    private static String where(Filter filter, CallerScope scope, MapSqlParameterSource arguments) {
        List<String> where = new ArrayList<>();
        if (filter.q() != null) {
            where.add(search(filter.q(), arguments));
        }
        exactly(where, arguments, "m.status", "status", filter.status());
        exactly(where, arguments, "m.bank_name", "bank", filter.bank());
        exactly(where, arguments, "m.branch", "branch", filter.branch());
        exactly(where, arguments, "m.title", "title", filter.title());
        exactly(where, arguments, "m.identity_type", "identityType", filter.identityType());
        exactly(where, arguments, "m.race", "race", filter.race());
        startsWith(where, arguments, "m.initials", "initials", filter.initials());
        startsWith(where, arguments, "m.surname", "surname", filter.surname());
        startsWith(where, arguments, "m.employee_number", "employeeNumber", filter.employeeNumber());
        if (filter.membershipNumber() != null) {
            where.add(membershipNumber(filter.membershipNumber(), "membershipNumber", arguments));
        }
        wholeOrLastFour(where, arguments, "upper(right(m.identity_number, 4))", "upper(m.identity_number)", "identity",
                filter.identityNumber() == null ? null : filter.identityNumber().toUpperCase(Locale.ROOT));
        wholeOrLastFour(where, arguments, "right(m.bank_account_number, 4)", "m.bank_account_number", "account",
                filter.bankAccountNumber());
        wholeOrLastFour(where, arguments, "right(m.phone, 4)", "m.phone", "phone", filter.phone());
        startsWith(where, arguments, "m.personal_email", "personalEmail", filter.personalEmail());
        startsWith(where, arguments, "m.work_email", "workEmail", filter.workEmail());
        inScope(scope, where, arguments);
        return where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
    }

    /**
     * The search box. A member is found by the START of their surname, a first name, their membership number or their
     * employee number, in any case; digits alone also find the membership number typed without its {@code M-}; four
     * digits find the identity numbers that end in them -- the four every masked display prints; and a longer term with
     * a digit in it finds the identity or passport number that is exactly it.
     */
    private static String search(String q, MapSqlParameterSource arguments) {
        String lower = q.toLowerCase(Locale.ROOT);
        List<String> any = new ArrayList<>(List.of(
                "lower(m.surname) LIKE :q ESCAPE '!'",
                "lower(m.first_names) LIKE :q ESCAPE '!'",
                "lower(m.membership_number) LIKE :q ESCAPE '!'",
                "lower(m.employee_number) LIKE :q ESCAPE '!'"));
        arguments.addValue("q", escaped(lower) + "%");
        if (allDigits(q)) {
            any.add("lower(m.membership_number) LIKE :qNumber ESCAPE '!'");
            arguments.addValue("qNumber", "m-" + lower + "%");
        }
        if (allDigits(q) && q.length() == 4) {
            any.add("upper(right(m.identity_number, 4)) = :qLastFour");
            arguments.addValue("qLastFour", q);
        } else if (q.length() >= 6 && q.chars().anyMatch(Character::isDigit)
                && q.chars().noneMatch(Character::isWhitespace)) {
            String whole = q.toUpperCase(Locale.ROOT);
            any.add("(upper(right(m.identity_number, 4)) = :qWholeLastFour AND upper(m.identity_number) = :qWhole)");
            arguments.addValue("qWholeLastFour", whole.substring(whole.length() - 4));
            arguments.addValue("qWhole", whole);
        }
        return "(" + String.join(" OR ", any) + ")";
    }

    /** A membership number by its start -- and, typed as digits alone, by its start after the {@code M-}. */
    private static String membershipNumber(String value, String name, MapSqlParameterSource arguments) {
        String lower = value.toLowerCase(Locale.ROOT);
        arguments.addValue(name, escaped(lower) + "%");
        if (!allDigits(value)) {
            return "lower(m.membership_number) LIKE :" + name + " ESCAPE '!'";
        }
        arguments.addValue(name + "Digits", "m-" + lower + "%");
        return "(lower(m.membership_number) LIKE :" + name + " ESCAPE '!' OR lower(m.membership_number) LIKE :"
                + name + "Digits ESCAPE '!')";
    }

    private static void exactly(List<String> where, MapSqlParameterSource arguments, String column, String name,
                                String value) {
        if (value != null) {
            where.add(column + " = :" + name);
            arguments.addValue(name, value);
        }
    }

    private static void startsWith(List<String> where, MapSqlParameterSource arguments, String column, String name,
                                   String value) {
        if (value != null) {
            where.add("lower(" + column + ") LIKE :" + name + " ESCAPE '!'");
            arguments.addValue(name, escaped(value.toLowerCase(Locale.ROOT)) + "%");
        }
    }

    /**
     * A number matched whole, or -- typed as four characters -- by its last four. The index of the last four answers
     * both: the whole number is compared only on the rows it finds.
     */
    private static void wholeOrLastFour(List<String> where, MapSqlParameterSource arguments, String lastFour,
                                        String whole, String name, String value) {
        if (value == null) {
            return;
        }
        arguments.addValue(name + "LastFour", value.length() > 4 ? value.substring(value.length() - 4) : value);
        if (value.length() == 4) {
            where.add(lastFour + " = :" + name + "LastFour");
        } else {
            where.add("(" + lastFour + " = :" + name + "LastFour AND " + whole + " = :" + name + "Whole)");
            arguments.addValue(name + "Whole", value);
        }
    }

    /** The caller's regions or areas, through the member's workplace; nothing to add for a caller who sees all. */
    private static void inScope(CallerScope scope, List<String> where, MapSqlParameterSource arguments) {
        if (scope.isAll()) {
            return;
        }
        boolean byArea = !scope.areaLetters().isEmpty();
        arguments.addValue("scopeCodes", byArea ? scope.areaLetters() : scope.regionCodes());
        where.add("m.workplace_id IN (SELECT w.id FROM workplaces w JOIN sasbo_areas a ON a.id = w.area_id"
                + (byArea ? " WHERE upper(a.letter) IN (:scopeCodes))"
                        : " JOIN sasbo_regions r ON r.id = a.region_id WHERE upper(r.code) IN (:scopeCodes))"));
    }

    /** A literal pattern's start: its {@code !}, {@code %} and {@code _} escaped with {@code !}. */
    static String escaped(String term) {
        return term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    private static boolean allDigits(String value) {
        return !value.isEmpty() && value.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    private static MemberRegisterPageDto.Row row(ResultSet rs, int rowNumber) throws SQLException {
        return new MemberRegisterPageDto.Row(
                rs.getInt("id"),
                rs.getString("membership_number"),
                rs.getString("employee_number"),
                rs.getString("title"),
                rs.getString("initials"),
                rs.getString("surname"),
                rs.getString("first_names"),
                rs.getString("bank_name"),
                rs.getString("branch"),
                rs.getString("status"));
    }
}

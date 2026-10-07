package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.enums.PaymentType;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollNames;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollParser;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The payroll module's bulk statements: what the comparison reads, in a handful of queries whatever the file's size,
 * and what it writes, in JDBC batches -- thirty of thirty-one entities generate their ids by IDENTITY, which stops
 * Hibernate batching, and a file is up to twenty thousand lines.
 *
 * <ul>
 *   <li>The employer's members: ONE statement -- a member is the employer's when their workplace's table code belongs
 *       to one of the SASBO employers linked to it, or when the employer text on their record is one of its names.</li>
 *   <li>The members an ID number on the file names: a statement per thousand ID numbers, on V26's unique index.</li>
 *   <li>The months before this one: two statements over the employer's files of the last year.</li>
 * </ul>
 */
@Repository
public class PayrollStore {

    /** How many values go in one {@code IN} list, and how many rows in one batch. */
    static final int CHUNK = 1000;

    private static final String MEMBER_COLUMNS = "m.id, m.membership_number, m.employee_number, m.identity_number, "
            + "m.initials, m.first_names, m.surname, m.status, m.payment_type, m.join_date, "
            + "m.arrears_balance_numeric AS arrears";

    private final NamedParameterJdbcTemplate jdbc;

    public PayrollStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------- what is compared

    /** Every member of the payroll employer, as the comparison needs them. */
    public List<PayrollMatcher.Member> employerMembers(long payrollEmployerId) {
        return jdbc.query("SELECT " + MEMBER_COLUMNS + " FROM members m "
                        + "WHERE m.workplace_id IN (SELECT w.id FROM workplaces w "
                        + "JOIN table_codes t ON t.id = w.table_code_id "
                        + "JOIN payroll_employer_employers pe ON pe.employer_id = t.employer_id "
                        + "WHERE pe.payroll_employer_id = :employer) "
                        + "OR upper(trim(m.bank_name)) IN (SELECT n.name_key FROM payroll_employer_names n "
                        + "WHERE n.payroll_employer_id = :employer) "
                        + "ORDER BY m.id",
                new MapSqlParameterSource("employer", payrollEmployerId), (rs, row) -> member(rs, true));
    }

    /**
     * The members the file's ID numbers name, by ID key: the employer's own from {@code employer}, everyone else from
     * the members on record -- on V26's unique index, a thousand ID numbers a statement.
     */
    public Map<String, PayrollMatcher.Member> membersByIdKey(Collection<String> idKeys,
                                                             Collection<PayrollMatcher.Member> employer) {
        Map<String, PayrollMatcher.Member> found = new HashMap<>();
        for (PayrollMatcher.Member member : employer) {
            if (member.idKey() != null) {
                found.putIfAbsent(member.idKey(), member);
            }
        }
        List<String> wanted = idKeys.stream().filter(key -> key != null && !found.containsKey(key)).distinct()
                .toList();
        for (int from = 0; from < wanted.size(); from += CHUNK) {
            List<String> chunk = wanted.subList(from, Math.min(wanted.size(), from + CHUNK));
            jdbc.query("SELECT " + MEMBER_COLUMNS + " FROM members m WHERE m.identity_number IN (:ids) "
                            + "AND m.identity_number IS NOT NULL AND m.identity_number <> ''",
                    new MapSqlParameterSource("ids", chunk), rs -> {
                        PayrollMatcher.Member member = member(rs, false);
                        if (member.idKey() != null) {
                            found.putIfAbsent(member.idKey(), member);
                        }
                    });
        }
        return found;
    }

    /**
     * What the employer's files of the year before {@code period} say of each member: the months they were missing,
     * the months caught up since, and how many months in a row up to the one before {@code period} they were missing.
     */
    public Map<Integer, PayrollMatcher.History> history(long payrollEmployerId, YearMonth period) {
        LocalDate from = period.minusMonths(za.co.titandynamix.sasbo_online_backend.payroll.PayrollLimits.HISTORY_MONTHS)
                .atDay(1);
        MapSqlParameterSource args = new MapSqlParameterSource("employer", payrollEmployerId)
                .addValue("from", Date.valueOf(from)).addValue("period", Date.valueOf(period.atDay(1)));
        Map<Integer, Set<YearMonth>> missed = new HashMap<>();
        jdbc.query("SELECT d.member_id, f.period FROM payroll_discrepancies d JOIN payroll_files f ON f.id = d.file_id "
                        + "WHERE d.payroll_employer_id = :employer AND d.type = 'MISSING_FROM_FILE' "
                        + "AND d.member_id IS NOT NULL AND f.status <> 'SUPERSEDED' "
                        + "AND (d.outcome IS NULL OR d.outcome NOT IN ('SUPERSEDED', 'NO_LONGER_APPLIES')) "
                        + "AND f.period >= :from AND f.period < :period", args,
                rs -> {
                    missed.computeIfAbsent(rs.getInt("member_id"), id -> new TreeSet<>())
                            .add(YearMonth.from(rs.getDate("period").toLocalDate()));
                });
        Map<Integer, Integer> caughtUp = new HashMap<>();
        jdbc.query("SELECT pm.member_id, pm.months FROM payroll_matches pm JOIN payroll_files f ON f.id = pm.file_id "
                        + "WHERE f.payroll_employer_id = :employer AND f.status <> 'SUPERSEDED' "
                        + "AND pm.amount_status = 'CATCH_UP' AND pm.member_id IS NOT NULL "
                        + "AND f.period >= :from AND f.period < :period", args,
                rs -> {
                    caughtUp.merge(rs.getInt("member_id"), Math.max(0, rs.getInt("months") - 1), Integer::sum);
                });
        Map<Integer, PayrollMatcher.History> history = new HashMap<>();
        Set<Integer> members = new HashSet<>(missed.keySet());
        members.addAll(caughtUp.keySet());
        for (Integer member : members) {
            Set<YearMonth> months = missed.getOrDefault(member, Set.of());
            int inARow = 0;
            YearMonth month = period.minusMonths(1);
            while (months.contains(month)) {
                inARow++;
                month = month.minusMonths(1);
            }
            history.put(member, new PayrollMatcher.History(months.size(), caughtUp.getOrDefault(member, 0), inARow));
        }
        return history;
    }

    private static PayrollMatcher.Member member(ResultSet rs, boolean atEmployer) throws SQLException {
        String status = rs.getString("status");
        String paymentType = rs.getString("payment_type");
        PaymentType payment = paymentTypeOf(paymentType);
        String initials = rs.getString("initials");
        if (initials == null || initials.isBlank()) {
            initials = PayrollNames.initialsOf(rs.getString("first_names"));
        }
        Date joined = rs.getDate("join_date");
        return new PayrollMatcher.Member(rs.getInt("id"), rs.getString("membership_number"),
                PayrollKeys.staffKey(rs.getString("employee_number")), PayrollKeys.idKey(rs.getString("identity_number")),
                initials, rs.getString("surname"), MemberStatuses.kindOf(status) == MemberStatusKind.LEAVING,
                payment == PaymentType.PAYROLL, payment == PaymentType.DEBIT_ORDER, atEmployer,
                joined == null ? null : joined.toLocalDate(), rs.getBigDecimal("arrears"));
    }

    /** A member's payment type as the record holds it; a value the enum does not know (Cash) is neither. */
    private static PaymentType paymentTypeOf(String stored) {
        try {
            return PaymentType.fromString(stored);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    // ----------------------------------------------------------------------------------------------- the lines

    /** The file's lines, in batches. 🔒 The only place a staff number, an ID number or a name is written. */
    public void insertRows(long fileId, List<PayrollParser.Line> lines) {
        String sql = "INSERT INTO payroll_rows (file_id, line_number, staff_number, staff_key, id_number, initials, "
                + "surname, name_key, amount, for_period, valid, problem, raw) VALUES (:file, :line, :staff, :staffKey, "
                + ":id, :initials, :surname, :nameKey, :amount, :forPeriod, :valid, :problem, :raw)";
        for (int from = 0; from < lines.size(); from += CHUNK) {
            List<PayrollParser.Line> chunk = lines.subList(from, Math.min(lines.size(), from + CHUNK));
            SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                PayrollParser.Line line = chunk.get(i);
                batch[i] = new MapSqlParameterSource("file", fileId).addValue("line", line.lineNumber())
                        .addValue("staff", line.staffNumber()).addValue("staffKey", line.staffKey())
                        .addValue("id", line.idNumber()).addValue("initials", cap(line.name().initials(), 20))
                        .addValue("surname", cap(line.name().surname(), 100))
                        .addValue("nameKey", PayrollNames.storedKey(line.name()))
                        .addValue("amount", line.amount())
                        .addValue("forPeriod", line.forPeriod() == null ? null : Date.valueOf(line.forPeriod().atDay(1)))
                        .addValue("valid", line.valid()).addValue("problem", cap(line.problem(), 200))
                        .addValue("raw", line.raw());
            }
            jdbc.batchUpdate(sql, batch);
        }
    }

    /** Each line's row id, by line number: one statement. */
    public Map<Integer, Long> rowIds(long fileId) {
        Map<Integer, Long> ids = new HashMap<>();
        jdbc.query("SELECT id, line_number FROM payroll_rows WHERE file_id = :file",
                new MapSqlParameterSource("file", fileId), rs -> {
                    ids.put(rs.getInt("line_number"), rs.getLong("id"));
                });
        return ids;
    }

    /** What the comparison made of each line, in batches. */
    public void insertMatches(long fileId, List<PayrollMatcher.LineMatch> matches, Map<Integer, Long> rowIds) {
        String sql = "INSERT INTO payroll_matches (file_id, row_id, member_id, match_rule, confidence, reason, "
                + "expected_amount, amount_status, months, rate_unknown_for) VALUES (:file, :row, :member, :rule, "
                + ":confidence, :reason, :expected, :status, :months, :rateUnknownFor)";
        List<PayrollMatcher.LineMatch> written = matches.stream().filter(m -> rowIds.containsKey(m.lineNumber()))
                .toList();
        for (int from = 0; from < written.size(); from += CHUNK) {
            List<PayrollMatcher.LineMatch> chunk = written.subList(from, Math.min(written.size(), from + CHUNK));
            SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                PayrollMatcher.LineMatch match = chunk.get(i);
                batch[i] = new MapSqlParameterSource("file", fileId).addValue("row", rowIds.get(match.lineNumber()))
                        .addValue("member", match.member() == null ? null : match.member().id())
                        .addValue("rule", match.rule().name())
                        .addValue("confidence", match.confidence() == null ? null : match.confidence().name())
                        .addValue("reason", cap(match.reason(), 200)).addValue("expected", match.expectedAmount())
                        .addValue("status", match.amountStatus() == null ? null : match.amountStatus().name())
                        .addValue("months", match.months())
                        .addValue("rateUnknownFor", match.rateUnknownFor() == null ? null
                                : Date.valueOf(match.rateUnknownFor().atDay(1)));
            }
            jdbc.batchUpdate(sql, batch);
        }
    }

    /**
     * The file's matches and lines gone -- before they are written again, or a year on -- with every discrepancy
     * letting go of its line first (it keeps the line's number).
     *
     * @return how many lines were deleted
     */
    public int deleteLines(long fileId) {
        MapSqlParameterSource file = new MapSqlParameterSource("file", fileId);
        jdbc.update("UPDATE payroll_discrepancies SET row_id = NULL WHERE file_id = :file AND row_id IS NOT NULL", file);
        jdbc.update("DELETE FROM payroll_matches WHERE file_id = :file", file);
        return jdbc.update("DELETE FROM payroll_rows WHERE file_id = :file", file);
    }

    /**
     * A year on (PAY-39): the file's bytes and lines deleted, and the protected query lists produced from it; its
     * discrepancies, their outcomes and the record that a list was produced kept. One transaction, the caller's.
     *
     * @return how many lines were deleted
     */
    public int purge(long fileId, Instant at) {
        int rows = deleteLines(fileId);
        jdbc.update("UPDATE payroll_files SET content = NULL, status = 'PURGED', purged_at = :at, "
                        + "version = version + 1 WHERE id = :file AND status <> 'PURGED'",
                new MapSqlParameterSource("file", fileId).addValue("at", Timestamp.from(at)));
        jdbc.update("UPDATE payroll_query_files SET content = NULL, purged_at = :at WHERE file_id = :file "
                        + "AND content IS NOT NULL", new MapSqlParameterSource("file", fileId).addValue("at", Timestamp.from(at)));
        return rows;
    }

    /** A produced query list downloaded once more: counted in the statement, so two at once count two. */
    public int countDownload(long queryFileId, Instant at) {
        return jdbc.update("UPDATE payroll_query_files SET downloads = downloads + 1, last_downloaded_at = :at "
                + "WHERE id = :id", new MapSqlParameterSource("id", queryFileId).addValue("at", Timestamp.from(at)));
    }

    // ------------------------------------------------------------------------------------------- discrepancies

    /** A discrepancy on record, as a re-run needs to find it again. */
    public record Existing(long id, String type, String subjectKey, String status, String outcome, Long queryId) {
    }

    public List<Existing> discrepanciesOf(long fileId) {
        return jdbc.query("SELECT id, type, subject_key, status, outcome, query_id FROM payroll_discrepancies "
                        + "WHERE file_id = :file", new MapSqlParameterSource("file", fileId),
                (rs, row) -> new Existing(rs.getLong("id"), rs.getString("type"), rs.getString("subject_key"),
                        rs.getString("status"), rs.getString("outcome"), rs.getObject("query_id", Long.class)));
    }

    /** New discrepancies, OPEN, in batches. */
    public void insertDiscrepancies(long fileId, long employerId, LocalDate period, List<PayrollMatcher.Found> found,
                                    Map<Integer, Long> rowIds, Instant at) {
        String sql = "INSERT INTO payroll_discrepancies (file_id, payroll_employer_id, period, type, status, member_id, "
                + "row_id, line_number, subject_key, amount_on_file, expected_amount, difference, months, detail, "
                + "created_at, version) VALUES (:file, :employer, :period, :type, 'OPEN', :member, :row, :line, "
                + ":subject, :amount, :expected, :difference, :months, :detail, :at, 0)";
        for (int from = 0; from < found.size(); from += CHUNK) {
            List<PayrollMatcher.Found> chunk = found.subList(from, Math.min(found.size(), from + CHUNK));
            SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                batch[i] = discrepancyArgs(chunk.get(i), rowIds).addValue("file", fileId)
                        .addValue("employer", employerId).addValue("period", Date.valueOf(period))
                        .addValue("at", Timestamp.from(at));
            }
            jdbc.batchUpdate(sql, batch);
        }
    }

    /**
     * Discrepancies found again: their figures brought up to date, their notes and status kept -- except one the system
     * had closed (no longer found, or superseded), which opens again, QUERIED if it is on a query.
     */
    public void updateDiscrepancies(Map<Long, PayrollMatcher.Found> found, Map<Long, Boolean> reopen,
                                    Map<Long, Long> queryOf, Map<Integer, Long> rowIds) {
        String sql = "UPDATE payroll_discrepancies SET member_id = :member, row_id = :row, line_number = :line, "
                + "amount_on_file = :amount, expected_amount = :expected, difference = :difference, months = :months, "
                + "detail = :detail, status = CASE WHEN :reopen = TRUE THEN :reopenAs ELSE status END, "
                + "outcome = CASE WHEN :reopen = TRUE THEN NULL ELSE outcome END, "
                + "resolved_by = CASE WHEN :reopen = TRUE THEN NULL ELSE resolved_by END, "
                + "resolved_at = CASE WHEN :reopen = TRUE THEN NULL ELSE resolved_at END, "
                + "version = version + 1 WHERE id = :id";
        List<Map.Entry<Long, PayrollMatcher.Found>> entries = new java.util.ArrayList<>(found.entrySet());
        for (int from = 0; from < entries.size(); from += CHUNK) {
            List<Map.Entry<Long, PayrollMatcher.Found>> chunk = entries.subList(from,
                    Math.min(entries.size(), from + CHUNK));
            SqlParameterSource[] batch = new SqlParameterSource[chunk.size()];
            for (int i = 0; i < chunk.size(); i++) {
                long id = chunk.get(i).getKey();
                boolean again = reopen.getOrDefault(id, false);
                batch[i] = discrepancyArgs(chunk.get(i).getValue(), rowIds).addValue("id", id)
                        .addValue("reopen", again)
                        .addValue("reopenAs", queryOf.get(id) == null ? "OPEN" : "QUERIED");
            }
            jdbc.batchUpdate(sql, batch);
        }
    }

    /** Discrepancies closed by the system -- no longer found, superseded, the balance received. */
    public void resolveBySystem(Collection<Long> ids, String outcome, String actor, Instant at) {
        List<Long> list = List.copyOf(ids);
        for (int from = 0; from < list.size(); from += CHUNK) {
            jdbc.update("UPDATE payroll_discrepancies SET status = 'RESOLVED', outcome = :outcome, resolved_by = :actor, "
                            + "resolved_at = :at, version = version + 1 WHERE id IN (:ids) AND status <> 'RESOLVED'",
                    new MapSqlParameterSource("ids", list.subList(from, Math.min(list.size(), from + CHUNK)))
                            .addValue("outcome", outcome).addValue("actor", actor).addValue("at", Timestamp.from(at)));
        }
    }

    private static MapSqlParameterSource discrepancyArgs(PayrollMatcher.Found f, Map<Integer, Long> rowIds) {
        return new MapSqlParameterSource("type", f.type().name()).addValue("member", f.memberId())
                .addValue("row", f.lineNumber() == null ? null : rowIds.get(f.lineNumber()))
                .addValue("line", f.lineNumber()).addValue("subject", f.subjectKey())
                .addValue("amount", f.amountOnFile()).addValue("expected", f.expectedAmount())
                .addValue("difference", f.difference()).addValue("months", f.months())
                .addValue("detail", cap(f.detail(), 300));
    }

    /**
     * Discrepancies put on a query, with the condition they were read under -- open and on no query -- so a query
     * raised at the same moment takes none twice.
     *
     * @return how many were put on it
     */
    public int putOnQuery(long queryId, Collection<Long> ids) {
        return jdbc.update("UPDATE payroll_discrepancies SET query_id = :query, status = 'QUERIED', "
                        + "version = version + 1 WHERE id IN (:ids) AND query_id IS NULL AND status = 'OPEN'",
                new MapSqlParameterSource("query", queryId).addValue("ids", List.copyOf(ids)));
    }

    /** A file's discrepancy counts written onto it: two sub-selects, one statement. */
    public void recount(long fileId) {
        jdbc.update("UPDATE payroll_files SET discrepancy_count = (SELECT count(*) FROM payroll_discrepancies d "
                        + "WHERE d.file_id = :file), open_count = (SELECT count(*) FROM payroll_discrepancies d "
                        + "WHERE d.file_id = :file AND d.status <> 'RESOLVED') WHERE id = :file",
                new MapSqlParameterSource("file", fileId));
    }

    /**
     * Claims a file for a comparison: its version moves on, so a second comparison of the same file at the same
     * moment finds it moved and is refused -- the first statement of the transaction, so the row is locked first.
     *
     * @return whether the file was at {@code version} and live
     */
    public boolean claim(long fileId, int version) {
        return jdbc.update("UPDATE payroll_files SET version = version + 1 WHERE id = :file AND version = :version "
                        + "AND status = 'RECONCILED'",
                new MapSqlParameterSource("file", fileId).addValue("version", version)) == 1;
    }

    /** What a comparison found, written onto the file. */
    public void writeFigures(long fileId, Map<String, Object> figures) {
        MapSqlParameterSource args = new MapSqlParameterSource("file", fileId);
        figures.forEach(args::addValue);
        jdbc.update("UPDATE payroll_files SET sheet_name = :sheet, heading_row = :headingRow, headings = :headings, "
                + "heading_cells = :headingCells, column_mapping = :mapping, stated_total = :statedTotal, "
                + "row_count = :rows, unreadable_count = :unreadable, rows_not_read = :notRead, counts = :counts, "
                + "matched_count = :matched, file_total = :fileTotal, rate_amount = :rate, "
                + "rate_effective_from = :rateFrom, expected_total = :expectedTotal, admin_fee_percent = :fee, "
                + "runs = runs + 1, last_run_at = :at, last_run_by = :by WHERE id = :file", args);
    }

    /** The money received for an employer's month, and how many payments made it. */
    public java.util.Map.Entry<BigDecimal, Integer> received(long employerId, LocalDate period) {
        return jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) AS received, count(*) AS payments "
                        + "FROM payroll_payments WHERE payroll_employer_id = :employer AND period = :period",
                new MapSqlParameterSource("employer", employerId).addValue("period", Date.valueOf(period)),
                (rs, row) -> Map.entry(rs.getBigDecimal("received"), rs.getInt("payments")));
    }

    /** The subscription in force for each month asked, from the dated rates: one statement. */
    public Map<YearMonth, BigDecimal> rates() {
        Map<YearMonth, BigDecimal> rates = new java.util.TreeMap<>();
        jdbc.query("SELECT effective_from, amount FROM acb_rates ORDER BY effective_from", new MapSqlParameterSource(),
                rs -> {
                    rates.put(YearMonth.from(rs.getDate("effective_from").toLocalDate()), rs.getBigDecimal("amount"));
                });
        return rates;
    }

    private static String cap(String text, int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }
}

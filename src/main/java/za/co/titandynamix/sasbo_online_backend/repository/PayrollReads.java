package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollDiscrepancyDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerRef;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPage;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollPaymentDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollQueryFileDto;
import za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollRowDto;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollDiscrepancyType;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollPaymentStatus;
import za.co.titandynamix.sasbo_online_backend.enums.PayrollQueryAction;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollKeys;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMatcher;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollMoney;
import za.co.titandynamix.sasbo_online_backend.payroll.PayrollNames;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What the payroll screens read, a page at a time, each page in a fixed number of statements however long it is: the
 * page with its joins, its count, and -- for discrepancies -- the notes of the page's rows in one more. Every filter is
 * a named parameter; the statement's text is built from fixed fragments only.
 */
@Repository
public class PayrollReads {

    /** Discrepancies list in the order of their kinds, as the enum lists them -- names of code, never input. */
    private static final String TYPE_ORDER = "CASE d.type " + Arrays.stream(PayrollDiscrepancyType.values())
            .map(t -> "WHEN '" + t.name() + "' THEN " + t.ordinal()).collect(Collectors.joining(" ")) + " END";

    private final NamedParameterJdbcTemplate jdbc;

    public PayrollReads(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------- files

    /** A file as the summary needs it -- every figure, never the bytes. */
    public record FileRow(long id, PayrollEmployerRef employer, String employerLayoutKey, LocalDate period,
                          String originalName, String entry, String format, int sizeBytes, String sha256,
                          String status, String uploadedBy, java.time.Instant uploadedAt, LocalDate retainUntil,
                          String sheetName, Integer headingRow, String headings, String headingCells,
                          String columnMapping, BigDecimal statedTotal, int rowCount, int unreadableCount,
                          int rowsNotRead, String counts, int matchedCount, int discrepancyCount, int openCount,
                          BigDecimal fileTotal, BigDecimal rateAmount, LocalDate rateEffectiveFrom,
                          BigDecimal expectedTotal, BigDecimal adminFeePercent, int runs, java.time.Instant lastRunAt,
                          String lastRunBy, int version, BigDecimal received, int payments) {

        /** Where the payment check stands for this file. */
        public PayrollPaymentStatus paymentStatus() {
            return PayrollMoney.status(payments > 0, received,
                    PayrollMoney.expectedPayment(fileTotal, adminFeePercent));
        }
    }

    private static final String FILE_COLUMNS = "f.id, f.payroll_employer_id, e.code AS employer_code, "
            + "e.name AS employer_name, e.layout_key AS employer_layout, f.period, f.original_name, f.entry, f.format, "
            + "f.size_bytes, f.sha256, f.status, f.uploaded_by, f.uploaded_at, f.retain_until, f.sheet_name, "
            + "f.heading_row, f.headings, f.heading_cells, f.column_mapping, f.stated_total, f.row_count, "
            + "f.unreadable_count, f.rows_not_read, f.counts, f.matched_count, f.discrepancy_count, f.open_count, "
            + "f.file_total, f.rate_amount, f.rate_effective_from, f.expected_total, f.admin_fee_percent, f.runs, "
            + "f.last_run_at, f.last_run_by, f.version, "
            + "(SELECT COALESCE(SUM(p.amount), 0) FROM payroll_payments p WHERE p.payroll_employer_id = "
            + "f.payroll_employer_id AND p.period = f.period) AS received, "
            + "(SELECT count(*) FROM payroll_payments p WHERE p.payroll_employer_id = f.payroll_employer_id "
            + "AND p.period = f.period) AS payments";

    public Optional<FileRow> file(long id) {
        List<FileRow> found = jdbc.query("SELECT " + FILE_COLUMNS + " FROM payroll_files f "
                + "JOIN payroll_employers e ON e.id = f.payroll_employer_id WHERE f.id = :id",
                new MapSqlParameterSource("id", id), (rs, row) -> fileRow(rs));
        return found.stream().findFirst();
    }

    /** The files, newest month first, then by employer as Stephne listed them. */
    public PayrollPage<PayrollFileDto> files(Long employerId, LocalDate period, String status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (employerId != null) {
            where.append(" AND f.payroll_employer_id = :employer");
            args.addValue("employer", employerId);
        }
        if (period != null) {
            where.append(" AND f.period = :period");
            args.addValue("period", Date.valueOf(period));
        }
        if (status != null) {
            where.append(" AND f.status = :status");
            args.addValue("status", status);
        }
        String from = " FROM payroll_files f JOIN payroll_employers e ON e.id = f.payroll_employer_id" + where;
        Long total = jdbc.queryForObject("SELECT count(*)" + from, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<PayrollFileDto> content = jdbc.query("SELECT " + FILE_COLUMNS + from
                        + " ORDER BY f.period DESC, e.ordinal, f.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, row) -> fileDto(fileRow(rs)));
        return PayrollPage.of(content, page, size, total == null ? 0 : total);
    }

    public static PayrollFileDto fileDto(FileRow f) {
        return new PayrollFileDto(f.id(), f.employer(), YearMonth.from(f.period()).toString(), f.originalName(),
                f.entry(), f.format(), f.sizeBytes(), f.sha256(), f.status(), f.uploadedBy(),
                f.uploadedAt().toString(), f.retainUntil().toString(), f.rowCount(), f.matchedCount(),
                f.discrepancyCount(), f.openCount(), PayrollKeys.money(f.fileTotal()), f.paymentStatus().name(),
                f.lastRunAt() == null ? null : f.lastRunAt().toString(), f.version());
    }

    private static FileRow fileRow(ResultSet rs) throws SQLException {
        return new FileRow(rs.getLong("id"), new PayrollEmployerRef(rs.getLong("payroll_employer_id"),
                rs.getString("employer_code"), rs.getString("employer_name")), rs.getString("employer_layout"),
                rs.getDate("period").toLocalDate(), rs.getString("original_name"), rs.getString("entry"),
                rs.getString("format"), rs.getInt("size_bytes"), rs.getString("sha256"), rs.getString("status"),
                rs.getString("uploaded_by"), instant(rs, "uploaded_at"), rs.getDate("retain_until").toLocalDate(),
                rs.getString("sheet_name"), (Integer) rs.getObject("heading_row"), rs.getString("headings"),
                rs.getString("heading_cells"), rs.getString("column_mapping"), rs.getBigDecimal("stated_total"),
                rs.getInt("row_count"), rs.getInt("unreadable_count"), rs.getInt("rows_not_read"),
                rs.getString("counts"), rs.getInt("matched_count"), rs.getInt("discrepancy_count"),
                rs.getInt("open_count"), rs.getBigDecimal("file_total"), rs.getBigDecimal("rate_amount"),
                date(rs, "rate_effective_from"), rs.getBigDecimal("expected_total"),
                rs.getBigDecimal("admin_fee_percent"), rs.getInt("runs"), instant(rs, "last_run_at"),
                rs.getString("last_run_by"), rs.getInt("version"), rs.getBigDecimal("received"),
                rs.getInt("payments"));
    }

    // -------------------------------------------------------------------------------------------------- lines

    /** A file's lines and what the comparison made of each, in file order. */
    public PayrollPage<PayrollRowDto> rows(long fileId, String show, int page, int size) {
        String filter = switch (show) {
            case "MATCHED" -> " AND pm.member_id IS NOT NULL";
            case "NOT_MATCHED" -> " AND r.valid = TRUE AND pm.member_id IS NULL";
            case "UNREADABLE" -> " AND r.valid = FALSE";
            default -> "";
        };
        String from = " FROM payroll_rows r LEFT JOIN payroll_matches pm ON pm.row_id = r.id "
                + "LEFT JOIN members m ON m.id = pm.member_id WHERE r.file_id = :file" + filter;
        MapSqlParameterSource args = new MapSqlParameterSource("file", fileId);
        Long total = jdbc.queryForObject("SELECT count(*)" + from, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<PayrollRowDto> content = jdbc.query("SELECT r.id, r.line_number, r.staff_number, r.id_number, "
                + "r.initials, r.surname, r.amount, r.valid, r.problem, pm.member_id, pm.match_rule, pm.confidence, "
                + "pm.reason, pm.expected_amount, pm.amount_status, pm.months, pm.rate_unknown_for, "
                + "m.membership_number, m.initials AS m_initials, m.first_names AS m_first, m.surname AS m_surname"
                + from + " ORDER BY r.line_number LIMIT :limit OFFSET :offset", args, (rs, row) -> {
                    Integer member = (Integer) rs.getObject("member_id");
                    PayrollRowDto.Match match = member == null ? null : new PayrollRowDto.Match(member,
                            rs.getString("membership_number"), memberName(rs, "m_initials", "m_first", "m_surname"),
                            rs.getString("match_rule"), rs.getString("confidence"), rs.getString("reason"),
                            PayrollKeys.money(rs.getBigDecimal("expected_amount")), rs.getString("amount_status"),
                            (Integer) rs.getObject("months"), month(rs, "rate_unknown_for"));
                    return new PayrollRowDto(rs.getLong("id"), rs.getInt("line_number"), rs.getString("staff_number"),
                            PayrollKeys.masked(rs.getString("id_number")), rs.getString("initials"),
                            rs.getString("surname"), PayrollKeys.money(rs.getBigDecimal("amount")),
                            rs.getBoolean("valid"), rs.getString("problem"), match);
                });
        return PayrollPage.of(content, page, size, total == null ? 0 : total);
    }

    /** Every line of a file with its match -- for the full report, read a thousand at a time by the caller. */
    public PayrollPage<PayrollRowDto> allRows(long fileId, int page, int size) {
        return rows(fileId, "ALL", page, size);
    }

    // ---------------------------------------------------------------------------------------------- discrepancies

    /** The filters a discrepancies list takes; {@code null} where not asked for. */
    public record Filter(Long employerId, LocalDate period, Long fileId, PayrollDiscrepancyType type,
                         Collection<PayrollDiscrepancyStatus> statuses) {
    }

    private static final String DISCREPANCY_SELECT = "SELECT d.id, d.file_id, d.payroll_employer_id, e.code AS e_code, "
            + "e.name AS e_name, d.period, d.type, d.status, d.detail, d.member_id, d.row_id, d.line_number, "
            + "d.amount_on_file, d.expected_amount, d.difference, d.months, d.query_id, d.outcome, d.resolved_by, "
            + "d.resolved_at, d.created_at, d.version, m.membership_number, m.initials AS m_initials, "
            + "m.first_names AS m_first, m.surname AS m_surname, m.status AS m_status, m.payment_type AS m_payment, "
            + "m.employee_number AS m_staff, "
            + "r.line_number AS r_line, r.staff_number AS r_staff, r.id_number AS r_id, r.initials AS r_initials, "
            + "r.surname AS r_surname, r.amount AS r_amount, q.raised_at AS q_raised";

    private static final String DISCREPANCY_FROM = " FROM payroll_discrepancies d "
            + "JOIN payroll_employers e ON e.id = d.payroll_employer_id "
            + "LEFT JOIN members m ON m.id = d.member_id "
            + "LEFT JOIN payroll_rows r ON r.id = d.row_id "
            + "LEFT JOIN payroll_queries q ON q.id = d.query_id";

    public PayrollPage<PayrollDiscrepancyDto> discrepancies(Filter filter, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (filter.employerId() != null) {
            where.append(" AND d.payroll_employer_id = :employer");
            args.addValue("employer", filter.employerId());
        }
        if (filter.period() != null) {
            where.append(" AND d.period = :period");
            args.addValue("period", Date.valueOf(filter.period()));
        }
        if (filter.fileId() != null) {
            where.append(" AND d.file_id = :file");
            args.addValue("file", filter.fileId());
        }
        if (filter.type() != null) {
            where.append(" AND d.type = :type");
            args.addValue("type", filter.type().name());
        }
        if (filter.statuses() != null && !filter.statuses().isEmpty()) {
            where.append(" AND d.status IN (:statuses)");
            args.addValue("statuses", filter.statuses().stream().map(Enum::name).toList());
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM payroll_discrepancies d" + where, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<Map.Entry<Long, PartialDiscrepancy>> rows = jdbc.query(DISCREPANCY_SELECT + DISCREPANCY_FROM + where
                        + " ORDER BY d.period DESC, e.ordinal, d.file_id DESC, " + TYPE_ORDER
                        + ", d.line_number NULLS LAST, d.id LIMIT :limit OFFSET :offset", args,
                (rs, row) -> Map.entry(rs.getLong("id"), partial(rs)));
        Map<Long, List<PayrollDiscrepancyDto.Note>> notes = notesOf(rows.stream().map(Map.Entry::getKey).toList());
        List<PayrollDiscrepancyDto> content = new ArrayList<>();
        for (Map.Entry<Long, PartialDiscrepancy> row : rows) {
            content.add(row.getValue().complete(notes.getOrDefault(row.getKey(), List.of()), false));
        }
        return PayrollPage.of(content, page, size, total == null ? 0 : total);
    }

    /**
     * Every discrepancy of a file, with its notes, in the report's order -- for the downloads, which list them all. A
     * file's are bounded by its lines and its employer's members; read in two statements, notes a thousand at a time.
     */
    public List<PayrollDiscrepancyDto> allOfFile(long fileId) {
        List<Map.Entry<Long, PartialDiscrepancy>> rows = jdbc.query(DISCREPANCY_SELECT + DISCREPANCY_FROM
                        + " WHERE d.file_id = :file ORDER BY " + TYPE_ORDER + ", d.line_number NULLS LAST, d.id",
                new MapSqlParameterSource("file", fileId), (rs, row) -> Map.entry(rs.getLong("id"), partial(rs)));
        List<Long> ids = rows.stream().map(Map.Entry::getKey).toList();
        Map<Long, List<PayrollDiscrepancyDto.Note>> notes = new HashMap<>();
        for (int from = 0; from < ids.size(); from += 1000) {
            notes.putAll(notesOf(ids.subList(from, Math.min(ids.size(), from + 1000))));
        }
        List<PayrollDiscrepancyDto> all = new ArrayList<>();
        for (Map.Entry<Long, PartialDiscrepancy> row : rows) {
            all.add(row.getValue().complete(notes.getOrDefault(row.getKey(), List.of()), false));
        }
        return all;
    }

    /** One discrepancy with every note. */
    public Optional<PayrollDiscrepancyDto> discrepancy(long id) {
        List<PartialDiscrepancy> found = jdbc.query(DISCREPANCY_SELECT + DISCREPANCY_FROM + " WHERE d.id = :id",
                new MapSqlParameterSource("id", id), (rs, row) -> partial(rs));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(found.get(0).complete(notesOf(List.of(id)).getOrDefault(id, List.of()), true));
    }

    /** A discrepancy as read, before its notes are put with it. */
    private record PartialDiscrepancy(long id, long fileId, PayrollEmployerRef employer, YearMonth period,
                                      PayrollDiscrepancyType type, PayrollDiscrepancyStatus status, String detail,
                                      PayrollDiscrepancyDto.Member member, PayrollDiscrepancyDto.Row row,
                                      BigDecimal amountOnFile, BigDecimal expected, BigDecimal difference,
                                      Integer months, PayrollDiscrepancyDto.QueryRef query, PayrollOutcome outcome,
                                      String resolvedBy, java.time.Instant resolvedAt, java.time.Instant createdAt,
                                      int version) {

        PayrollDiscrepancyDto complete(List<PayrollDiscrepancyDto.Note> notes, boolean withNotes) {
            PayrollDiscrepancyDto.Resolution resolution = outcome == null ? null
                    : new PayrollDiscrepancyDto.Resolution(outcome.name(), outcome.label(), resolvedBy,
                            resolvedAt == null ? null : resolvedAt.toString());
            PayrollQueryAction action = type.queryAction();
            return new PayrollDiscrepancyDto(id, fileId, employer, period.toString(), type.name(), type.label(),
                    type.rule().isEmpty() ? null : type.rule(), type.whatToDo(), status.name(), detail, member, row,
                    PayrollKeys.money(amountOnFile), PayrollKeys.money(expected), PayrollKeys.money(difference),
                    months, action == null ? null : action.name(), query,
                    type.outcomes().stream().map(Enum::name).toList(), resolution, notes.size(),
                    notes.isEmpty() ? null : notes.get(notes.size() - 1), withNotes ? notes : null,
                    createdAt.toString(), version);
        }
    }

    private static PartialDiscrepancy partial(ResultSet rs) throws SQLException {
        Integer memberId = (Integer) rs.getObject("member_id");
        PayrollDiscrepancyDto.Member member = memberId == null ? null : new PayrollDiscrepancyDto.Member(memberId,
                rs.getString("membership_number"), memberName(rs, "m_initials", "m_first", "m_surname"),
                rs.getString("m_status"), rs.getString("m_payment"), rs.getString("m_staff"));
        Object rowLine = rs.getObject("r_line");
        PayrollDiscrepancyDto.Row row = rowLine == null ? null : new PayrollDiscrepancyDto.Row(rs.getInt("r_line"),
                rs.getString("r_staff"), PayrollKeys.masked(rs.getString("r_id")), rs.getString("r_initials"),
                rs.getString("r_surname"), PayrollKeys.money(rs.getBigDecimal("r_amount")));
        Long queryId = rs.getObject("query_id", Long.class);
        java.time.Instant raised = instant(rs, "q_raised");
        String outcome = rs.getString("outcome");
        return new PartialDiscrepancy(rs.getLong("id"), rs.getLong("file_id"), new PayrollEmployerRef(
                rs.getLong("payroll_employer_id"), rs.getString("e_code"), rs.getString("e_name")),
                YearMonth.from(rs.getDate("period").toLocalDate()), PayrollDiscrepancyType.valueOf(rs.getString("type")),
                PayrollDiscrepancyStatus.valueOf(rs.getString("status")), rs.getString("detail"), member, row,
                rs.getBigDecimal("amount_on_file"), rs.getBigDecimal("expected_amount"), rs.getBigDecimal("difference"),
                (Integer) rs.getObject("months"), queryId == null ? null
                        : new PayrollDiscrepancyDto.QueryRef(queryId, raised == null ? null : raised.toString()),
                outcome == null ? null : PayrollOutcome.valueOf(outcome), rs.getString("resolved_by"),
                instant(rs, "resolved_at"), instant(rs, "created_at"), rs.getInt("version"));
    }

    /** The notes of the discrepancies given, oldest first: one statement. */
    private Map<Long, List<PayrollDiscrepancyDto.Note>> notesOf(List<Long> ids) {
        Map<Long, List<PayrollDiscrepancyDto.Note>> notes = new HashMap<>();
        if (ids.isEmpty()) {
            return notes;
        }
        jdbc.query("SELECT id, discrepancy_id, note, written_by, written_at FROM payroll_discrepancy_notes "
                        + "WHERE discrepancy_id IN (:ids) ORDER BY discrepancy_id, written_at, id",
                new MapSqlParameterSource("ids", ids), rs -> {
                    notes.computeIfAbsent(rs.getLong("discrepancy_id"), key -> new ArrayList<>())
                            .add(new PayrollDiscrepancyDto.Note(rs.getLong("id"), rs.getString("note"),
                                    rs.getString("written_by"), instant(rs, "written_at").toString()));
                });
        return notes;
    }

    /** A file's discrepancies counted by kind and status: one statement. */
    public Map<PayrollDiscrepancyType, int[]> typeCounts(long fileId) {
        Map<PayrollDiscrepancyType, int[]> counts = new java.util.EnumMap<>(PayrollDiscrepancyType.class);
        for (PayrollDiscrepancyType type : PayrollDiscrepancyType.values()) {
            counts.put(type, new int[3]);
        }
        jdbc.query("SELECT type, status, count(*) AS n FROM payroll_discrepancies WHERE file_id = :file "
                        + "GROUP BY type, status", new MapSqlParameterSource("file", fileId), rs -> {
                    counts.get(PayrollDiscrepancyType.valueOf(rs.getString("type")))[
                            PayrollDiscrepancyStatus.valueOf(rs.getString("status")).ordinal()] = rs.getInt("n");
                });
        return counts;
    }

    // ------------------------------------------------------------------------------------------------- payments

    private static final String PAYMENT_SELECT = "SELECT p.id, p.payroll_employer_id, e.code, e.name, p.period, "
            + "p.amount, p.received_on, p.reference, p.recorded_by, p.recorded_at FROM payroll_payments p "
            + "JOIN payroll_employers e ON e.id = p.payroll_employer_id";

    /** An employer's payments for a month, in the order they arrived. */
    public List<PayrollPaymentDto> payments(long employerId, LocalDate period) {
        return jdbc.query(PAYMENT_SELECT + " WHERE p.payroll_employer_id = :employer AND p.period = :period "
                        + "ORDER BY p.received_on, p.id",
                new MapSqlParameterSource("employer", employerId).addValue("period", Date.valueOf(period)),
                (rs, row) -> payment(rs, false));
    }

    public PayrollPage<PayrollPaymentDto> payments(Long employerId, LocalDate period, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource args = new MapSqlParameterSource();
        if (employerId != null) {
            where.append(" AND p.payroll_employer_id = :employer");
            args.addValue("employer", employerId);
        }
        if (period != null) {
            where.append(" AND p.period = :period");
            args.addValue("period", Date.valueOf(period));
        }
        Long total = jdbc.queryForObject("SELECT count(*) FROM payroll_payments p" + where, args, Long.class);
        args.addValue("limit", size).addValue("offset", (long) page * size);
        List<PayrollPaymentDto> content = jdbc.query(PAYMENT_SELECT + where
                + " ORDER BY p.period DESC, p.received_on DESC, p.id DESC LIMIT :limit OFFSET :offset", args,
                (rs, row) -> payment(rs, false));
        return PayrollPage.of(content, page, size, total == null ? 0 : total);
    }

    public static PayrollPaymentDto payment(ResultSet rs, boolean alreadyRecorded) throws SQLException {
        return new PayrollPaymentDto(rs.getLong("id"), new PayrollEmployerRef(rs.getLong("payroll_employer_id"),
                rs.getString("code"), rs.getString("name")), YearMonth.from(rs.getDate("period").toLocalDate())
                .toString(), PayrollKeys.money(rs.getBigDecimal("amount")), rs.getDate("received_on").toLocalDate()
                .toString(), rs.getString("reference"), rs.getString("recorded_by"),
                instant(rs, "recorded_at").toString(), alreadyRecorded);
    }

    // -------------------------------------------------------------------------------------------------- queries

    /** A query with its lines -- the discrepancies that name it, with who and what. */
    public Optional<PayrollQueryDto> query(long id) {
        List<PayrollQueryDto> found = jdbc.query("SELECT q.id, q.file_id, q.payroll_employer_id, e.code, e.name, "
                        + "q.period, q.raised_by, q.raised_at, q.add_count, q.delete_count, q.correct_count "
                        + "FROM payroll_queries q JOIN payroll_employers e ON e.id = q.payroll_employer_id "
                        + "WHERE q.id = :id", new MapSqlParameterSource("id", id),
                (rs, row) -> new PayrollQueryDto(rs.getLong("id"), rs.getLong("file_id"), new PayrollEmployerRef(
                        rs.getLong("payroll_employer_id"), rs.getString("code"), rs.getString("name")),
                        YearMonth.from(rs.getDate("period").toLocalDate()).toString(), rs.getString("raised_by"),
                        instant(rs, "raised_at").toString(), new PayrollQueryDto.Counts(rs.getInt("add_count"),
                        rs.getInt("delete_count"), rs.getInt("correct_count")), List.of(), List.of()));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        PayrollQueryDto query = found.get(0);
        List<PayrollQueryDto.Line> lines = jdbc.query(DISCREPANCY_SELECT + DISCREPANCY_FROM
                        + " WHERE d.query_id = :id ORDER BY " + TYPE_ORDER + ", d.line_number NULLS LAST, d.id",
                new MapSqlParameterSource("id", id), (rs, row) -> queryLine(partial(rs)));
        return Optional.of(new PayrollQueryDto(query.id(), query.fileId(), query.employer(), query.period(),
                query.raisedBy(), query.raisedAt(), query.counts(), lines, queryFiles(id)));
    }

    /** The protected files produced from a query list, newest first -- what they are, never a password. */
    public List<PayrollQueryFileDto> queryFiles(long queryId) {
        return jdbc.query(QUERY_FILE_SELECT + " WHERE f.query_id = :id ORDER BY f.produced_at DESC, f.id DESC",
                new MapSqlParameterSource("id", queryId), (rs, row) -> queryFile(rs));
    }

    /** One produced file, by id. */
    public Optional<PayrollQueryFileDto> queryFile(long id) {
        return jdbc.query(QUERY_FILE_SELECT + " WHERE f.id = :id", new MapSqlParameterSource("id", id),
                (rs, row) -> queryFile(rs)).stream().findFirst();
    }

    private static final String QUERY_FILE_SELECT = "SELECT f.id, f.query_id, f.format, f.protection, f.file_name, "
            + "f.size_bytes, f.sha256, f.produced_at, f.produced_by, f.downloads, f.last_downloaded_at, f.purged_at "
            + "FROM payroll_query_files f";

    private static PayrollQueryFileDto queryFile(ResultSet rs) throws SQLException {
        java.time.Instant downloaded = instant(rs, "last_downloaded_at");
        return new PayrollQueryFileDto(rs.getLong("id"), rs.getLong("query_id"), rs.getString("format"),
                rs.getString("protection"), rs.getString("file_name"), rs.getInt("size_bytes"), rs.getString("sha256"),
                instant(rs, "produced_at").toString(), rs.getString("produced_by"), rs.getInt("downloads"),
                downloaded == null ? null : downloaded.toString(), rs.getTimestamp("purged_at") != null);
    }

    private static PayrollQueryDto.Line queryLine(PartialDiscrepancy d) {
        PayrollQueryAction action = d.type().queryAction();
        String name = d.row() != null ? PayrollMatcher.displayName(d.row().initials(), d.row().surname())
                : d.member() == null ? null : d.member().name();
        String staff = d.row() != null ? d.row().staffNumber() : null;
        String id = d.row() != null ? d.row().idNumber() : null;
        return new PayrollQueryDto.Line(action.name(), d.id(), d.type().name(), staff, id, name,
                d.member() == null ? null : d.member().membershipNumber(), PayrollKeys.money(d.amountOnFile()),
                PayrollKeys.money(d.expected()), queryReason(d.type(), action));
    }

    /** The sentence beside a line of the employer's query list. */
    static String queryReason(PayrollDiscrepancyType type, PayrollQueryAction action) {
        return switch (type) {
            case NOT_A_MEMBER -> "Not a member of SASBO: please stop this deduction.";
            case MEMBERSHIP_ENDED -> "No longer a member of SASBO: please stop this deduction. No refund is due.";
            case MISSING_FROM_FILE -> "A member of SASBO not deducted this month: please deduct the subscription.";
            case AMOUNT_DIFFERS -> "The amount deducted is not the subscription: please correct it.";
            case DEDUCTED_TWICE -> "Deducted more than once this month: please deduct once. SASBO refunds the member.";
            default -> action.request();
        };
    }

    /** The query lists raised for a file, newest first. */
    public List<za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto.QueryRef> queriesOf(long fileId) {
        return jdbc.query("SELECT id, raised_at, raised_by, add_count, delete_count, correct_count FROM payroll_queries "
                        + "WHERE file_id = :file ORDER BY raised_at DESC, id DESC", new MapSqlParameterSource("file", fileId),
                (rs, row) -> new za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollFileSummaryDto.QueryRef(
                        rs.getLong("id"), instant(rs, "raised_at").toString(), rs.getString("raised_by"),
                        rs.getInt("add_count"), rs.getInt("delete_count"), rs.getInt("correct_count")));
    }

    // ------------------------------------------------------------------------------------------------ employers

    /** Each employer's newest live file, with its open discrepancies: one statement. */
    public Map<Long, za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerDto.LatestFile> latestFiles() {
        Map<Long, za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerDto.LatestFile> latest =
                new HashMap<>();
        jdbc.query("SELECT f.id, f.payroll_employer_id, f.period, f.status, f.open_count FROM payroll_files f "
                + "WHERE f.status = 'RECONCILED' AND f.period = (SELECT MAX(g.period) FROM payroll_files g "
                + "WHERE g.payroll_employer_id = f.payroll_employer_id AND g.status = 'RECONCILED')", rs -> {
                    latest.put(rs.getLong("payroll_employer_id"),
                            new za.co.titandynamix.sasbo_online_backend.dto.payroll.PayrollEmployerDto.LatestFile(
                                    rs.getLong("id"), YearMonth.from(rs.getDate("period").toLocalDate()).toString(),
                                    rs.getString("status"), rs.getInt("open_count")));
                });
        return latest;
    }

    /** Each employer's linked SASBO employers' names: one statement. */
    public Map<Long, List<String>> sasboEmployers() {
        Map<Long, List<String>> names = new HashMap<>();
        jdbc.query("SELECT pe.payroll_employer_id, e.name FROM payroll_employer_employers pe "
                + "JOIN employers e ON e.id = pe.employer_id ORDER BY e.ordinal", rs -> {
                    names.computeIfAbsent(rs.getLong("payroll_employer_id"), key -> new ArrayList<>())
                            .add(rs.getString("name"));
                });
        return names;
    }

    // --------------------------------------------------------------------------------------------------- helpers

    /** A member as lists show one: initials (from the record, or the first names) and surname -- never first names. */
    static String memberName(ResultSet rs, String initials, String firstNames, String surname) throws SQLException {
        String shown = rs.getString(initials);
        if (shown == null || shown.isBlank()) {
            shown = PayrollNames.initialsOf(rs.getString(firstNames));
        }
        return PayrollMatcher.displayName(shown, rs.getString(surname));
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp at = rs.getTimestamp(column);
        return at == null ? null : at.toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        Date day = rs.getDate(column);
        return day == null ? null : day.toLocalDate();
    }

    private static String month(ResultSet rs, String column) throws SQLException {
        LocalDate day = date(rs, column);
        return day == null ? null : YearMonth.from(day).toString();
    }
}

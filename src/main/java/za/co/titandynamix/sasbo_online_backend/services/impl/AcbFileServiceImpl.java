package za.co.titandynamix.sasbo_online_backend.services.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.acb.LineTotals;
import za.co.titandynamix.sasbo_online_backend.acb.file.BureauFileLine;
import za.co.titandynamix.sasbo_online_backend.acb.file.BureauFileWriter;
import za.co.titandynamix.sasbo_online_backend.config.AcbFileProperties;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunLineRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbFileService;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Renders a run's bureau file, keeps it, and serves it again unchanged.
 *
 * <h2>One file per run, and it never silently changes</h2>
 * The first render of an approved run stores the bytes, their SHA-256 and each line's sequence
 * number, in one transaction with its audit row. A later render of the same run must reproduce the
 * same bytes -- the hash proves it -- and is refused with {@code FILE_CHANGED_SINCE} if it does not.
 * Once the run is submitted the stored file IS the record, and it is served as stored.
 *
 * <h2>The trailer is checked against the run before anything is kept</h2>
 * The writer derives count and value from the records it wrote; they must equal the run's own
 * control totals, counted from its lines. A file whose trailer disagrees with its run is not kept.
 *
 * <h2>Why the table is read with JDBC</h2>
 * The bytes are member data and the only binary column in ACB; mapping them onto an entity would put
 * three thousand account numbers into the persistence context of every query that touched the row.
 * The file table is read and written here and nowhere else.
 */
@Slf4j
@Service
public class AcbFileServiceImpl implements AcbFileService {

    /** The runs whose file may be rendered or served: approved, and every state after it. */
    private static final Set<AcbRunStatus> RENDERABLE =
            EnumSet.of(AcbRunStatus.APPROVED, AcbRunStatus.SUBMITTED, AcbRunStatus.RETURNED, AcbRunStatus.CLOSED);

    private static final String LINES_FOR_FILE = "SELECT l.id, l.branch_code, l.account_number, l.account_type, "
            + "l.amount, l.action_date, l.membership_number, l.surname, l.initials, o_run.action_month AS re_month "
            + "FROM acb_run_lines l "
            + "LEFT JOIN acb_run_lines o ON o.id = l.re_presents_line_id "
            + "LEFT JOIN acb_runs o_run ON o_run.id = o.run_id "
            + "WHERE l.run_id = ? ORDER BY l.action_date, l.id";

    private final AcbRunRepository runs;
    private final AcbRunLineRepository lines;
    private final AcbAuditTrail audit;
    private final AcbFileProperties properties;
    private final SeededMembersGuard seededMembers;
    private final HolidayCalendarSource calendars;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate tx;

    public AcbFileServiceImpl(AcbRunRepository runs, AcbRunLineRepository lines, AcbAuditTrail audit,
                              AcbFileProperties properties, SeededMembersGuard seededMembers,
                              HolidayCalendarSource calendars, JdbcTemplate jdbc,
                              Clock clock, PlatformTransactionManager transactions) {
        this.runs = runs;
        this.lines = lines;
        this.audit = audit;
        this.properties = properties;
        this.seededMembers = seededMembers;
        this.calendars = calendars;
        this.jdbc = jdbc;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactions);
    }

    @Override
    public RenderedFile render(long runId, String actor) {
        return tx.execute(status -> renderInThisTransaction(runId, actor));
    }

    private RenderedFile renderInThisTransaction(long runId, String actor) {
        // Serialises two renders of one run: the second waits, then finds the first one's file.
        List<String> locked = jdbc.queryForList("SELECT status FROM acb_runs WHERE id = ? FOR UPDATE",
                String.class, runId);
        if (locked.isEmpty()) {
            throw AcbRuns.notFound(runId);
        }
        AcbRun run = runs.findById(runId).orElseThrow(() -> AcbRuns.notFound(runId));
        if (!RENDERABLE.contains(run.getStatus())) {
            throw new AcbRefusal(AcbRefusalCode.FILE_NOT_APPROVED,
                    "A bureau file is written only for an approved run; this one is " + run.getStatus() + ".",
                    AcbRuns.facts(run));
        }

        Optional<StoredFile> stored = storedFile(runId);
        if (stored.isPresent() && run.getStatus() != AcbRunStatus.APPROVED) {
            // Sent: the stored file is the record of what went to the bank. Serve it as it is.
            return served(run, stored.get());
        }

        // Before a byte is written: where the production marker is set, no file is made from a
        // seeded member (SD3-A). A file already sent is served above as the record it is.
        seededMembers.refuseSeededMembers(run);
        // 🔒 A holiday added inside the month since the run was prepared would put a debit on a day the
        // bank declines the whole file for (SD3-F).
        AcbRuns.requireCalendarAgrees(run, calendars.load());
        List<BureauFileLine> fileLines = fileLines(runId);
        BureauFileWriter.Written written = BureauFileWriter.write(properties.settings(), header(run), fileLines);
        requireTrailerMatchesRun(run, written);
        String sha256 = sha256(written.bytes());

        if (stored.isPresent()) {
            if (!stored.get().sha256().equals(sha256)) {
                throw new AcbRefusal(AcbRefusalCode.FILE_CHANGED_SINCE,
                        "This run's lines have changed since its file was produced, so the file would "
                                + "not be the one that was approved. Nothing has been replaced.",
                        Map.of("runId", String.valueOf(runId), "storedSha256", stored.get().sha256()));
            }
            return served(run, stored.get());
        }

        String fileName = fileName(YearMonth.from(run.getActionMonth()));
        BigDecimal debitValue = BigDecimal.valueOf(written.debitCents(), 2);
        jdbc.update("INSERT INTO acb_run_files (run_id, file_name, sha256, content, records, debit_count, "
                        + "debit_value, hash_total, created_at, created_by, keep_until) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId, fileName, sha256, written.bytes(), written.records(), written.debitCount(), debitValue,
                written.hashTotal(), OffsetDateTime.ofInstant(AcbTime.instant(clock), ZoneOffset.UTC), actor,
                run.getPurgeDate());
        List<Object[]> sequences = written.sequenceByLine().entrySet().stream()
                .map(e -> new Object[] {e.getValue(), e.getKey()})
                .toList();
        jdbc.batchUpdate("UPDATE acb_run_lines SET file_sequence = ? WHERE id = ?", sequences);
        audit.record(runId, null, "FILE_RENDERED", run.getStatus(), run.getStatus(),
                fileName + ": " + written.records() + " records, " + written.debitCount() + " debits, R "
                        + debitValue + ", sha256 " + sha256.substring(0, 12), actor);
        log.info("ACB run {}: bureau file rendered, {} records", runId, written.records());
        return new RenderedFile(fileName, sha256, written.bytes(), written.records(), written.debitCount(), debitValue);
    }

    @Override
    public boolean hasRendered(long runId) {
        Integer files = jdbc.queryForObject("SELECT count(*) FROM acb_run_files WHERE run_id = ?", Integer.class, runId);
        return files != null && files > 0;
    }

    @Override
    public int purgeExpired() {
        LocalDate today = AcbTime.today(clock);
        int purged = jdbc.update("UPDATE acb_run_files SET content = NULL, purged_at = ? "
                        + "WHERE keep_until < ? AND content IS NOT NULL",
                OffsetDateTime.ofInstant(AcbTime.instant(clock), ZoneOffset.UTC), today);
        if (purged > 0) {
            log.info("ACB: {} bureau file(s) past their purge date purged; their hashes are kept", purged);
        }
        return purged;
    }

    // ------------------------------------------------------------------------------------------

    /** The name the old system gave the file: {@code sasbosub} and the month, {@code sasbosub.jul}. */
    static String fileName(YearMonth month) {
        return "sasbosub." + month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toLowerCase(Locale.ROOT);
    }

    private static BureauFileWriter.Header header(AcbRun run) {
        return new BureauFileWriter.Header(run.getCreationDate(), run.getPurgeDate(),
                run.getFirstDueDate(), run.getLastDueDate());
    }

    private void requireTrailerMatchesRun(AcbRun run, BureauFileWriter.Written written) {
        LineTotals totals = lines.totalsFor(run.getId());
        long runCents = totals.total().movePointRight(2).longValueExact();
        if (written.debitCount() != totals.lineCount() || written.debitCents() != runCents) {
            throw new AcbRefusal(AcbRefusalCode.FILE_CHANGED_SINCE,
                    "The file's control totals do not match the run's own, so it is not kept.",
                    Map.of("runId", String.valueOf(run.getId()),
                            "fileDebits", String.valueOf(written.debitCount()),
                            "runLines", String.valueOf(totals.lineCount())));
        }
    }

    private List<BureauFileLine> fileLines(long runId) {
        return jdbc.query(LINES_FOR_FILE, (rs, row) -> {
            Date reMonth = rs.getDate("re_month");
            Object accountType = rs.getObject("account_type");
            return new BureauFileLine(
                    rs.getLong("id"),
                    rs.getString("branch_code"),
                    rs.getString("account_number"),
                    accountType == null ? null : ((Number) accountType).intValue(),
                    rs.getBigDecimal("amount"),
                    rs.getDate("action_date").toLocalDate(),
                    rs.getString("membership_number"),
                    rs.getString("surname"),
                    rs.getString("initials"),
                    reMonth == null ? null : YearMonth.from(reMonth.toLocalDate()));
        }, runId);
    }

    /** A stored file's facts; its bytes are read only when it is served. */
    private record StoredFile(String fileName, String sha256, boolean purged, int records, int debitCount,
                              BigDecimal debitValue) {
    }

    private Optional<StoredFile> storedFile(long runId) {
        return jdbc.query("SELECT file_name, sha256, content IS NULL AS purged, records, debit_count, debit_value "
                        + "FROM acb_run_files WHERE run_id = ?",
                (rs, row) -> new StoredFile(rs.getString("file_name"), rs.getString("sha256"),
                        rs.getBoolean("purged"), rs.getInt("records"), rs.getInt("debit_count"),
                        rs.getBigDecimal("debit_value")),
                runId).stream().findFirst();
    }

    private RenderedFile served(AcbRun run, StoredFile stored) {
        if (stored.purged()) {
            throw new AcbRefusal(AcbRefusalCode.FILE_PURGED,
                    "This run's file passed its purge date and its bytes were removed. Its hash and totals "
                            + "remain on the run.",
                    Map.of("runId", String.valueOf(run.getId()), "sha256", stored.sha256()));
        }
        byte[] bytes = jdbc.queryForObject("SELECT content FROM acb_run_files WHERE run_id = ?", byte[].class,
                run.getId());
        if (!sha256(Objects.requireNonNull(bytes)).equals(stored.sha256())) {
            throw new IllegalStateException("The stored file for run " + run.getId() + " no longer matches its hash");
        }
        return new RenderedFile(stored.fileName(), stored.sha256(), bytes, stored.records(), stored.debitCount(),
                stored.debitValue());
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required of every Java runtime", impossible);
        }
    }
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

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
 * The data migration's batches (V80), one statement at a time. Every move of a batch is guarded by its status -- and a
 * job's by its state -- in the statement itself, so of two requests that race, one changes the row and the other finds
 * it changed, and a job swept as dead cannot then be finished by the job presumed dead. Both databases accept every
 * statement here.
 */
@Repository
public class MigrationBatchRepository {

    public static final String RECEIVING = "RECEIVING";
    public static final String DRAFT = "DRAFT";
    public static final String VALIDATED = "VALIDATED";
    public static final String COMMITTING = "COMMITTING";
    public static final String COMMITTED = "COMMITTED";
    public static final String ROLLING_BACK = "ROLLING_BACK";
    public static final String ROLLED_BACK = "ROLLED_BACK";

    public static final String VALIDATE = "VALIDATE";
    public static final String COMMIT = "COMMIT";
    public static final String ROLLBACK = "ROLLBACK";

    public static final String RUNNING = "RUNNING";

    private static final String COLUMNS = "id, source_file_name, source_sha256, source_bytes, source_format, mapping_key,"
            + " mapping_snapshot, as_at, status, rows_read, rows_valid, rows_warned, rows_invalid, rows_committed,"
            + " commit_cursor, registered_by, registered_at, validated_by, validated_at, committed_by, committed_at,"
            + " rolled_back_by, rolled_back_at, rollback_reason, job_kind, job_state, job_done, job_total, job_started_at,"
            + " job_heartbeat_at, job_finished_at, job_failure";

    /** A batch as stored. Its {@code toString} carries its number and status, never its file's name. */
    public record Batch(long id, String sourceFileName, String sourceSha256, long sourceBytes, String sourceFormat,
                        String mappingKey, String mappingSnapshot, LocalDate asAt, String status, int rowsRead,
                        int rowsValid, int rowsWarned, int rowsInvalid, int rowsCommitted, long commitCursor,
                        String registeredBy, Instant registeredAt, String validatedBy, Instant validatedAt,
                        String committedBy, Instant committedAt, String rolledBackBy, Instant rolledBackAt,
                        String rollbackReason, String jobKind, String jobState, int jobDone, int jobTotal,
                        Instant jobStartedAt, Instant jobHeartbeatAt, Instant jobFinishedAt, String jobFailure) {

        @Override
        public String toString() {
            return "Batch[" + id + ", " + status + "]";
        }
    }

    /** A page of batches, newest first, and how many there are. */
    public record Slice(List<Batch> rows, long total) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public MigrationBatchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------------------------------------------ receiving

    /** A file being read: no hash yet, no rows yet. The partial unique index refuses a second while one is read. */
    public long insertReceiving(String fileName, String format, String mappingKey, String snapshot, LocalDate asAt,
                                String actor, Instant now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO migration_batches (source_file_name, source_bytes, source_format, mapping_key, mapping_snapshot,
                  as_at, status, rows_read, rows_valid, rows_warned, rows_invalid, rows_committed, commit_cursor,
                  registered_by, registered_at, job_done, job_total, job_heartbeat_at)
                VALUES (:name, 0, :format, :mappingKey, :snapshot, :asAt, 'RECEIVING', 0, 0, 0, 0, 0, 0,
                  :actor, :now, 0, 0, :now)
                """, new MapSqlParameterSource("name", fileName).addValue("format", format)
                .addValue("mappingKey", mappingKey).addValue("snapshot", snapshot).addValue("asAt", Date.valueOf(asAt))
                .addValue("actor", actor).addValue("now", utc(now)), key, new String[] {"id"});
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("the batch was written and no id came back");
        }
        return id.longValue();
    }

    /** The read is alive: how far it has got. */
    public void receiving(long id, int rows, Instant now) {
        jdbc.update("UPDATE migration_batches SET rows_read = :rows, job_heartbeat_at = :now"
                        + " WHERE id = :id AND status = 'RECEIVING'",
                new MapSqlParameterSource("id", id).addValue("rows", rows).addValue("now", utc(now)));
    }

    /** The file is read: its hash, its size and its rows, and the batch a DRAFT. */
    public boolean received(long id, String sha256, long bytes, int rows) {
        return jdbc.update("""
                UPDATE migration_batches SET status = 'DRAFT', source_sha256 = :sha256, source_bytes = :bytes,
                  rows_read = :rows, job_heartbeat_at = NULL
                WHERE id = :id AND status = 'RECEIVING'
                """, new MapSqlParameterSource("id", id).addValue("sha256", sha256).addValue("bytes", bytes)
                .addValue("rows", rows)) == 1;
    }

    /** A batch and its staged rows gone -- a read that failed keeps nothing. Rows first: H2 has no cascade. */
    public void delete(long id) {
        MapSqlParameterSource args = new MapSqlParameterSource("id", id);
        jdbc.update("DELETE FROM migration_rows WHERE batch_id = :id", args);
        jdbc.update("DELETE FROM migration_batches WHERE id = :id", args);
    }

    public boolean anyReceiving() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM migration_batches WHERE status = 'RECEIVING'", new MapSqlParameterSource(),
                Boolean.class));
    }

    /** Batches still being read whose read went quiet before {@code staleBefore}: an instance died reading them. */
    public List<Long> staleReceiving(Instant staleBefore) {
        return jdbc.queryForList("SELECT id FROM migration_batches WHERE status = 'RECEIVING'"
                        + " AND job_heartbeat_at < :staleBefore", new MapSqlParameterSource("staleBefore", utc(staleBefore)),
                Long.class);
    }

    // ------------------------------------------------------------------------------------------------ reading

    public Optional<Batch> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_batches WHERE id = :id",
                new MapSqlParameterSource("id", id), MigrationBatchRepository::batch).stream().findFirst();
    }

    /** The batch this file was registered as, unless that batch was undone. */
    public Optional<Batch> findLiveByHash(String sha256) {
        return jdbc.query("SELECT " + COLUMNS + " FROM migration_batches WHERE source_sha256 = :sha256"
                        + " AND status <> 'ROLLED_BACK' ORDER BY id", new MapSqlParameterSource("sha256", sha256),
                MigrationBatchRepository::batch).stream().findFirst();
    }

    /** Whether a file of this name was registered for this date -- the demonstration's "when absent", by its key. */
    public boolean existsNamed(String fileName, LocalDate asAt) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*) > 0 FROM migration_batches"
                        + " WHERE source_file_name = :name AND as_at = :asAt",
                new MapSqlParameterSource("name", fileName).addValue("asAt", Date.valueOf(asAt)), Boolean.class));
    }

    /** One page, newest first; the total from the same statement. */
    public Slice page(int page, int size) {
        List<long[]> totals = new java.util.ArrayList<>();
        List<Batch> rows = jdbc.query("SELECT " + COLUMNS + ", COUNT(*) OVER () AS total_rows FROM migration_batches"
                        + " ORDER BY id DESC LIMIT :size OFFSET :offset",
                new MapSqlParameterSource("size", size).addValue("offset", (long) page * size), (rs, n) -> {
                    totals.add(new long[] {rs.getLong("total_rows")});
                    return batch(rs, n);
                });
        return new Slice(rows, totals.isEmpty() ? countAll() : totals.get(0)[0]);
    }

    private long countAll() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM migration_batches", new MapSqlParameterSource(), Long.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------------------------------------ jobs

    public boolean anyJobRunning() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM migration_batches WHERE job_state = 'RUNNING'", new MapSqlParameterSource(),
                Boolean.class));
    }

    /**
     * Starts a job on a batch in one of {@code statuses} with no job running. The partial unique index refuses a second
     * running job anywhere -- one at a time, whatever the batch.
     */
    public boolean startJob(long id, String kind, Collection<String> statuses, int total, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET job_kind = :kind, job_state = 'RUNNING', job_done = 0, job_total = :total,
                  job_started_at = :now, job_heartbeat_at = :now, job_finished_at = NULL, job_failure = NULL
                WHERE id = :id AND status IN (:statuses) AND (job_state IS NULL OR job_state <> 'RUNNING')
                """, new MapSqlParameterSource("id", id).addValue("kind", kind).addValue("total", total)
                .addValue("statuses", statuses).addValue("now", utc(now))) == 1;
    }

    /** The job is alive, and how far it has got. False where it was swept meanwhile. */
    public boolean progress(long id, int done, Instant now) {
        return jdbc.update("UPDATE migration_batches SET job_done = :done, job_heartbeat_at = :now"
                        + " WHERE id = :id AND job_state = 'RUNNING'",
                new MapSqlParameterSource("id", id).addValue("done", done).addValue("now", utc(now))) == 1;
    }

    /** The job failed, saying why once -- a sentence of this code's own, never an exception's message. */
    public boolean failJob(long id, String failure, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET job_state = 'FAILED', job_failure = :failure, job_finished_at = :now
                WHERE id = :id AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("failure", failure).addValue("now", utc(now))) == 1;
    }

    /** Every job whose heartbeat went quiet before {@code staleBefore}: FAILED, interrupted. The ids it swept. */
    public List<Long> sweepJobs(Instant staleBefore, String failure, Instant now) {
        MapSqlParameterSource args = new MapSqlParameterSource("staleBefore", utc(staleBefore))
                .addValue("failure", failure).addValue("now", utc(now));
        List<Long> stale = jdbc.queryForList("SELECT id FROM migration_batches WHERE job_state = 'RUNNING'"
                + " AND job_heartbeat_at < :staleBefore", args, Long.class);
        if (!stale.isEmpty()) {
            jdbc.update("""
                    UPDATE migration_batches SET job_state = 'FAILED', job_failure = :failure, job_finished_at = :now
                    WHERE job_state = 'RUNNING' AND job_heartbeat_at < :staleBefore
                    """, args);
        }
        return stale;
    }

    // ------------------------------------------------------------------------------------------------ the check

    /** The mapping a batch is checked by, chosen again before a check: its key and its snapshot. */
    public boolean remap(long id, String mappingKey, String snapshot) {
        return jdbc.update("""
                UPDATE migration_batches SET mapping_key = :key, mapping_snapshot = :snapshot
                WHERE id = :id AND status IN ('DRAFT', 'VALIDATED') AND (job_state IS NULL OR job_state <> 'RUNNING')
                """, new MapSqlParameterSource("id", id).addValue("key", mappingKey).addValue("snapshot", snapshot)) == 1;
    }

    /** The check finished: VALIDATED, its counts, who and when, and the job DONE -- in one statement. */
    public boolean validated(long id, int valid, int warned, int invalid, String actor, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET status = 'VALIDATED', rows_valid = :valid, rows_warned = :warned,
                  rows_invalid = :invalid, validated_by = :actor, validated_at = :now, job_state = 'DONE',
                  job_done = rows_read, job_finished_at = :now
                WHERE id = :id AND job_kind = 'VALIDATE' AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("valid", valid).addValue("warned", warned)
                .addValue("invalid", invalid).addValue("actor", actor).addValue("now", utc(now))) == 1;
    }

    /** A check that did not finish leaves its rows half re-checked: the batch is a DRAFT until it is checked again. */
    public void toDraft(long id) {
        jdbc.update("UPDATE migration_batches SET status = 'DRAFT', rows_valid = 0, rows_warned = 0, rows_invalid = 0"
                + " WHERE id = :id AND status IN ('DRAFT', 'VALIDATED')", new MapSqlParameterSource("id", id));
    }

    // ------------------------------------------------------------------------------------------------ the move

    /** The counts after a move found rows clashing with members captured since the check. */
    public void recount(long id, int valid, int warned, int invalid) {
        jdbc.update("UPDATE migration_batches SET rows_valid = :valid, rows_warned = :warned, rows_invalid = :invalid"
                        + " WHERE id = :id",
                new MapSqlParameterSource("id", id).addValue("valid", valid).addValue("warned", warned)
                        .addValue("invalid", invalid));
    }

    /** The move begins -- or resumes, from where it was. */
    public boolean committing(long id) {
        return jdbc.update("UPDATE migration_batches SET status = 'COMMITTING'"
                + " WHERE id = :id AND status IN ('VALIDATED', 'COMMITTING')", new MapSqlParameterSource("id", id)) == 1;
    }

    /** A chunk moved: the cursor past its last row, its members counted, the job's progress. */
    public boolean advanced(long id, long cursor, int moved, int done, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET commit_cursor = :cursor, rows_committed = rows_committed + :moved,
                  job_done = :done, job_heartbeat_at = :now
                WHERE id = :id AND status = 'COMMITTING' AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("cursor", cursor).addValue("moved", moved)
                .addValue("done", done).addValue("now", utc(now))) == 1;
    }

    /** The move finished: COMMITTED, who and when, and the job DONE -- in one statement. */
    public boolean committed(long id, String actor, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET status = 'COMMITTED', committed_by = :actor, committed_at = :now,
                  job_state = 'DONE', job_finished_at = :now
                WHERE id = :id AND status = 'COMMITTING' AND job_kind = 'COMMIT' AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("actor", actor).addValue("now", utc(now))) == 1;
    }

    // ------------------------------------------------------------------------------------------------ the undo

    /** The undo begins -- or resumes -- with the reason given for it. */
    public boolean rollingBack(long id, String reason) {
        return jdbc.update("""
                UPDATE migration_batches SET status = 'ROLLING_BACK', rollback_reason = :reason
                WHERE id = :id AND status IN ('COMMITTED', 'COMMITTING', 'ROLLING_BACK')
                """, new MapSqlParameterSource("id", id).addValue("reason", reason)) == 1;
    }

    /** A chunk undone: its members no longer counted, the job's progress. */
    public boolean undone(long id, int removed, int done, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET rows_committed = rows_committed - :removed, job_done = :done,
                  job_heartbeat_at = :now
                WHERE id = :id AND status = 'ROLLING_BACK' AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("removed", removed).addValue("done", done)
                .addValue("now", utc(now))) == 1;
    }

    /** The undo finished: ROLLED_BACK, who and when, and the job DONE -- in one statement. */
    public boolean rolledBack(long id, String actor, Instant now) {
        return jdbc.update("""
                UPDATE migration_batches SET status = 'ROLLED_BACK', rolled_back_by = :actor, rolled_back_at = :now,
                  commit_cursor = 0, job_state = 'DONE', job_finished_at = :now
                WHERE id = :id AND status = 'ROLLING_BACK' AND job_kind = 'ROLLBACK' AND job_state = 'RUNNING'
                """, new MapSqlParameterSource("id", id).addValue("actor", actor).addValue("now", utc(now))) == 1;
    }

    // ------------------------------------------------------------------------------------------------ mapping rows

    private static Batch batch(ResultSet rs, int n) throws SQLException {
        return new Batch(rs.getLong("id"), rs.getString("source_file_name"), rs.getString("source_sha256"),
                rs.getLong("source_bytes"), rs.getString("source_format"), rs.getString("mapping_key"),
                rs.getString("mapping_snapshot"), rs.getDate("as_at").toLocalDate(), rs.getString("status"),
                rs.getInt("rows_read"), rs.getInt("rows_valid"), rs.getInt("rows_warned"), rs.getInt("rows_invalid"),
                rs.getInt("rows_committed"), rs.getLong("commit_cursor"), rs.getString("registered_by"),
                instant(rs, "registered_at"), rs.getString("validated_by"), instant(rs, "validated_at"),
                rs.getString("committed_by"), instant(rs, "committed_at"), rs.getString("rolled_back_by"),
                instant(rs, "rolled_back_at"), rs.getString("rollback_reason"), rs.getString("job_kind"),
                rs.getString("job_state"), rs.getInt("job_done"), rs.getInt("job_total"), instant(rs, "job_started_at"),
                instant(rs, "job_heartbeat_at"), instant(rs, "job_finished_at"), rs.getString("job_failure"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime at = rs.getObject(column, OffsetDateTime.class);
        return at == null ? null : at.toInstant();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

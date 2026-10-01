package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The statistics packs (month end 04.10): a job's record, one statement at a time. Every move of a pack is guarded by
 * its status in the statement itself -- only a PREPARING pack beats, finishes, fails or is swept -- so a pack swept as
 * dead cannot then be finished by the job that was presumed dead.
 */
@Repository
public class MonthEndPackRepository {

    public static final String PREPARING = "PREPARING";
    public static final String READY = "READY";
    public static final String FAILED = "FAILED";

    /** The two workbooks a pack holds, by the column that holds each -- chosen from these, never from a request. */
    public enum Workbook {
        STATISTICS("statistics_file"),
        MONTHLY("monthly_file");

        final String column;

        Workbook(String column) {
            this.column = column;
        }
    }

    /** A pack as the screen reads it: never its bytes. */
    public record Pack(long id, LocalDate monthStart, String status, String askedBy, Instant startedAt,
                       Instant heartbeatAt, Instant finishedAt, String failure) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public MonthEndPackRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /** A pack in preparation, of no take -- the open month's, read live. */
    public long insertPreparing(LocalDate monthStart, String askedBy, Instant now) {
        return insertPreparing(monthStart, null, askedBy, now);
    }

    /**
     * A pack in preparation, of the take it is built from -- the month's newest, where it is taken (04.11): a pack of a
     * take since replaced is never served as the month's. The partial unique index refuses a second while one is in
     * hand (MR-PERF-11).
     */
    public long insertPreparing(LocalDate monthStart, Long takeId, String askedBy, Instant now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO month_end_packs (month_start, month_id, status, asked_by, started_at, heartbeat_at)
                VALUES (:monthStart, :takeId, 'PREPARING', :askedBy, :now, :now)
                """, new MapSqlParameterSource("monthStart", monthStart).addValue("takeId", takeId, java.sql.Types.BIGINT)
                .addValue("askedBy", askedBy).addValue("now", utc(now)), key, new String[] {"id"});
        Number id = key.getKey();
        if (id == null) {
            throw new IllegalStateException("the pack was written and no id came back");
        }
        return id.longValue();
    }

    public Optional<Pack> find(long id) {
        return one("WHERE id = :id", new MapSqlParameterSource("id", id));
    }

    /** The pack in hand, if one is being prepared. */
    public Optional<Pack> preparing() {
        return one("WHERE status = 'PREPARING'", new MapSqlParameterSource());
    }

    /** The newest READY pack of a month's take -- its newest, as the caller reads it (04.11). */
    public Optional<Pack> latestReady(LocalDate monthStart, long takeId) {
        return one("WHERE month_start = :monthStart AND month_id = :takeId AND status = 'READY' ORDER BY id DESC",
                new MapSqlParameterSource("monthStart", monthStart).addValue("takeId", takeId));
    }

    /** The job is alive. */
    public boolean heartbeat(long id, Instant now) {
        return jdbc.update("UPDATE month_end_packs SET heartbeat_at = :now WHERE id = :id AND status = 'PREPARING'",
                new MapSqlParameterSource("id", id).addValue("now", utc(now))) == 1;
    }

    /** The job finished: both workbooks, READY. False where the pack was swept meanwhile. */
    public boolean ready(long id, byte[] statistics, byte[] monthly, Instant now) {
        return jdbc.update("""
                UPDATE month_end_packs SET status = 'READY', statistics_file = :statistics, monthly_file = :monthly,
                  heartbeat_at = :now, finished_at = :now
                WHERE id = :id AND status = 'PREPARING'
                """, new MapSqlParameterSource("id", id).addValue("statistics", statistics)
                .addValue("monthly", monthly).addValue("now", utc(now))) == 1;
    }

    /** The job failed, saying why once (ME-21). */
    public boolean fail(long id, String failure, Instant now) {
        return jdbc.update("""
                UPDATE month_end_packs SET status = 'FAILED', failure = :failure, finished_at = :now
                WHERE id = :id AND status = 'PREPARING'
                """, new MapSqlParameterSource("id", id).addValue("failure", failure)
                .addValue("now", utc(now))) == 1;
    }

    /** Every pack whose job went quiet before {@code staleBefore}: FAILED, interrupted. */
    public int sweep(Instant staleBefore, String failure, Instant now) {
        return jdbc.update("""
                UPDATE month_end_packs SET status = 'FAILED', failure = :failure, finished_at = :now
                WHERE status = 'PREPARING' AND heartbeat_at < :staleBefore
                """, new MapSqlParameterSource("staleBefore", utc(staleBefore)).addValue("failure", failure)
                .addValue("now", utc(now)));
    }

    /** One workbook's bytes, where the pack is READY. */
    public Optional<byte[]> file(long id, Workbook workbook) {
        List<byte[]> found = jdbc.query("SELECT " + workbook.column + " AS bytes FROM month_end_packs "
                        + "WHERE id = :id AND status = 'READY'", new MapSqlParameterSource("id", id),
                (rs, n) -> rs.getBytes("bytes"));
        return found.stream().findFirst();
    }

    private Optional<Pack> one(String where, MapSqlParameterSource args) {
        return jdbc.query("SELECT id, month_start, status, asked_by, started_at, heartbeat_at, finished_at, failure "
                + "FROM month_end_packs " + where, args, MonthEndPackRepository::pack).stream().findFirst();
    }

    private static Pack pack(ResultSet rs, int n) throws SQLException {
        OffsetDateTime finished = rs.getObject("finished_at", OffsetDateTime.class);
        return new Pack(rs.getLong("id"), rs.getDate("month_start").toLocalDate(), rs.getString("status"),
                rs.getString("asked_by"), rs.getObject("started_at", OffsetDateTime.class).toInstant(),
                rs.getObject("heartbeat_at", OffsetDateTime.class).toInstant(),
                finished == null ? null : finished.toInstant(), rs.getString("failure"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}

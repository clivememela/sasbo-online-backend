package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * The old system's month-end figures, entered for a month (V81): read whole, and replaced whole -- a month's figures
 * are typed from one pack. The caller holds the transaction a replacement runs in.
 */
@Repository
public class MigrationTotalsRepository {

    /** One figure as stored. */
    public record Total(String dimension, String key, String measure, long value, String source, String enteredBy,
                        Instant enteredAt) {
    }

    private final NamedParameterJdbcTemplate named;
    private final JdbcTemplate jdbc;

    public MigrationTotalsRepository(NamedParameterJdbcTemplate named) {
        this.named = named;
        this.jdbc = named.getJdbcTemplate();
    }

    /** A month's figures, in the order a reconciliation reads them. */
    public List<Total> month(LocalDate monthStart) {
        return named.query("""
                SELECT dimension, item_key, measure, legacy_value, source, entered_by, entered_at
                FROM migration_month_totals WHERE month_start = :month ORDER BY dimension, item_key, measure
                """, new MapSqlParameterSource("month", Date.valueOf(monthStart)), (rs, n) -> new Total(
                rs.getString("dimension"), rs.getString("item_key"), rs.getString("measure"), rs.getLong("legacy_value"),
                rs.getString("source"), rs.getString("entered_by"),
                rs.getObject("entered_at", OffsetDateTime.class).toInstant()));
    }

    /** A month's figures replaced by these -- inside the caller's transaction, so a reader sees the old or the new. */
    public void replace(LocalDate monthStart, List<Total> totals, String actor, Instant now) {
        named.update("DELETE FROM migration_month_totals WHERE month_start = :month",
                new MapSqlParameterSource("month", Date.valueOf(monthStart)));
        OffsetDateTime at = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        List<Object[]> args = new ArrayList<>(totals.size());
        for (Total total : totals) {
            args.add(new Object[] {Date.valueOf(monthStart), total.dimension(), total.key(), total.measure(),
                    total.value(), total.source(), actor, at});
        }
        if (!args.isEmpty()) {
            jdbc.batchUpdate("""
                    INSERT INTO migration_month_totals (month_start, dimension, item_key, measure, legacy_value, source,
                      entered_by, entered_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, args, new int[] {Types.DATE, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.BIGINT,
                    Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP_WITH_TIMEZONE});
        }
    }
}

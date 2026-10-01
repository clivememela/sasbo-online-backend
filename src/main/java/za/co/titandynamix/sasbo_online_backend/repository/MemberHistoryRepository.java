package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.member.MemberHistoryPageDto;

import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * One member's history, a page at a time (month end 03.6): the field changes of {@code member_changes} and the status
 * changes of {@code member_status_changes}, merged by time in ONE statement -- the page and the number of entries it
 * is a page of together -- newest first, and then by the entry's own number, so two pages never share an entry or
 * skip one. A status change carries SASBO's code by its NUMBER, never the table's id (RC7), with the code's name and --
 * where the member gave one under it -- the reason's (04.5). Both databases accept it.
 */
@Repository
public class MemberHistoryRepository {

    private static final String STATEMENT = """
            SELECT kind, entry_id, changed_at, changed_by, what, from_value, to_value, reason, code, code_name,
                   reason_under_code, save_id, note, COUNT(*) OVER () AS total
            FROM (
              SELECT 'FIELD' AS kind, c.id AS entry_id, c.changed_at, c.changed_by, c.field AS what,
                     c.old_value AS from_value, c.new_value AS to_value, CAST(NULL AS VARCHAR(40)) AS reason,
                     CAST(NULL AS INTEGER) AS code, CAST(NULL AS VARCHAR(255)) AS code_name,
                     CAST(NULL AS VARCHAR(255)) AS reason_under_code, c.save_id, CAST(NULL AS VARCHAR(500)) AS note
              FROM member_changes c
              WHERE c.member_id = :member
              UNION ALL
              SELECT 'STATUS', s.id, s.changed_at, s.changed_by, 'status', s.from_status, s.to_status, s.reason,
                     COALESCE(d.sasbo_code, dp.sasbo_code, sc.code),
                     CAST(COALESCE(dp.name, d.name, sc.name) AS VARCHAR(255)),
                     CAST(CASE WHEN d.parent_id IS NOT NULL THEN d.name END AS VARCHAR(255)),
                     CAST(NULL AS VARCHAR(36)), s.note
              FROM member_status_changes s
              LEFT JOIN deletion_codes d ON d.id = s.deletion_code_id
              LEFT JOIN deletion_codes dp ON dp.id = d.parent_id
              LEFT JOIN suspension_codes sc ON sc.id = s.suspension_code_id
              WHERE s.member_id = :member
            ) h
            ORDER BY changed_at DESC, kind DESC, entry_id DESC
            LIMIT :limit OFFSET :offset
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public MemberHistoryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A page of the entries, and how many there are in all -- nought where the page came back empty. */
    public record Slice(List<MemberHistoryPageDto.Entry> entries, long total) {
    }

    public Slice page(int memberId, int page, int size) {
        long[] total = {0};
        List<MemberHistoryPageDto.Entry> entries = jdbc.query(STATEMENT, new MapSqlParameterSource()
                        .addValue("member", memberId).addValue("limit", size).addValue("offset", (long) page * size),
                (rs, n) -> {
                    total[0] = rs.getLong("total");
                    OffsetDateTime at = rs.getObject("changed_at", OffsetDateTime.class);
                    Integer code = rs.getObject("code") == null ? null : rs.getInt("code");
                    return new MemberHistoryPageDto.Entry(rs.getString("kind"), rs.getLong("entry_id"),
                            at == null ? null : at.toInstant().toString(), rs.getString("changed_by"),
                            rs.getString("what"), rs.getString("from_value"), rs.getString("to_value"),
                            rs.getString("reason"), code, rs.getString("code_name"), rs.getString("reason_under_code"),
                            rs.getString("save_id"), rs.getString("note"));
                });
        return new Slice(entries, total[0]);
    }

    /**
     * The day, in South Africa, of the first change this database recorded (ME-7) -- read by the index on
     * {@code changed_at}; empty where none was ever recorded.
     */
    public Optional<LocalDate> historyFrom() {
        OffsetDateTime first = jdbc.getJdbcTemplate().queryForObject("SELECT MIN(changed_at) FROM member_changes",
                OffsetDateTime.class);
        return Optional.ofNullable(first).map(at -> at.atZoneSameInstant(MonthRange.ZONE).toLocalDate());
    }
}

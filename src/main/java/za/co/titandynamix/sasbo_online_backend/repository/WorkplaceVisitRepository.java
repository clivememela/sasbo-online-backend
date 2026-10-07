package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitPageDto.Visit;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.WorkplaceVisitRequest;

import javax.sql.DataSource;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * A workplace's visits (month end A.1, V62), by one statement each -- written here and nowhere else. The visits are
 * append-only: nothing here updates or deletes one, and PostgreSQL's trigger refuses it if anything did.
 */
@Repository
public class WorkplaceVisitRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public WorkplaceVisitRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    /** A workplace on record here, and its complement as it stands -- or none. */
    public Optional<Integer[]> complementOf(long workplaceId) {
        List<Integer[]> found = jdbc.query("SELECT staff_complement FROM workplaces WHERE id = :id",
                new MapSqlParameterSource("id", workplaceId),
                (rs, i) -> new Integer[] {rs.getObject(1) == null ? null : rs.getInt(1)});
        return found.stream().findFirst();
    }

    /**
     * The workplace's complement as it stands, its row LOCKED for the rest of the transaction -- what a visit about to
     * write another reads, so two visits to one branch at once are written one after the other, each keeping the
     * complement it found before it (October build review). Or none.
     */
    public Optional<Integer[]> lockComplementOf(long workplaceId) {
        List<Integer[]> found = jdbc.query("SELECT staff_complement FROM workplaces WHERE id = :id FOR UPDATE",
                new MapSqlParameterSource("id", workplaceId),
                (rs, i) -> new Integer[] {rs.getObject(1) == null ? null : rs.getInt(1)});
        return found.stream().findFirst();
    }

    /** Writes the complement a visit found onto the workplace. */
    public void setComplement(long workplaceId, int complement) {
        jdbc.update("UPDATE workplaces SET staff_complement = :complement WHERE id = :id",
                new MapSqlParameterSource("id", workplaceId).addValue("complement", complement));
    }

    /** A visit captured: its day known, never seeded. Answers its id. */
    public long insert(long workplaceId, WorkplaceVisitRequest visit, LocalDate visitedOn, LocalTime timeIn,
                       LocalTime timeOut, Integer complementBefore, Instant capturedAt) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO workplace_visits (workplace_id, visited_on, day_known, organiser, time_in, time_out,
                  complement_found, complement_before, membership_found, non_members, recruited,
                  non_members_interviewed, seeded, captured_at)
                VALUES (:workplace, :visitedOn, TRUE, :organiser, :timeIn, :timeOut, :complementFound,
                  :complementBefore, :membershipFound, :nonMembers, :recruited, :interviewed, FALSE, :capturedAt)
                """, new MapSqlParameterSource("workplace", workplaceId)
                        .addValue("visitedOn", Date.valueOf(visitedOn))
                        .addValue("organiser", visit.organiser())
                        .addValue("timeIn", timeIn == null ? null : Time.valueOf(timeIn))
                        .addValue("timeOut", timeOut == null ? null : Time.valueOf(timeOut))
                        .addValue("complementFound", visit.complementFound())
                        .addValue("complementBefore", complementBefore)
                        .addValue("membershipFound", visit.membershipFound())
                        .addValue("nonMembers", visit.nonMembers())
                        .addValue("recruited", visit.recruited())
                        .addValue("interviewed", visit.nonMembersInterviewed())
                        .addValue("capturedAt", Timestamp.from(capturedAt)),
                key, new String[] {"id"});
        return key.getKey().longValue();
    }

    /** How many visits a workplace has on record. */
    public long count(long workplaceId) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM workplace_visits WHERE workplace_id = :id",
                new MapSqlParameterSource("id", workplaceId), Long.class);
        return n == null ? 0 : n;
    }

    /** One of a workplace's newest visits: its day -- or the first of its month, where only the month is known. */
    public record Newest(long workplaceId, LocalDate visitedOn, boolean dayKnown) {
    }

    /**
     * The newest visits of each workplace asked for, {@code each} at most a workplace -- a location listing's "Last
     * Visited" line, ten months as the branch list prints it (October build). ONE statement for every workplace of a
     * batch print, numbered within each workplace on {@code workplace_visits_newest}'s order; workplace by workplace,
     * newest first.
     */
    public List<Newest> newest(java.util.Collection<Long> workplaceIds, int each) {
        if (workplaceIds.isEmpty()) {
            return List.of();
        }
        return jdbc.query("""
                SELECT workplace_id, visited_on, day_known FROM (
                  SELECT workplace_id, visited_on, day_known,
                         ROW_NUMBER() OVER (PARTITION BY workplace_id ORDER BY visited_on DESC, id DESC) AS n
                  FROM workplace_visits WHERE workplace_id IN (:ids)) numbered
                WHERE n <= :each ORDER BY workplace_id, n
                """, new MapSqlParameterSource("ids", workplaceIds).addValue("each", each),
                (rs, i) -> new Newest(rs.getLong("workplace_id"), rs.getDate("visited_on").toLocalDate(),
                        rs.getBoolean("day_known")));
    }

    /** A page of a workplace's visits, newest first and then by their own number. */
    public List<Visit> page(long workplaceId, int page, int size) {
        return jdbc.query("""
                SELECT id, visited_on, day_known, complement_found, complement_before, seeded FROM workplace_visits
                WHERE workplace_id = :id ORDER BY visited_on DESC, id DESC LIMIT :size OFFSET :offset
                """, new MapSqlParameterSource("id", workplaceId).addValue("size", size)
                        .addValue("offset", (long) page * size),
                (rs, i) -> new Visit(rs.getLong("id"), rs.getDate("visited_on").toLocalDate().toString(),
                        rs.getBoolean("day_known"), rs.getObject("complement_found", Integer.class),
                        rs.getObject("complement_before", Integer.class), rs.getBoolean("seeded")));
    }
}

package za.co.titandynamix.sasbo_online_backend.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import za.co.titandynamix.sasbo_online_backend.dto.workplace.VisitDraftDto;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Visit drafts (V82), by one statement each -- written here and nowhere else.
 *
 * <h2>Always the owner's</h2>
 * Every statement names the owner beside the draft's id: a draft is found, changed or submitted only by the person who
 * saved it, so another person's draft is not "refused" -- it is not there.
 *
 * <h2>The optimistic lock is in the statement</h2>
 * A save and a submission change the row only {@code WHERE} its version is the one the person saw and it is still a
 * draft, and move the version on in the same statement -- so two saves of one version cannot both land, whatever
 * checked first. The service reads the row {@code FOR UPDATE} before it, to say WHY a save was refused.
 */
@Repository
public class WorkplaceVisitDraftRepository {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMITTED = "SUBMITTED";

    /** A draft as its owner reads it: itself, its workplace's number and name, and its visit's complement before. */
    private static final String COLUMNS = "d.id, d.status, d.workplace_id, w.ra_number, w.name AS workplace_name,"
            + " d.visited_on, d.time_in, d.time_out, d.organiser, d.complement_found, v.complement_before,"
            + " d.membership_found, d.non_members, d.recruited, d.non_members_interviewed, d.created_by, d.created_at,"
            + " d.updated_at, d.submitted_by, d.submitted_at, d.visit_id, d.version";

    private static final String FROM = " FROM workplace_visit_drafts d"
            + " JOIN workplaces w ON w.id = d.workplace_id"
            + " LEFT JOIN workplace_visits v ON v.id = d.visit_id";

    private final NamedParameterJdbcTemplate jdbc;

    public WorkplaceVisitDraftRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** What a draft keeps of a visit, read and checked: the day may be missing until it is submitted. */
    public record Fields(LocalDate visitedOn, LocalTime timeIn, LocalTime timeOut, String organiser,
                         Integer complementFound, Integer membershipFound, Integer nonMembers, Integer recruited,
                         Integer nonMembersInterviewed) {
    }

    /** What the service needs to say why a save would be refused: the row as it stands, locked. */
    public record Head(long id, String status, long workplaceId, int version, Long visitId, Instant updatedAt,
                       Instant submittedAt) {
    }

    /** A page of drafts, and how many it is a page of. The total is 0 where the page came back empty. */
    public record Slice(List<VisitDraftDto> rows, long total) {
    }

    /** The owner's draft, as they read it back -- or none. */
    public Optional<VisitDraftDto> one(long id, String owner) {
        return jdbc.query("SELECT " + COLUMNS + FROM + " WHERE d.id = :id AND d.owner = :owner",
                new MapSqlParameterSource("id", id).addValue("owner", owner), (rs, n) -> dto(rs)).stream().findFirst();
    }

    /** The owner's draft as it stands, its row locked for the rest of the transaction -- or none. */
    public Optional<Head> lock(long id, String owner) {
        return jdbc.query("SELECT id, status, workplace_id, version, visit_id, updated_at, submitted_at "
                        + "FROM workplace_visit_drafts WHERE id = :id AND owner = :owner FOR UPDATE",
                new MapSqlParameterSource("id", id).addValue("owner", owner),
                (rs, n) -> new Head(rs.getLong("id"), rs.getString("status"), rs.getLong("workplace_id"),
                        rs.getInt("version"), rs.getObject("visit_id", Long.class),
                        rs.getTimestamp("updated_at").toInstant(),
                        rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant()))
                .stream().findFirst();
    }

    /** A new draft, at version nought. Answers its id. */
    public long insert(long workplaceId, Fields fields, String owner, String actor, Instant at) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO workplace_visit_drafts (workplace_id, status, visited_on, organiser, time_in, time_out,
                  complement_found, membership_found, non_members, recruited, non_members_interviewed, owner,
                  created_by, created_at, updated_at, version)
                VALUES (:workplace, 'DRAFT', :visitedOn, :organiser, :timeIn, :timeOut, :complementFound,
                  :membershipFound, :nonMembers, :recruited, :interviewed, :owner, :actor, :at, :at, 0)
                """, values(fields).addValue("workplace", workplaceId).addValue("owner", owner)
                        .addValue("actor", actor).addValue("at", Timestamp.from(at)),
                key, new String[] {"id"});
        return key.getKey().longValue();
    }

    /** Saves over the owner's draft, where it is still a draft at the version named. Answers whether it did. */
    public boolean update(long id, String owner, int version, long workplaceId, Fields fields, Instant at) {
        return jdbc.update("""
                UPDATE workplace_visit_drafts SET workplace_id = :workplace, visited_on = :visitedOn,
                  organiser = :organiser, time_in = :timeIn, time_out = :timeOut, complement_found = :complementFound,
                  membership_found = :membershipFound, non_members = :nonMembers, recruited = :recruited,
                  non_members_interviewed = :interviewed, updated_at = :at, version = version + 1
                WHERE id = :id AND owner = :owner AND status = 'DRAFT' AND version = :version
                """, values(fields).addValue("workplace", workplaceId).addValue("at", Timestamp.from(at))
                .addValue("id", id).addValue("owner", owner).addValue("version", version)) == 1;
    }

    /** Marks the owner's draft submitted as the visit named, where it is still a draft at the version named. */
    public boolean submitted(long id, String owner, int version, long visitId, String actor, Instant at) {
        return jdbc.update("""
                UPDATE workplace_visit_drafts SET status = 'SUBMITTED', visit_id = :visit, submitted_by = :actor,
                  submitted_at = :at, updated_at = :at, version = version + 1
                WHERE id = :id AND owner = :owner AND status = 'DRAFT' AND version = :version
                """, new MapSqlParameterSource("visit", visitId).addValue("actor", actor)
                .addValue("at", Timestamp.from(at)).addValue("id", id).addValue("owner", owner)
                .addValue("version", version)) == 1;
    }

    /** A page of the owner's drafts of one status, the most recently saved first, then by their own number. */
    public Slice mine(String owner, String status, int page, int size) {
        long[] total = {0};
        List<VisitDraftDto> rows = jdbc.query("SELECT count(*) OVER () AS total, " + COLUMNS + FROM
                        + " WHERE d.owner = :owner AND d.status = :status"
                        + " ORDER BY d.updated_at DESC, d.id DESC LIMIT :limit OFFSET :offset",
                new MapSqlParameterSource("owner", owner).addValue("status", status).addValue("limit", size)
                        .addValue("offset", (long) page * size),
                (rs, n) -> {
                    total[0] = rs.getLong("total");
                    return dto(rs);
                });
        return new Slice(rows, total[0]);
    }

    private static MapSqlParameterSource values(Fields fields) {
        return new MapSqlParameterSource("visitedOn", fields.visitedOn() == null ? null : Date.valueOf(fields.visitedOn()))
                .addValue("organiser", fields.organiser())
                .addValue("timeIn", fields.timeIn() == null ? null : Time.valueOf(fields.timeIn()))
                .addValue("timeOut", fields.timeOut() == null ? null : Time.valueOf(fields.timeOut()))
                .addValue("complementFound", fields.complementFound())
                .addValue("membershipFound", fields.membershipFound())
                .addValue("nonMembers", fields.nonMembers())
                .addValue("recruited", fields.recruited())
                .addValue("interviewed", fields.nonMembersInterviewed());
    }

    private static VisitDraftDto dto(ResultSet rs) throws SQLException {
        Date visitedOn = rs.getDate("visited_on");
        Time timeIn = rs.getTime("time_in");
        Time timeOut = rs.getTime("time_out");
        Timestamp submittedAt = rs.getTimestamp("submitted_at");
        return new VisitDraftDto(
                rs.getLong("id"),
                rs.getString("status"),
                rs.getLong("workplace_id"),
                rs.getInt("ra_number"),
                rs.getString("workplace_name"),
                visitedOn == null ? null : visitedOn.toLocalDate().toString(),
                timeIn == null ? null : timeIn.toLocalTime().toString(),
                timeOut == null ? null : timeOut.toLocalTime().toString(),
                rs.getString("organiser"),
                rs.getObject("complement_found", Integer.class),
                rs.getObject("complement_before", Integer.class),
                rs.getObject("membership_found", Integer.class),
                rs.getObject("non_members", Integer.class),
                rs.getObject("recruited", Integer.class),
                rs.getObject("non_members_interviewed", Integer.class),
                rs.getString("created_by"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("updated_at").toInstant().toString(),
                rs.getString("submitted_by"),
                submittedAt == null ? null : submittedAt.toInstant().toString(),
                rs.getObject("visit_id", Long.class),
                rs.getInt("version"));
    }
}

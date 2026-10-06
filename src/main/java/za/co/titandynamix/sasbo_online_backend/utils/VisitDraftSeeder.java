package za.co.titandynamix.sasbo_online_backend.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Invented visits, saved and submitted (October build, the visit forms) -- so a fresh demonstration shows the Location
 * Visit form's drafts and submitted visits with something in them (the owner, 6 Oct: "good seed data that reflect all
 * of these work").
 *
 * <h2>What is seeded</h2>
 * Six visits by an invented organiser to six workplaces of one area: three SUBMITTED in the month before the clock's --
 * each a visit on record in {@code workplace_visits}, as a submission makes one -- and three still DRAFT this month, one
 * with no day chosen yet. They belong to the demonstration organiser of the sign-in seed ({@link #SEED_OWNER}), whose
 * area they are in, so signing in as that organiser shows them as "my drafts" and can submit them.
 *
 * <h2>Only where the switch says {@code all}, never under the production marker</h2>
 * A visit carries the staff complement found, which is made from a staff number (MD62, MR-SEC-19), and the rows stand
 * on SASBO's workplaces; and invented rows are exactly what production must never hold. The complement each visit
 * found is the workplace's own, so the seed changes no workplace's complement.
 *
 * <h2>When absent, by owner, workplace and status</h2>
 * One read of what the seed has written before, then only what is missing: a second start writes nothing, and a row a
 * person changed is never touched. The visits are stamped CAPTURED NOW, by the injected clock: a taken month reads the
 * visits captured before it was taken, and this runs before the seed's months are taken, so what they freeze and what
 * they read agree.
 */
@Slf4j
@Component
public class VisitDraftSeeder {

    /** The demonstration organiser of the sign-in seed (contracts/signin.md): whose drafts these are. */
    public static final String SEED_OWNER = "demo.organiser";

    /**
     * That organiser's area -- the drafts' workplaces are in it, so its organiser may submit them. Area L is in the
     * region where the invented population stands, so its workplaces' listings have members to show.
     */
    public static final String SEED_AREA = "L";

    /** Who the history says saved and submitted them: the seed, as the demonstration's past says (03.7). */
    public static final String SEED_ACTOR = DemonstrationMemberSeeder.SEED_ACTOR;

    /** The invented organiser's name on each visit. */
    static final String ORGANISER = "INVENTED ORGANISER";

    /**
     * One invented visit: which of the area's workplaces (by its place in R/A order), saved or submitted, its day --
     * a day of last month for a submitted visit, of this month for a draft, none at all where {@code day} is 0 -- its
     * times, and what the organiser found besides the complement.
     */
    record Planned(int nth, String status, int day, LocalTime in, LocalTime out, int membership, int nonMembers,
                   int recruited, int interviewed) {
    }

    static final List<Planned> PLANNED = List.of(
            new Planned(0, "SUBMITTED", 5, LocalTime.of(9, 0), LocalTime.of(10, 30), 31, 6, 2, 4),
            new Planned(1, "SUBMITTED", 12, LocalTime.of(11, 0), LocalTime.of(12, 15), 18, 3, 1, 3),
            new Planned(2, "SUBMITTED", 19, LocalTime.of(14, 0), LocalTime.of(15, 0), 24, 9, 3, 6),
            new Planned(3, "DRAFT", 1, LocalTime.of(8, 30), LocalTime.of(9, 45), 12, 4, 0, 2),
            new Planned(4, "DRAFT", 1, LocalTime.of(10, 0), null, 20, 2, 1, 1),
            new Planned(5, "DRAFT", 0, null, null, 0, 0, 0, 0));

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SasboEnvironment environment;
    private final WorkplaceSwitch workplaceSwitch;
    private final Clock clock;

    public VisitDraftSeeder(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                            SasboEnvironment environment, WorkplaceSwitch workplaceSwitch, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.environment = environment;
        this.workplaceSwitch = workplaceSwitch;
        this.clock = clock;
    }

    /** @return how many visit drafts this call wrote */
    public int seed() {
        if (environment.isProduction()) {
            log.info("Invented visit drafts are never seeded under the production marker");
            return 0;
        }
        if (!workplaceSwitch.staffNumbersServed()) {
            log.info("Invented visit drafts are seeded only where the switch says all (SASBO_WORKPLACES)");
            return 0;
        }
        Integer written = transactions.execute(status -> write());
        int added = written == null ? 0 : written;
        log.info("Invented visit drafts: {} added", added);
        return added;
    }

    /** An open workplace of the seed's area, in R/A order, and the complement on record there. */
    private record Place(long id, int complement) {
    }

    private int write() {
        List<Place> places = jdbc.query("SELECT w.id, w.staff_complement FROM workplaces w "
                        + "JOIN sasbo_areas a ON a.id = w.area_id WHERE UPPER(a.letter) = :area AND w.closed_on IS NULL "
                        + "ORDER BY w.ra_number LIMIT :wanted",
                new MapSqlParameterSource("area", SEED_AREA).addValue("wanted", PLANNED.size()),
                (rs, n) -> new Place(rs.getLong("id"), rs.getInt("staff_complement")));
        if (places.isEmpty()) {
            return 0;
        }
        Set<String> present = new HashSet<>(jdbc.query("SELECT workplace_id, status FROM workplace_visit_drafts "
                        + "WHERE owner = :owner AND created_by = :actor",
                new MapSqlParameterSource("owner", SEED_OWNER).addValue("actor", SEED_ACTOR),
                (rs, n) -> rs.getLong("workplace_id") + "|" + rs.getString("status")));
        LocalDate today = LocalDate.now(clock.withZone(MonthRange.ZONE));
        YearMonth thisMonth = YearMonth.from(today);
        Timestamp now = Timestamp.from(clock.instant());
        int added = 0;
        for (Planned planned : PLANNED) {
            Place place = places.get(planned.nth() % places.size());
            if (!present.add(place.id() + "|" + planned.status())) {
                continue;
            }
            boolean submitted = "SUBMITTED".equals(planned.status());
            LocalDate day = planned.day() == 0 ? null
                    : submitted ? thisMonth.minusMonths(1).atDay(planned.day()) : thisMonth.atDay(planned.day());
            MapSqlParameterSource row = new MapSqlParameterSource("workplace", place.id())
                    .addValue("status", planned.status())
                    .addValue("day", day == null ? null : Date.valueOf(day))
                    .addValue("organiser", day == null ? null : ORGANISER)
                    .addValue("timeIn", planned.in() == null ? null : Time.valueOf(planned.in()))
                    .addValue("timeOut", planned.out() == null ? null : Time.valueOf(planned.out()))
                    .addValue("complement", day == null ? null : place.complement())
                    .addValue("membership", planned.membership() == 0 ? null : planned.membership())
                    .addValue("nonMembers", planned.nonMembers() == 0 ? null : planned.nonMembers())
                    .addValue("recruited", planned.recruited() == 0 ? null : planned.recruited())
                    .addValue("interviewed", planned.interviewed() == 0 ? null : planned.interviewed())
                    .addValue("owner", SEED_OWNER)
                    .addValue("actor", SEED_ACTOR)
                    .addValue("now", now)
                    .addValue("visit", submitted ? visit(row(place, day, planned), now) : null)
                    .addValue("submittedAt", submitted ? now : null)
                    .addValue("submittedBy", submitted ? SEED_ACTOR : null)
                    .addValue("version", submitted ? 1 : 0);
            jdbc.update("""
                    INSERT INTO workplace_visit_drafts (workplace_id, status, visited_on, organiser, time_in, time_out,
                      complement_found, membership_found, non_members, recruited, non_members_interviewed, owner,
                      created_by, created_at, updated_at, submitted_at, submitted_by, visit_id, version)
                    VALUES (:workplace, :status, :day, :organiser, :timeIn, :timeOut, :complement, :membership,
                      :nonMembers, :recruited, :interviewed, :owner, :actor, :now, :now, :submittedAt, :submittedBy,
                      :visit, :version)
                    """, row);
            added++;
        }
        return added;
    }

    /** The visit a submitted draft became, as the record path writes one: its day known, captured here. */
    private MapSqlParameterSource row(Place place, LocalDate day, Planned planned) {
        return new MapSqlParameterSource("workplace", place.id())
                .addValue("day", Date.valueOf(day))
                .addValue("organiser", ORGANISER)
                .addValue("timeIn", planned.in() == null ? null : Time.valueOf(planned.in()))
                .addValue("timeOut", planned.out() == null ? null : Time.valueOf(planned.out()))
                .addValue("complement", place.complement())
                .addValue("membership", planned.membership())
                .addValue("nonMembers", planned.nonMembers())
                .addValue("recruited", planned.recruited())
                .addValue("interviewed", planned.interviewed());
    }

    private long visit(MapSqlParameterSource visit, Timestamp now) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO workplace_visits (workplace_id, visited_on, day_known, organiser, time_in, time_out,
                  complement_found, complement_before, membership_found, non_members, recruited,
                  non_members_interviewed, seeded, captured_at)
                VALUES (:workplace, :day, TRUE, :organiser, :timeIn, :timeOut, :complement, NULL, :membership,
                  :nonMembers, :recruited, :interviewed, FALSE, :now)
                """, visit.addValue("now", now), key, new String[] {"id"});
        return key.getKey().longValue();
    }
}

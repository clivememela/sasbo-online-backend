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
import java.time.Instant;
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
 * The rows stand on SASBO's workplaces, and invented rows are exactly what production must never hold.
 *
 * <h2>No complement found (October build review)</h2>
 * An invented visit takes no staff complement: one that did would be a person's reading of a real branch's staff, which
 * the workplaces' seed keeps over the branch list on its next refresh -- three of SASBO's branches frozen at an invented
 * figure. So {@code complement_found} is empty on every invented visit and draft.
 *
 * <h2>Captured on its own day</h2>
 * Each visit is stamped captured at 10:00 in SASBO's zone on the day it was made -- never after the clock's now -- as an
 * organiser's would be: a taken month reads the visits captured before it was taken, so a visit of last month is in
 * last month's figures, whenever the month is taken.
 *
 * <h2>When absent, by owner and workplace</h2>
 * One read of the workplaces the seed has written a draft for, then only the missing: a second start writes nothing, a
 * row a person changed is never touched, and a seeded draft the organiser submitted does not come back as a new one.
 *
 * <p>At integration it is called through {@code DataSeeder.demonstration(...)}, with the October build's other
 * demonstration seeders.
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

    /** When in its day an invented visit is captured, in SASBO's zone. */
    static final LocalTime CAPTURED_AT = LocalTime.of(10, 0);

    /**
     * One invented visit: which of the area's workplaces (by its place in R/A order), saved or submitted, its day --
     * a day of last month for a submitted visit, of this month for a draft, none at all where {@code day} is 0 -- its
     * times, and what the organiser found of the membership.
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

    private int write() {
        List<Long> places = jdbc.queryForList("SELECT w.id FROM workplaces w "
                        + "JOIN sasbo_areas a ON a.id = w.area_id WHERE UPPER(a.letter) = :area AND w.closed_on IS NULL "
                        + "ORDER BY w.ra_number LIMIT :wanted",
                new MapSqlParameterSource("area", SEED_AREA).addValue("wanted", PLANNED.size()), Long.class);
        if (places.isEmpty()) {
            return 0;
        }
        Set<Long> present = new HashSet<>(jdbc.queryForList("SELECT workplace_id FROM workplace_visit_drafts "
                        + "WHERE owner = :owner AND created_by = :actor",
                new MapSqlParameterSource("owner", SEED_OWNER).addValue("actor", SEED_ACTOR), Long.class));
        LocalDate today = LocalDate.now(clock.withZone(MonthRange.ZONE));
        YearMonth thisMonth = YearMonth.from(today);
        Instant now = clock.instant();
        int added = 0;
        for (Planned planned : PLANNED) {
            long place = places.get(planned.nth() % places.size());
            if (!present.add(place)) {
                continue;
            }
            boolean submitted = "SUBMITTED".equals(planned.status());
            LocalDate day = planned.day() == 0 ? null
                    : submitted ? thisMonth.minusMonths(1).atDay(planned.day()) : thisMonth.atDay(planned.day());
            Timestamp captured = Timestamp.from(capturedOn(day, now));
            MapSqlParameterSource row = new MapSqlParameterSource("workplace", place)
                    .addValue("status", planned.status())
                    .addValue("day", day == null ? null : Date.valueOf(day))
                    .addValue("organiser", day == null ? null : ORGANISER)
                    .addValue("timeIn", planned.in() == null ? null : Time.valueOf(planned.in()))
                    .addValue("timeOut", planned.out() == null ? null : Time.valueOf(planned.out()))
                    .addValue("membership", planned.membership() == 0 ? null : planned.membership())
                    .addValue("nonMembers", planned.nonMembers() == 0 ? null : planned.nonMembers())
                    .addValue("recruited", planned.recruited() == 0 ? null : planned.recruited())
                    .addValue("interviewed", planned.interviewed() == 0 ? null : planned.interviewed())
                    .addValue("owner", SEED_OWNER)
                    .addValue("actor", SEED_ACTOR)
                    .addValue("at", captured)
                    .addValue("visit", submitted ? visit(place, day, planned, captured) : null)
                    .addValue("submittedAt", submitted ? captured : null)
                    .addValue("submittedBy", submitted ? SEED_ACTOR : null)
                    .addValue("version", submitted ? 1 : 0);
            jdbc.update("""
                    INSERT INTO workplace_visit_drafts (workplace_id, status, visited_on, organiser, time_in, time_out,
                      complement_found, membership_found, non_members, recruited, non_members_interviewed, owner,
                      created_by, created_at, updated_at, submitted_at, submitted_by, visit_id, version)
                    VALUES (:workplace, :status, :day, :organiser, :timeIn, :timeOut, NULL, :membership,
                      :nonMembers, :recruited, :interviewed, :owner, :actor, :at, :at, :submittedAt, :submittedBy,
                      :visit, :version)
                    """, row);
            added++;
        }
        return added;
    }

    /** 10:00 in SASBO's zone on the visit's day -- or now, where that is still to come or the visit has no day yet. */
    private static Instant capturedOn(LocalDate day, Instant now) {
        if (day == null) {
            return now;
        }
        Instant onTheDay = day.atTime(CAPTURED_AT).atZone(MonthRange.ZONE).toInstant();
        return onTheDay.isAfter(now) ? now : onTheDay;
    }

    /** The visit a submitted draft became, as the record path writes one: its day known, captured here, no complement. */
    private long visit(long place, LocalDate day, Planned planned, Timestamp captured) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update("""
                INSERT INTO workplace_visits (workplace_id, visited_on, day_known, organiser, time_in, time_out,
                  complement_found, complement_before, membership_found, non_members, recruited,
                  non_members_interviewed, seeded, captured_at)
                VALUES (:workplace, :day, TRUE, :organiser, :timeIn, :timeOut, NULL, NULL, :membership,
                  :nonMembers, :recruited, :interviewed, FALSE, :captured)
                """, new MapSqlParameterSource("workplace", place)
                        .addValue("day", Date.valueOf(day))
                        .addValue("organiser", ORGANISER)
                        .addValue("timeIn", planned.in() == null ? null : Time.valueOf(planned.in()))
                        .addValue("timeOut", planned.out() == null ? null : Time.valueOf(planned.out()))
                        .addValue("membership", planned.membership())
                        .addValue("nonMembers", planned.nonMembers())
                        .addValue("recruited", planned.recruited())
                        .addValue("interviewed", planned.interviewed())
                        .addValue("captured", captured),
                key, new String[] {"id"});
        return key.getKey().longValue();
    }
}

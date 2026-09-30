package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MV-1: the summary's arithmetic is a PROPERTY, not three examples. Over a generated population -- members joining,
 * leaving, coming back and leaving again across eighteen months, seeded, the seed in every assertion -- every month's
 * figures are what an independent model of the same members gives: on record, suspended, the opening, joined, left,
 * reinstated and the year to date. And the rules hold of them: paying and suspended are on record (rule V); the month
 * closes -- closing is opening, plus joined, less left, plus reinstated, with nothing unexplained (rule S); a month
 * opens where the one before closed; every grouping's rows sum to the same whole (MV-16). The edge shapes no example
 * thinks of are in it on purpose: a member who joins and leaves in one month, who leaves and returns in one, who leaves
 * twice, who leaves, returns and leaves again in one, and changes at the last second of a month and the first of the next.
 *
 * <p>A fixed seed, so a run is repeatable; {@code -Dmv1.seed=} tries another. On PostgreSQL, the database every host
 * runs, from the fixture's lists and workplaces with its own members taken out. On H2, at this population, the first
 * figure outlived its four-second bound (run 36772004942), so the property is held where the figures are served.
 */
@DisplayName("MV-1: the summary's arithmetic, over a generated population, on PostgreSQL")
class MemberSummaryPropertyIT extends AbstractSwitchedOnIT {

    static final long SEED = Long.getLong("mv1.seed", 20260930L);

    static final YearMonth FIRST = YearMonth.of(2025, 4);
    static final YearMonth LAST = SummaryFixture.MONTH;

    /** The generated members; the edge shapes are added to them. */
    static final int GENERATED = 240;

    /** The last instant anything happens: a day before the fixture's clock. */
    static final Instant END = SummaryFixture.NOW.minus(Duration.ofDays(1));

    static final List<String> ON_RECORD = List.of("Active", "Pending");
    static final List<String> LEAVING = List.of("Resigned", "Retired", "Terminated");

    /** The workplaces the fixture puts on record, and none. */
    static final Integer[] PLACES = {900001, 900002, 900003, 900004, null};

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberSummaryService summaries;

    record Change(Instant at, String from, String to) {
    }

    /** One member as the model knows them: when they joined, where they stand, and every change of status. */
    record Generated(String number, LocalDate joined, Integer ra, String initial, List<Change> changes) {

        String now() {
            return changes.isEmpty() ? initial : changes.getLast().to();
        }

        /** The status at an instant: what their first change at or after it moved them FROM, or the one they hold. */
        String at(Instant instant) {
            for (Change change : changes) {
                if (!change.at().isBefore(instant)) {
                    return change.from();
                }
            }
            return now();
        }
    }

    @Test
    @DisplayName("🔒 MV-1, MV-16: eighteen months of a generated population -- each month the model's figures; rule S closes; a month opens where the last closed; every grouping sums to the whole")
    void theArithmeticIsAProperty() {
        List<Generated> population = generate(new Random(SEED));
        write(population);

        long previousOnRecord = -1;
        int sumsChecked = 0;
        int monthsWithMovement = 0;
        for (YearMonth month = FIRST; !month.isAfter(LAST); month = month.plusMonths(1)) {
            MonthRange range = MonthRange.of(month);
            long[] model = model(population, range);
            MemberSummaryDto answer = summaries.summary(month.toString(), "group", null, null);
            MemberSummaryDto.Figures whole = answer.totals();
            String at = "seed " + SEED + ", " + month;

            assertThat(new long[] {whole.onRecord(), whole.suspended(), whole.opening(), whole.joined(), whole.left(),
                    whole.reinstated(), whole.joinedYearToDate(), whole.leftYearToDate()})
                    .as("%s: on record, suspended, opening, joined, left, reinstated, joined and left in the year -- "
                            + "as the model gives them", at).containsExactly(model);
            assertThat(whole.paying() + whole.suspended()).as("%s: rule V", at).isEqualTo(whole.onRecord());
            assertThat(whole.unexplained()).as("%s: rule S closes, nothing unexplained", at).isZero();
            assertThat(whole.onRecord()).as("%s: closing is opening, plus joined, less left, plus reinstated", at)
                    .isEqualTo(whole.opening() + whole.joined() - whole.left() + whole.reinstated());
            if (previousOnRecord >= 0) {
                assertThat(whole.opening()).as("%s opens where the month before closed", at).isEqualTo(previousOnRecord);
            }
            previousOnRecord = whole.onRecord();
            if (whole.joined() + whole.left() + whole.reinstated() > 0) {
                monthsWithMovement++;
            }

            for (String by : SummaryFixture.GROUPINGS) {
                MemberSummaryDto grouped = summaries.summary(month.toString(), by, null, null);
                MemberSummaryDto.Figures total = grouped.totals();
                assertThat(new long[] {total.onRecord(), total.suspended(), total.paying()})
                        .as("%s by %s: the same whole", at, by)
                        .containsExactly(whole.onRecord(), whole.suspended(), whole.paying());
                if (grouped.rows().stream().allMatch(row -> row.figures() != null)) {
                    assertThat(new long[] {
                            grouped.rows().stream().mapToLong(row -> row.figures().onRecord()).sum(),
                            grouped.rows().stream().mapToLong(row -> row.figures().suspended()).sum(),
                            grouped.rows().stream().mapToLong(row -> row.figures().paying()).sum()})
                            .as("%s by %s: the rows sum to the whole (MV-16)", at, by)
                            .containsExactly(whole.onRecord(), whole.suspended(), whole.paying());
                    sumsChecked++;
                }
            }
        }
        assertThat(monthsWithMovement).as("seed %d: months with joiners, leavers or returns -- the property is tested, "
                + "not assumed", SEED).isGreaterThanOrEqualTo(15);
        assertThat(sumsChecked).as("seed %d: groupings whose rows were all answered, and summed", SEED)
                .isGreaterThanOrEqualTo(60);
    }

    @Test
    @DisplayName("must-pass: the generated population is on record as generated -- every member and every change")
    void thePopulationIsTheOneGenerated() {
        List<Generated> population = generate(new Random(SEED));
        write(population);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM members", Long.class)).isEqualTo((long) population.size());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes", Long.class))
                .isEqualTo(population.stream().mapToLong(member -> member.changes().size()).sum());
    }

    // ------------------------------------------------------------------------------------------------ the model

    /** On record, suspended, opening, joined, left, reinstated, joined and left in the year -- the SQL's rules, in Java. */
    static long[] model(List<Generated> population, MonthRange range) {
        LocalDate yearStart = range.firstDayOfYear();
        Instant yearStartInstant = yearStart.atStartOfDay(MonthRange.ZONE).toInstant();
        long onRecord = 0, suspended = 0, opening = 0, joined = 0, left = 0, reinstated = 0, joinedYear = 0, leftYear = 0;
        for (Generated member : population) {
            if (member.joined().isBefore(range.firstDayOfNext())) {
                MemberStatusKind end = MemberStatuses.kindOf(member.at(range.next()));
                onRecord += onRecord(end) ? 1 : 0;
                suspended += end == MemberStatusKind.SUSPENDED ? 1 : 0;
            }
            if (member.joined().isBefore(range.firstDay())) {
                opening += onRecord(MemberStatuses.kindOf(member.at(range.start()))) ? 1 : 0;
            }
            if (!member.joined().isBefore(range.firstDay()) && member.joined().isBefore(range.firstDayOfNext())) {
                joined++;
            }
            if (!member.joined().isBefore(yearStart) && member.joined().isBefore(range.firstDayOfNext())) {
                joinedYear++;
            }
            for (Change change : member.changes()) {
                boolean into = leaving(change.to()) && !leaving(change.from());
                boolean outOf = leaving(change.from()) && !leaving(change.to());
                if (!change.at().isBefore(range.start()) && change.at().isBefore(range.next())) {
                    left += into ? 1 : 0;
                    reinstated += outOf ? 1 : 0;
                }
                if (!change.at().isBefore(yearStartInstant) && change.at().isBefore(range.next())) {
                    leftYear += into ? 1 : 0;
                }
            }
        }
        return new long[] {onRecord, suspended, opening, joined, left, reinstated, joinedYear, leftYear};
    }

    private static boolean onRecord(MemberStatusKind kind) {
        return kind == MemberStatusKind.NEITHER || kind == MemberStatusKind.SUSPENDED;
    }

    private static boolean leaving(String status) {
        return MemberStatuses.kindOf(status) == MemberStatusKind.LEAVING;
    }

    // ------------------------------------------------------------------------------------------------ the population

    static List<Generated> generate(Random random) {
        List<Generated> out = new ArrayList<>();
        LocalDate from = LocalDate.of(2024, 10, 1);
        long days = ChronoUnit.DAYS.between(from, END.atZone(MonthRange.ZONE).toLocalDate());
        for (int n = 0; n < GENERATED; n++) {
            LocalDate joined = from.plusDays(random.nextLong(days));
            String status = random.nextInt(10) == 0 ? "Pending" : "Active";
            String initial = status;
            List<Change> changes = new ArrayList<>();
            Instant cursor = joined.atTime(LocalTime.of(9, 0)).atZone(MonthRange.ZONE).toInstant();
            while (true) {
                cursor = cursor.plus(Duration.ofMinutes(60 + random.nextInt(60 * 24 * 50)));
                if (cursor.isAfter(END)) {
                    break;
                }
                MemberStatusKind kind = MemberStatuses.kindOf(status);
                int roll = random.nextInt(100);
                String next = null;
                if (kind == MemberStatusKind.NEITHER) {
                    next = roll < 10 ? LEAVING.get(random.nextInt(LEAVING.size())) : roll < 15 ? "Suspended" : null;
                } else if (kind == MemberStatusKind.SUSPENDED) {
                    next = roll < 35 ? "Active" : roll < 45 ? LEAVING.get(random.nextInt(LEAVING.size())) : null;
                } else if (roll < 8) {
                    next = ON_RECORD.get(random.nextInt(ON_RECORD.size()));
                }
                if (next != null) {
                    changes.add(new Change(cursor, status, next));
                    status = next;
                }
            }
            out.add(new Generated(String.format("M-7%05d", n), joined, PLACES[random.nextInt(PLACES.length)], initial,
                    changes));
        }
        // The shapes no example thinks of.
        out.add(edge(900, LocalDate.of(2026, 3, 3), 900001, "Active",
                change("2026-03-20T10:00", "Active", "Resigned")));
        out.add(edge(901, LocalDate.of(2025, 6, 10), 900003, "Active",
                change("2026-02-05T10:00", "Active", "Terminated"), change("2026-02-25T10:00", "Terminated", "Active")));
        out.add(edge(902, LocalDate.of(2025, 5, 1), 900002, "Active",
                change("2025-11-10T10:00", "Active", "Resigned"), change("2026-01-15T10:00", "Resigned", "Active"),
                change("2026-04-02T10:00", "Active", "Retired")));
        out.add(edge(903, LocalDate.of(2025, 1, 10), null, "Pending",
                change("2026-05-03T10:00", "Pending", "Resigned"), change("2026-05-10T10:00", "Resigned", "Active"),
                change("2026-05-28T10:00", "Active", "Terminated")));
        out.add(edge(904, LocalDate.of(2025, 8, 4), 900004, "Active",
                change("2025-08-06T10:00", "Active", "Suspended"), change("2025-08-29T10:00", "Suspended", "Active")));
        // The last second of July, SAST, and the first of August: one month each.
        out.add(edge(905, LocalDate.of(2025, 2, 2), 900001, "Active",
                change("2025-07-31T23:59:59", "Active", "Resigned"), change("2025-08-01T00:00", "Resigned", "Active")));
        return out;
    }

    private static Generated edge(int n, LocalDate joined, Integer ra, String initial, Change... changes) {
        return new Generated(String.format("M-7%05d", n), joined, ra, initial, List.of(changes));
    }

    /** A change at a local time in SAST. */
    private static Change change(String local, String from, String to) {
        return new Change(java.time.LocalDateTime.parse(local).atZone(MonthRange.ZONE).toInstant(), from, to);
    }

    /** The fixture's lists and workplaces, its own members taken out, and the generated ones in. */
    private void write(List<Generated> population) {
        SummaryFixture.insert(jdbc, members);
        // Emptied as the lane's own reset empties them, by TRUNCATE.
        jdbc.execute("TRUNCATE member_status_changes, members CASCADE");
        for (Generated generated : population) {
            Member member = MemberFixture.withSurname("GENERATED");
            member.setMembershipNumber(generated.number());
            member.setStatus(generated.now());
            member.setJoinDate(generated.joined());
            Integer id = members.save(member).getId();
            if (generated.ra() != null) {
                jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) "
                        + "WHERE id = ?", generated.ra(), id);
            }
            for (Change change : generated.changes()) {
                // A leaving carries a deletion code, as the table's check requires; a suspension is set by hand, with
                // none (the fixture has no suspension code); a return is REINSTATED.
                boolean leaves = leaving(change.to());
                jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                                + "changed_at, deletion_code_id) VALUES (?, ?, ?, ?, 'system', ?, "
                                + "(SELECT id FROM deletion_codes WHERE sasbo_code = 2 AND ?))", id, change.from(),
                        change.to(), leaves ? "DELETION_CODE" : "Suspended".equals(change.to()) ? "CAPTURED" : "REINSTATED",
                        OffsetDateTime.ofInstant(change.at(), ZoneOffset.UTC), leaves);
            }
        }
    }
}

package za.co.titandynamix.sasbo_online_backend.support;

import org.springframework.jdbc.core.JdbcTemplate;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.MonthEndCount;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ONE population and ONE expected answer for the figures of September 2026 (02.2) -- put on record the same way on H2
 * and on PostgreSQL, so that the summary is proved figure for figure the same on both (MV-49, BR65). Every row is
 * invented (MD57).
 *
 * <pre>
 * Workplace   R/A     area  table code  committee  complement   members
 * W1          900001  A     901 (ONE)   Q01        20           m1-m6 Active; m7 Suspended; m8 joined 10 Sep;
 *                                                              m9 left in June, reinstated 8 Sep
 * W2          900002  A     902 (TWO)   Q01        10           m10-m12 Active
 * W3          900003  B     901 (ONE)   R01         5           m13-m14 Active; m15 left 5 Sep (code 2);
 *                                                              m16 left 20 Aug (code 1); m17 joined 2 Sep, left 12 Sep (code 5)
 * W4          900004  B     none        R01         3           m18 Active
 * none                                                          m19-m20 Active; m21 "Inactive" -- a status nobody knows
 * </pre>
 * Employer ONE (901) is INVENTED BANK in group ONE, core; employer TWO (902) INVENTED INSURER in group TWO. Area A is in
 * region NORTH, B in SOUTH.
 */
public final class SummaryFixture {

    /** The clock the figures are read at: the middle of September 2026, in the morning in Johannesburg. */
    public static final Instant NOW = Instant.parse("2026-09-15T08:00:00Z");
    public static final YearMonth MONTH = YearMonth.of(2026, 9);

    private SummaryFixture() {
    }

    /** SASBO's lists, the workplaces, the members and their changes -- all invented. */
    public static void insert(JdbcTemplate jdbc, MemberRepository members) {
        jdbc.update("INSERT INTO sasbo_regions (code, name) VALUES ('NORTH', 'INVENTED NORTH'), ('SOUTH', 'INVENTED SOUTH')");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'A', id FROM sasbo_regions WHERE code = 'NORTH'");
        jdbc.update("INSERT INTO sasbo_areas (letter, region_id) SELECT 'B', id FROM sasbo_regions WHERE code = 'SOUTH'");
        jdbc.update("INSERT INTO employer_groups (name, core, ordinal) VALUES ('INVENTED GROUP ONE', TRUE, 1), "
                + "('INVENTED GROUP TWO', FALSE, 2)");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 1, 'INVENTED BANK', id FROM employer_groups "
                + "WHERE name = 'INVENTED GROUP ONE'");
        jdbc.update("INSERT INTO employers (ordinal, name, group_id) SELECT 2, 'INVENTED INSURER', id FROM employer_groups "
                + "WHERE name = 'INVENTED GROUP TWO'");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 901, 'INVENTED BANK', id, NULL "
                + "FROM employers WHERE ordinal = 1");
        jdbc.update("INSERT INTO table_codes (code, name, employer_id, part) SELECT 902, 'INVENTED INSURER', id, NULL "
                + "FROM employers WHERE ordinal = 2");
        jdbc.update("INSERT INTO committee_provinces (letter, name) VALUES ('Q', NULL), ('R', NULL)");
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'Q01', id, "
                + "'INVENTED NORTH COMMITTEE', NULL FROM committee_provinces WHERE letter = 'Q'");
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'R01', id, "
                + "'INVENTED SOUTH COMMITTEE', NULL FROM committee_provinces WHERE letter = 'R'");
        jdbc.update("INSERT INTO deletion_codes (sasbo_code, name) VALUES (1, 'Left service'), (2, 'Resigned Society'), "
                + "(5, 'Subscription in Arrears')");
        workplace(jdbc, 900001, "INVENTED MAIN STREET", 901, "A", "Q01", 20);
        workplace(jdbc, 900002, "INVENTED HEAD OFFICE", 902, "A", "Q01", 10);
        workplace(jdbc, 900003, "INVENTED HARBOUR", 901, "B", "R01", 5);
        workplace(jdbc, 900004, "INVENTED OUTPOST", null, "B", "R01", 3);

        LocalDate long_ago = LocalDate.of(2020, 1, 1);
        Map<Integer, Integer> ids = new LinkedHashMap<>();
        for (int m = 1; m <= 6; m++) {
            ids.put(m, member(members, m, "Active", long_ago));
        }
        ids.put(7, member(members, 7, "Suspended", long_ago));
        ids.put(8, member(members, 8, "Active", LocalDate.of(2026, 9, 10)));
        ids.put(9, member(members, 9, "Active", LocalDate.of(2019, 1, 1)));
        for (int m = 10; m <= 12; m++) {
            ids.put(m, member(members, m, "Active", LocalDate.of(2021, 1, 1)));
        }
        ids.put(13, member(members, 13, "Active", long_ago));
        ids.put(14, member(members, 14, "Active", long_ago));
        ids.put(15, member(members, 15, "Resigned", long_ago));
        ids.put(16, member(members, 16, "Retired", long_ago));
        ids.put(17, member(members, 17, "Terminated", LocalDate.of(2026, 9, 2)));
        ids.put(18, member(members, 18, "Active", long_ago));
        ids.put(19, member(members, 19, "Active", LocalDate.of(2022, 1, 1)));
        ids.put(20, member(members, 20, "Active", LocalDate.of(2022, 1, 1)));
        ids.put(21, member(members, 21, "Inactive", LocalDate.of(2022, 1, 1)));

        standAt(jdbc, 900001, ids, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        standAt(jdbc, 900002, ids, 10, 11, 12);
        standAt(jdbc, 900003, ids, 13, 14, 15, 16, 17);
        standAt(jdbc, 900004, ids, 18);

        change(jdbc, ids.get(9), "Active", "Resigned", "DELETION_CODE", 2, "2026-06-01T08:00:00Z");
        change(jdbc, ids.get(9), "Resigned", "Active", "REINSTATED", null, "2026-09-08T08:00:00Z");
        change(jdbc, ids.get(15), "Active", "Resigned", "DELETION_CODE", 2, "2026-09-05T08:00:00Z");
        change(jdbc, ids.get(16), "Active", "Retired", "DELETION_CODE", 1, "2026-08-20T08:00:00Z");
        change(jdbc, ids.get(17), "Active", "Terminated", "DELETION_CODE", 5, "2026-09-12T08:00:00Z");
    }

    private static void workplace(JdbcTemplate jdbc, int ra, String name, Integer tableCode, String area,
                                  String committee, int complement) {
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                        + "committee_code, committee_id, staff_complement, cl_code) VALUES (?, ?, 'INVENTED EMPLOYER', "
                        + "(SELECT id FROM table_codes WHERE code = ?), ?, (SELECT id FROM sasbo_areas WHERE letter = ?), "
                        + "?, (SELECT id FROM committees WHERE code = ?), ?, '00000000')",
                ra, name, tableCode, tableCode == null ? null : "NAME", area, committee, committee, complement);
    }

    private static Integer member(MemberRepository members, int m, String status, LocalDate joined) {
        Member member = MemberFixture.withSurname("Invented" + m);
        member.setMembershipNumber(String.format("M-8%05d", m));
        member.setStatus(status);
        member.setJoinDate(joined);
        return members.save(member).getId();
    }

    private static void standAt(JdbcTemplate jdbc, int ra, Map<Integer, Integer> ids, int... members) {
        for (int m : members) {
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = ?) WHERE id = ?",
                    ra, ids.get(m));
        }
    }

    private static void change(JdbcTemplate jdbc, Integer member, String from, String to, String reason,
                               Integer deletionCode, String at) {
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, "
                        + "changed_at, deletion_code_id) VALUES (?, ?, ?, ?, 'system', ?, "
                        + "(SELECT id FROM deletion_codes WHERE sasbo_code = ?))",
                member, from, to, reason, OffsetDateTime.ofInstant(Instant.parse(at), ZoneOffset.UTC), deletionCode);
    }

    // ------------------------------------------------------------------------------------------ the expected answer

    /** A row's figures, as the fixture's arithmetic gives them: on record, suspended, joined, left, reinstated, opening, complement. */
    public record Expected(long onRecord, long suspended, long joined, long left, long reinstated, long opening,
                           Long complement) {
    }

    /** The whole of September 2026: seventeen on record, one suspended, two joined, two left, one reinstated. */
    public static final Expected WHOLE = new Expected(17, 1, 2, 2, 1, 16, 38L);

    /** Each grouping's rows, in the order the answer lists them. {@code null} is a row answered without its figures. */
    public static Map<String, Expected> rows(String by) {
        Map<String, Expected> rows = new LinkedHashMap<>();
        switch (by) {
            case "group" -> {
                rows.put("INVENTED GROUP ONE", new Expected(11, 1, 2, 2, 1, 10, 25L));
                rows.put("INVENTED GROUP TWO", new Expected(3, 0, 0, 0, 0, 3, 10L));
                rows.put("~NO_TABLE_CODE", new Expected(1, 0, 0, 0, 0, 1, 3L));
                rows.put("~NO_WORKPLACE", new Expected(2, 0, 0, 0, 0, 2, 0L));
            }
            case "employer", "tableCode" -> {
                // Two, being three, is FEWER THAN FIVE; beside the total it would be given back, so ONE is withheld.
                rows.put(by.equals("employer") ? "1" : "901", null);
                rows.put(by.equals("employer") ? "2" : "902", null);
                rows.put("~NO_TABLE_CODE", new Expected(1, 0, 0, 0, 0, 1, 3L));
                rows.put("~NO_WORKPLACE", new Expected(2, 0, 0, 0, 0, 2, 0L));
            }
            case "area" -> {
                rows.put("A", new Expected(12, 1, 1, 0, 1, 10, 30L));
                rows.put("B", new Expected(3, 0, 1, 2, 0, 4, 8L));
                rows.put("~NO_WORKPLACE", new Expected(2, 0, 0, 0, 0, 2, 0L));
            }
            case "region" -> {
                rows.put("NORTH", new Expected(12, 1, 1, 0, 1, 10, 30L));
                rows.put("SOUTH", new Expected(3, 0, 1, 2, 0, 4, 8L));
                rows.put("~NO_WORKPLACE", new Expected(2, 0, 0, 0, 0, 2, 0L));
            }
            case "committee" -> {
                rows.put("Q01", new Expected(12, 1, 1, 0, 1, 10, 30L));
                rows.put("R01", new Expected(3, 0, 1, 2, 0, 4, 8L));
                rows.put("~NO_WORKPLACE", new Expected(2, 0, 0, 0, 0, 2, 0L));
            }
            default -> throw new IllegalArgumentException(by);
        }
        return rows;
    }

    /** The six groupings. */
    public static final List<String> GROUPINGS = List.of("group", "employer", "tableCode", "area", "region", "committee");

    /** The answer, held to the fixture's arithmetic: the whole, every row, and what could not be placed. */
    public static void assertAnswer(MemberSummaryDto answer, String by) {
        assertThat(answer.month()).isEqualTo(MONTH.toString());
        assertFigures("the whole", answer.totals(), WHOLE);
        assertThat(answer.totals().joinedYearToDate()).as("joined in 2026").isEqualTo(2);
        assertThat(answer.totals().leftYearToDate()).as("left in 2026: June, August and two in September").isEqualTo(4);
        assertThat(answer.totals().penetration()).isEqualByComparingTo("0.4211");

        Map<String, Expected> expected = rows(by);
        assertThat(answer.rows()).extracting(MemberSummaryDto.Row::key).containsExactlyElementsOf(expected.keySet());
        for (MemberSummaryDto.Row row : answer.rows()) {
            Expected figures = expected.get(row.key());
            if (figures == null) {
                assertThat(row.figures()).as("row %s answers no figures", row.key()).isNull();
                assertThat(row.fewerThanFive() || row.withheld()).isTrue();
            } else {
                assertThat(row.fewerThanFive()).isFalse();
                assertThat(row.withheld()).isFalse();
                assertFigures(by + " " + row.key(), row.figures(), figures);
            }
        }
        long shown = answer.rows().stream().filter(r -> r.figures() != null).mapToLong(r -> r.figures().onRecord()).sum();
        if (answer.rows().stream().allMatch(r -> r.figures() != null)) {
            assertThat(shown).as("MV-16: the rows sum to the whole").isEqualTo(WHOLE.onRecord());
        }

        assertThat(answer.unplaced().statuses()).containsExactly(new MemberSummaryDto.UnknownStatus("Inactive", 1));
        assertThat(answer.unplaced().noWorkplace()).isEqualTo(2);
        assertThat(answer.unplaced().noTableCode()).isEqualTo(1);
        assertThat(answer.membersFrom()).isEqualTo("2019-01-01");
        assertThat(answer.leaversFrom()).isEqualTo("2026-06-01");
    }

    // ------------------------------------------------------------------------------------------ the month taken (04.2)

    /**
     * A workplace's frozen row of September, as the fixture's arithmetic gives it: its area, table code and complement
     * as they stood, and on record, suspended, opening, joined, left, reinstated and the earliest Date Joined.
     */
    public record ExpectedFact(String area, Integer tableCode, Integer complement, long onRecord, long suspended,
                               long opening, long joined, long left, long reinstated, String membersFrom) {
    }

    /** September's rows, by R/A number -- 0 is the row of the members with no workplace. */
    public static final Map<Integer, ExpectedFact> SEPTEMBER_FACTS = Map.of(
            900001, new ExpectedFact("A", 901, 20, 9, 1, 7, 1, 0, 1, "2019-01-01"),
            900002, new ExpectedFact("A", 902, 10, 3, 0, 3, 0, 0, 0, "2021-01-01"),
            900003, new ExpectedFact("B", 901, 5, 2, 0, 3, 1, 2, 0, "2020-01-01"),
            900004, new ExpectedFact("B", null, 3, 1, 0, 1, 0, 0, 0, "2020-01-01"),
            0, new ExpectedFact(null, null, null, 2, 0, 2, 0, 0, 0, "2022-01-01"));

    /**
     * September taken, held to the fixture's arithmetic on either database (MV-49): every row a workplace with its
     * lineage as it stood, every count at its own grain -- and each figure that appears twice equal in both (MV-16).
     * The fixture's members pay as {@code Payroll}, which is not a way of paying the member record stores today, so
     * every one of them is OTHER, as stored (BR41), and none has a subscription on record.
     */
    public static void assertTakenSeptember(List<MonthEndFactsRepository.Fact> facts,
                                            List<MonthEndFactsRepository.Count> counts) {
        Map<Long, Integer> raByKey = new LinkedHashMap<>();
        for (MonthEndFactsRepository.Fact fact : facts) {
            raByKey.put(fact.workplaceKey(), fact.raNumber() == null ? 0 : fact.raNumber());
        }
        assertThat(raByKey.values()).as("one row a workplace, and one for the members with none")
                .containsExactlyInAnyOrderElementsOf(SEPTEMBER_FACTS.keySet());
        for (MonthEndFactsRepository.Fact fact : facts) {
            int ra = raByKey.get(fact.workplaceKey());
            ExpectedFact want = SEPTEMBER_FACTS.get(ra);
            String what = "workplace " + ra;
            assertThat(fact.areaLetter()).as("%s: area as it stood", what).isEqualTo(want.area());
            assertThat(fact.tableCode()).as("%s: table code as it stood", what).isEqualTo(want.tableCode());
            assertThat(fact.complement()).as("%s: complement", what).isEqualTo(want.complement());
            assertThat(fact.onRecord()).as("%s: on record", what).isEqualTo(want.onRecord());
            assertThat(fact.suspended()).as("%s: suspended", what).isEqualTo(want.suspended());
            assertThat(fact.paying()).as("%s: paying", what).isEqualTo(want.onRecord() - want.suspended());
            assertThat(fact.opening()).as("%s: opening", what).isEqualTo(want.opening());
            assertThat(fact.joined()).as("%s: joined", what).isEqualTo(want.joined());
            assertThat(fact.left()).as("%s: left", what).isEqualTo(want.left());
            assertThat(fact.reinstated()).as("%s: reinstated", what).isEqualTo(want.reinstated());
            assertThat(fact.onRecord()).as("%s: the movement closes (rule S)", what)
                    .isEqualTo(fact.opening() + fact.joined() - fact.left() + fact.reinstated());
            assertThat(fact.membersFrom()).as("%s: the earliest Date Joined", what).hasToString(want.membersFrom());
            assertThat(fact.byDebitOrder() + fact.byDeduction()).as("%s: none pays a way stored today", what).isZero();
            assertThat(fact.byOther()).as("%s: 'Payroll' is other, as stored (BR41)", what).isEqualTo(fact.paying());
            assertThat(fact.debitOrderRand().add(fact.deductionRand()).add(fact.otherRand()))
                    .as("%s: no subscription on record", what).isZero();
            assertThat(fact.representatives() + fact.committeeMembers() + fact.seeded()).as(what).isZero();
        }
        MonthEndFactsRepository.Fact main = facts.stream().filter(f -> Integer.valueOf(900001).equals(f.raNumber()))
                .findFirst().orElseThrow();
        assertThat(main).extracting(MonthEndFactsRepository.Fact::tableCodeName, MonthEndFactsRepository.Fact::employerName,
                        MonthEndFactsRepository.Fact::groupName, MonthEndFactsRepository.Fact::groupCore,
                        MonthEndFactsRepository.Fact::regionCode, MonthEndFactsRepository.Fact::committeeCode,
                        MonthEndFactsRepository.Fact::committeeName)
                .as("the main street's lineage as it stood")
                .containsExactly("INVENTED BANK", "INVENTED BANK", "INVENTED GROUP ONE", true, "NORTH", "Q01",
                        "INVENTED NORTH COMMITTEE");
        assertThat(facts.stream().mapToLong(MonthEndFactsRepository.Fact::onRecord).sum())
                .as("MV-16: the rows sum to the summary's whole").isEqualTo(WHOLE.onRecord());

        List<String> found = counts.stream().map(c -> c.measure() + " " + raByKey.get(c.workplaceKey()) + " " + c.code()
                + " " + c.name() + " " + c.detail() + " = " + c.members()).toList();
        assertThat(found).as("the counts at their own grain").containsExactlyInAnyOrder(
                "LEFT_BY_CODE 900003 2 Resigned Society null = 1",
                "LEFT_BY_CODE 900003 5 Subscription in Arrears null = 1",
                "SUSPENDED_BY_CODE 900001 null null null = 1",
                "RECRUITED_BY 900001 null null null = 1",
                "RECRUITED_BY 900003 null null null = 1",
                "UNKNOWN_STATUS 0 null Inactive null = 1");
        for (MonthEndFactsRepository.Fact fact : facts) {
            assertThat(sum(counts, fact.workplaceKey(), MonthEndCount.Measure.LEFT_BY_CODE))
                    .as("MV-16: the leavers by code are the row's left").isEqualTo(fact.left());
            assertThat(sum(counts, fact.workplaceKey(), MonthEndCount.Measure.SUSPENDED_BY_CODE))
                    .as("MV-16: the suspended by code are the row's suspended").isEqualTo(fact.suspended());
            assertThat(sum(counts, fact.workplaceKey(), MonthEndCount.Measure.RECRUITED_BY))
                    .as("MV-16: the recruits are the row's joined").isEqualTo(fact.joined());
        }
    }

    private static long sum(List<MonthEndFactsRepository.Count> counts, long workplaceKey,
                            MonthEndCount.Measure measure) {
        return counts.stream().filter(c -> c.workplaceKey() == workplaceKey && c.measure() == measure)
                .mapToLong(MonthEndFactsRepository.Count::members).sum();
    }

    private static void assertFigures(String what, MemberSummaryDto.Figures got, Expected want) {
        assertThat(got.onRecord()).as("%s: on record", what).isEqualTo(want.onRecord());
        assertThat(got.suspended()).as("%s: suspended", what).isEqualTo(want.suspended());
        assertThat(got.paying()).as("%s: paying = on record - suspended (MD24)", what)
                .isEqualTo(want.onRecord() - want.suspended());
        assertThat(got.joined()).as("%s: joined", what).isEqualTo(want.joined());
        assertThat(got.left()).as("%s: left", what).isEqualTo(want.left());
        assertThat(got.reinstated()).as("%s: reinstated", what).isEqualTo(want.reinstated());
        assertThat(got.opening()).as("%s: opening", what).isEqualTo(want.opening());
        assertThat(got.unexplained()).as("%s: the movement closes (rule S)", what).isZero();
        assertThat(got.complement()).as("%s: complement", what).isEqualTo(want.complement());
    }
}

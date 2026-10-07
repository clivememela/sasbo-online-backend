package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.config.WorkplaceSwitch;
import za.co.titandynamix.sasbo_online_backend.controller.MonthEndStatisticsController;
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndStatisticDto;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.SasboReferenceRepository;
import za.co.titandynamix.sasbo_online_backend.support.StatisticsExpectations;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static za.co.titandynamix.sasbo_online_backend.support.StatisticsExpectations.rows;
import static za.co.titandynamix.sasbo_online_backend.support.StatisticsExpectations.sheet;
import static za.co.titandynamix.sasbo_online_backend.support.StatisticsExpectations.total;

/**
 * The statistics of a month, on H2 (month end 04.3 to 04.8): {@link SummaryFixture}'s September, each statistic held
 * to {@link StatisticsExpectations} -- the table the integration lane holds PostgreSQL to (MV-49) -- open and taken
 * alike; the year to date the sum of the months taken; a total equal to the figures' own (MV-16); the refusals; and
 * the switch below {@code all}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndStatisticsTest.AtTheFixturesClock.class)
@DisplayName("The statistics of a month, on H2")
class MonthEndStatisticsTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        Clock theFixturesClock() {
            return Clock.fixed(SummaryFixture.NOW, AcbTime.ZONE);
        }
    }

    /** Nothing of the seed's: only the fixture is on this database. */
    @MockitoBean
    private DataSeeder noSeed;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndStatistics statistics;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MemberSummaryService summaries;

    @Autowired
    private MonthEndFactsRepository facts;

    @Autowired
    private SasboReferenceRepository codes;

    @Autowired
    private SasboEnvironment environment;

    @Autowired
    private SeedMemberNumbers seedNumbers;

    @Autowired
    private SummaryGate gate;

    @BeforeEach
    void thePopulationAndNothingTaken() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
        jdbc.update("DELETE FROM month_end_counts");
        jdbc.update("DELETE FROM month_end_workplaces");
        jdbc.update("DELETE FROM month_end_months");
    }

    @Test
    @DisplayName("🔒 MV-49: every statistic of the open September, as the fixture's arithmetic gives it")
    void theOpenMonth() {
        MonthEndStatisticDto newAndDeleted = statistics.newAndDeleted("2026-09", null, null);
        assertThat(newAndDeleted.state()).isEqualTo("OPEN");
        assertThat(newAndDeleted.takenAt()).isNull();
        StatisticsExpectations.assertNewAndDeleted(newAndDeleted);
        StatisticsExpectations.assertMembership(statistics.membership("2026-09", null, null));
        StatisticsExpectations.assertLeavers(statistics.leavers("2026-09", null, null));
        StatisticsExpectations.assertBranches(statistics.branches("2026-09", null, null));
        StatisticsExpectations.assertRecruitment(statistics.recruitment("2026-09", null, null), 2);
        StatisticsExpectations.assertRepresentatives(statistics.representatives("2026-09", null, null));
    }

    @Test
    @DisplayName("🔒 MD19: September taken answers what the open September answered -- read from what was frozen")
    void theTakenMonthIsTheOpenMonth() {
        MonthEndStatisticDto open = statistics.membership("2026-09", null, null);
        takes.take(SummaryFixture.MONTH, "seed");

        MonthEndStatisticDto taken = statistics.membership("2026-09", null, null);
        assertThat(taken.state()).isEqualTo("TAKEN");
        assertThat(taken.takenBy()).isEqualTo("seed");
        assertThat(taken.sheets()).as("the same sheets, figure for figure").isEqualTo(open.sheets());
        StatisticsExpectations.assertNewAndDeleted(statistics.newAndDeleted("2026-09", null, null));
        StatisticsExpectations.assertLeavers(statistics.leavers("2026-09", null, null));
        StatisticsExpectations.assertBranches(statistics.branches("2026-09", null, null));
        StatisticsExpectations.assertRecruitment(statistics.recruitment("2026-09", null, null), 2);
        StatisticsExpectations.assertRepresentatives(statistics.representatives("2026-09", null, null));
        assertThat(statistics.branches("2026-09", null, null)).as("asked again: byte for byte")
                .isEqualTo(statistics.branches("2026-09", null, null));
    }

    @Test
    @DisplayName("04.4, 04.7: a suspension by SASBO's code and a recruit by their recruiter are rows of their own, not only the none-on-record row")
    void aCodeAndARecruiterAreRowsOfTheirOwn() {
        // The fixture's suspended member has no change on record and its recruits no recruiter, so the grouping by code
        // and by recruiter was only ever seen through its "none on record" row. Here m7 was suspended in September with
        // code 11, and m8, who joined on the 10th, was recruited by an invented recruiter -- undone after.
        boolean codeAdded = jdbc.queryForObject("SELECT count(*) FROM suspension_codes WHERE code = 11", Long.class) == 0;
        if (codeAdded) {
            jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (11, 'INVENTED NOT ON PAYROLL')");
        }
        Integer m7 = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = 'M-800007'", Integer.class);
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at, "
                + "suspension_code_id) SELECT ?, 'Active', 'Suspended', 'SUSPENSION_CODE', 'system', "
                + "TIMESTAMP WITH TIME ZONE '2026-09-05 08:00:00+00:00', id FROM suspension_codes WHERE code = 11", m7);
        jdbc.update("INSERT INTO recruiters (name) VALUES ('INVENTED RECRUITER')");
        Integer recruiter = jdbc.queryForObject("SELECT id FROM recruiters WHERE name = 'INVENTED RECRUITER'",
                Integer.class);
        jdbc.update("UPDATE members SET recruiter_id = ? WHERE membership_number = 'M-800008'", recruiter);
        try {
            assertThat(rows(sheet(statistics.membership("2026-09", null, null), "suspended-by-code")))
                    .as("m7 by the code of their last suspension, 11").containsExactly("11: 1");
            assertThat(rows(sheet(statistics.recruitment("2026-09", null, null), "by-recruiter")))
                    .as("m8 by their recruiter, the month and the year; the other recruit still has none")
                    .anyMatch(row -> row.startsWith(recruiter + ": ") && row.endsWith(" 1 1"))
                    .anyMatch(row -> row.startsWith("~NO_RECRUITER: "));
        } finally {
            jdbc.update("UPDATE members SET recruiter_id = NULL WHERE membership_number = 'M-800008'");
            jdbc.update("DELETE FROM recruiters WHERE id = ?", recruiter);
            jdbc.update("DELETE FROM member_status_changes WHERE member_id = ? AND reason = 'SUSPENSION_CODE'", m7);
            if (codeAdded) {
                jdbc.update("DELETE FROM suspension_codes WHERE code = 11");
            }
        }
    }

    @Test
    @DisplayName("🔒 MO23: A.C.B.'s two reasons read in SASBO's codes -- a termination for SUBS_IN_ARREARS is a leaver under code 5, a suspension for UNPAID_SUSPENDED is suspended under code 13")
    void acbsTwoReasonsReadInSasbosCodes() {
        // A.C.B. writes its changes with a reason and no code of SASBO's. Here m14 is terminated by it in September, and
        // m7 -- the fixture's one suspended member, with no change on record -- suspended by it; undone after. The
        // fixture's own code-5 leaver, m17, was given the code by hand: the two are one column (MO23).
        boolean codeAdded = jdbc.queryForObject("SELECT count(*) FROM suspension_codes WHERE code = 13", Long.class) == 0;
        if (codeAdded) {
            jdbc.update("INSERT INTO suspension_codes (code, name) VALUES (13, 'INVENTED DEBIT RETURNED')");
        }
        Integer m14 = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = 'M-800014'", Integer.class);
        Integer m7 = jdbc.queryForObject("SELECT id FROM members WHERE membership_number = 'M-800007'", Integer.class);
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at) "
                + "VALUES (?, 'Active', 'Terminated', 'SUBS_IN_ARREARS', 'acb', "
                + "TIMESTAMP WITH TIME ZONE '2026-09-13 08:00:00+00:00')", m14);
        jdbc.update("UPDATE members SET status = 'Terminated' WHERE id = ?", m14);
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at) "
                + "VALUES (?, 'Active', 'Suspended', 'UNPAID_SUSPENDED', 'acb', "
                + "TIMESTAMP WITH TIME ZONE '2026-09-05 08:00:00+00:00')", m7);
        try {
            MonthEndStatisticDto.Sheet byArea = sheet(statistics.leavers("2026-09", null, null), "by-area");
            assertThat(rows(byArea)).as("area B: m15 under code 2, and m17 and m14 under code 5 -- none without a code")
                    .containsExactly("A: 0 0 0 0 0", "B: 0 1 2 0 3");
            assertThat(rows(sheet(statistics.membership("2026-09", null, null), "suspended-by-code")))
                    .as("m7 under the code of A.C.B.'s suspension, 13").containsExactly("13: 1");
        } finally {
            jdbc.update("DELETE FROM member_status_changes WHERE member_id IN (?, ?) AND changed_by = 'acb'", m14, m7);
            jdbc.update("UPDATE members SET status = 'Active' WHERE id = ?", m14);
            if (codeAdded) {
                jdbc.update("DELETE FROM suspension_codes WHERE code = 13");
            }
        }
    }

    @Test
    @DisplayName("🔒 04.8, MM80: a member marked as Memnet is counted by table code -- and a member who is not, is not")
    void aMemnetMemberIsCounted() {
        // m13, on record at W3 under table code 901, marked from the nomination form; m15 at the same workplace, who
        // resigned in September, marked too -- a member no longer on record is not a Memnet member of the month's.
        jdbc.update("UPDATE members SET is_memnet = TRUE WHERE membership_number IN ('M-800013', 'M-800015')");
        try {
            MonthEndStatisticDto representatives = statistics.representatives("2026-09", null, null);
            assertThat(total(sheet(representatives, "memnet-members")))
                    .as("one Memnet member, at one workplace of four").isEqualTo("1 4 1 3 0.2500");
            assertThat(total(sheet(representatives, "committee-members"))).as("the BEC mark is another's")
                    .isEqualTo("0 4 0 4 0.0000");
        } finally {
            jdbc.update("UPDATE members SET is_memnet = NULL WHERE membership_number IN ('M-800013', 'M-800015')");
        }
    }

    @Test
    @DisplayName("🔒 04.9: the branches due a visit -- the longest unvisited first, each with its last visit; rule C over its members and what it could still yield; the total whole")
    void theBranchesDueAVisit() {
        // W1 visited on 5 September, captured here; W2 visited in June, a month SASBO's branch list prints; W3 and W4
        // never. The three smaller rows are fewer than five; three hidden rows give back only their sum, never one of
        // them, so W1's eight stand (MD37: a row beside is withheld where ONE hidden row could be worked out).
        Long w1 = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900001", Long.class);
        Long w2 = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900002", Long.class);
        Long w3 = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900003", Long.class);
        Long w4 = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = 900004", Long.class);
        jdbc.update("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, complement_found, seeded, "
                + "captured_at) VALUES (?, DATE '2026-09-05', TRUE, 20, FALSE, "
                + "TIMESTAMP WITH TIME ZONE '2026-09-05 10:00:00+00:00')", w1);
        jdbc.update("INSERT INTO workplace_visits (workplace_id, visited_on, day_known, seeded, captured_at) VALUES "
                + "(?, DATE '2026-06-01', FALSE, TRUE, TIMESTAMP WITH TIME ZONE '2026-09-01 08:00:00+00:00')", w2);
        try {
            MonthEndStatisticDto visits = statistics.visits("2026-09", null, null);
            MonthEndStatisticDto.Sheet sheet = sheet(visits, "due-a-visit");

            assertThat(visits.statistic()).isEqualTo("visits");
            assertThat(sheet.rows()).extracting(MonthEndStatisticDto.Row::key)
                    .as("never visited first, then the oldest visit; the members with no workplace last")
                    .containsExactly("W" + w3, "W" + w4, "W" + w2, "W" + w1, "~NO_WORKPLACE");
            assertThat(sheet.rows()).extracting(MonthEndStatisticDto.Row::label).startsWith(
                    "R/A 900003 INVENTED HARBOUR · never visited", "R/A 900004 INVENTED OUTPOST · never visited",
                    "R/A 900002 INVENTED HEAD OFFICE · last visited Jun 2026",
                    "R/A 900001 INVENTED MAIN STREET · last visited Sep 2026");
            assertThat(rows(sheet)).as("rule C: what it could still yield goes with the members it would give back")
                    .containsExactly("W" + w3 + ": fewer than five", "W" + w4 + ": fewer than five",
                            "W" + w2 + ": fewer than five", "W" + w1 + ": 8 20 12", "~NO_WORKPLACE: 2 0 0");
            assertThat(total(sheet)).as("the members, the complement, and the sum of what each branch could yield")
                    .isEqualTo("16 38 24");
        } finally {
            jdbc.update("DELETE FROM workplace_visits");
        }
    }

    @Test
    @DisplayName("🔒 MR-ROB-4, DL-65: a member captured after September is taken, dated inside it, moves none of its statistics")
    void aLateCaptureMovesNothing() {
        takes.take(SummaryFixture.MONTH, "seed");
        MonthEndStatisticDto before = statistics.membership("2026-09", null, null);
        za.co.titandynamix.sasbo_online_backend.entity.member.Member late =
                za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture.withSurname("InventedLate");
        late.setMembershipNumber("M-899998");
        late.setStatus("Active");
        late.setJoinDate(java.time.LocalDate.of(2026, 9, 14));
        Integer id = members.save(late).getId();
        try {
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = 900001) "
                    + "WHERE id = ?", id);

            assertThat(statistics.membership("2026-09", null, null).sheets())
                    .as("read from what was frozen, never counted again").isEqualTo(before.sheets());
        } finally {
            jdbc.update("DELETE FROM members WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("🔒 04.11, MD70: September taken again is read from its newest take -- a late member counted by every statistic and the year to date, the first take kept")
    void aMonthTakenAgain() {
        MonthEndFactsRepository.TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        MonthEndStatisticDto before = statistics.membership("2026-09", null, null);
        String joinedBefore = total(sheet(statistics.newAndDeleted("2026-09", null, null), "year-joined"));
        za.co.titandynamix.sasbo_online_backend.entity.member.Member late =
                za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture.withSurname("InventedLate");
        late.setMembershipNumber("M-899997");
        late.setStatus("Active");
        late.setJoinDate(java.time.LocalDate.of(2026, 9, 14));
        Integer id = members.save(late).getId();
        try {
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = 900001) "
                    + "WHERE id = ?", id);
            takes.take(SummaryFixture.MONTH, "corrected", first.id());

            MonthEndStatisticDto again = statistics.membership("2026-09", null, null);
            assertThat(again.takenBy()).as("the newest take's").isEqualTo("corrected");
            assertThat(again.sheets()).as("the late member counted").isNotEqualTo(before.sheets());
            assertThat(total(sheet(statistics.newAndDeleted("2026-09", null, null), "year-joined")))
                    .as("the year to date sums the newest take of each month").isNotEqualTo(joinedBefore);
            assertThat(facts.facts(first.id()).stream().mapToLong(MonthEndFactsRepository.Fact::onRecord).sum())
                    .as("the first take kept as it was").isEqualTo(
                            facts.facts(takes.taken(SummaryFixture.MONTH).orElseThrow().id()).stream()
                                    .mapToLong(MonthEndFactsRepository.Fact::onRecord).sum() - 1);
        } finally {
            jdbc.update("DELETE FROM members WHERE id = ?", id);
        }
    }

    @Test
    @DisplayName("🔒 rule Y: the year to date is the sum of the months taken -- June, August and September's leavers")
    void theYearToDate() {
        takes.take(YearMonth.of(2026, 6), "seed");
        takes.take(YearMonth.of(2026, 8), "seed");
        takes.take(SummaryFixture.MONTH, "seed");

        MonthEndStatisticDto answer = statistics.newAndDeleted("2026-09", null, null);
        assertThat(rows(sheet(answer, "year-left"))).as("m9 left W1 in June; m16 W3 in August; two more in September")
                .containsExactly("A: 1 0 1", "B: 3 0 3", "~NO_WORKPLACE: - - 0");
        assertThat(total(sheet(answer, "year-left"))).as("the summary's left in 2026").isEqualTo("4 0 4");
        assertThat(total(sheet(answer, "year-joined"))).isEqualTo("2 0 2");
        assertThat(answer.notes()).anyMatch(n -> n.contains("June 2026, August 2026, September 2026"));
        StatisticsExpectations.assertOneFigure(statistics.all("2026-09"));
    }

    @Test
    @DisplayName("🔒 P4: a figure printed in two statistics is one figure wherever it stands -- the month open, and taken")
    void aFigureInTwoStatisticsIsOneFigure() {
        StatisticsExpectations.assertOneFigure(statistics.all("2026-09"));
        takes.take(SummaryFixture.MONTH, "seed");
        StatisticsExpectations.assertOneFigure(statistics.all("2026-09"));
    }

    @Test
    @DisplayName("🔒 MV-16: each statistic's total is the figures' own for the month")
    void theTotalsAreTheSummarys() {
        MemberSummaryDto summary = summaries.summary("2026-09", "group", null, null);

        assertThat(total(sheet(statistics.membership("2026-09", null, null), "by-table-code"))).startsWith(
                summary.totals().onRecord() + " " + summary.totals().paying() + " " + summary.totals().suspended() + " ");
        assertThat(total(sheet(statistics.newAndDeleted("2026-09", null, null), "month-joined")))
                .endsWith(" " + summary.totals().joined());
        assertThat(total(sheet(statistics.newAndDeleted("2026-09", null, null), "month-left")))
                .endsWith(" " + summary.totals().left());
        assertThat(total(sheet(statistics.branches("2026-09", null, null), "by-area")))
                .startsWith(summary.totals().complement() + " " + summary.totals().paying() + " "
                        + summary.totals().penetration().toPlainString());
    }

    @Test
    @DisplayName("every output takes an area or a region: area B is its own rows, and its total")
    void anArea() {
        MonthEndStatisticDto b = statistics.newAndDeleted("2026-09", "b", null);

        assertThat(b.area()).isEqualTo("B");
        assertThat(rows(sheet(b, "month-left"))).containsExactly("B: 2 0 2");
        assertThat(total(sheet(b, "month-left"))).isEqualTo("2 0 2");
        assertThat(rows(sheet(statistics.branches("2026-09", null, "SOUTH"), "by-area")))
                .containsExactly("B: 8 3 0.3750 0.00 5 2");
        // 04.8 "by area and table code", as reps_no and comm_mem print them: an area's table codes, and its total.
        MonthEndStatisticDto representativesOfB = statistics.representatives("2026-09", "B", null);
        for (String key : List.of("representatives", "committee-members", "memnet-members")) {
            assertThat(sheet(representativesOfB, key).rows()).as("%s: area B's table codes alone", key)
                    .extracting(MonthEndStatisticDto.Row::key).containsExactly("901", "~NO_TABLE_CODE");
            assertThat(total(sheet(representativesOfB, key))).as("%s: area B's two workplaces, none with one", key)
                    .isEqualTo("0 2 0 2 0.0000");
        }
    }

    @Test
    @DisplayName("the refusals: a past month not taken, an area not on record, an area and a region together")
    void theRefusals() {
        assertThatThrownBy(() -> statistics.membership("2026-07", null, null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_TAKEN));
        assertThatThrownBy(() -> statistics.membership("2026-10", null, null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_BEGUN));
        assertThatThrownBy(() -> statistics.membership("2026-09", "Q", null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        r -> assertThat(r.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_AREA_UNKNOWN));
        assertThatThrownBy(() -> statistics.membership("2026-09", "A", "NORTH"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, r -> {
                    assertThat(r.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                    assertThat(r.details()).containsEntry("field", "region");
                });
    }

    @Test
    @DisplayName("04.5: the resigned by the reason they gave -- a reason kept under code 2 is its own column, and still code 2 in every other sheet")
    void theResignedByTheirReason() {
        jdbc.update("INSERT INTO deletion_codes (sasbo_code, name, parent_id) SELECT NULL, 'Financial', id "
                + "FROM deletion_codes WHERE sasbo_code = 2");
        String m15 = "(SELECT id FROM members WHERE membership_number = 'M-800015')";
        jdbc.update("UPDATE member_status_changes SET deletion_code_id = (SELECT id FROM deletion_codes WHERE name = "
                + "'Financial') WHERE member_id = " + m15);
        try {
            MemberStatisticSheets leavers = new MemberStatisticSheets(statistics.leavers("2026-09", null, null));

            assertThat(leavers.sheet("resigned").columns()).extracting(MonthEndStatisticDto.Column::label)
                    .containsExactly("Financial", "No reason given", "Total");
            assertThat(rows(leavers.sheet("resigned"))).containsExactly("E1: 1 0 1");
            assertThat(rows(leavers.sheet("by-area"))).as("counted under SASBO's code 2, as ever")
                    .containsExactly("A: 0 0 0 0 0", "B: 0 1 1 0 2");
        } finally {
            jdbc.update("UPDATE member_status_changes SET deletion_code_id = (SELECT id FROM deletion_codes WHERE "
                    + "sasbo_code = 2) WHERE member_id = " + m15);
            jdbc.update("DELETE FROM deletion_codes WHERE name = 'Financial' AND parent_id IS NOT NULL");
        }
    }

    /** One statistic's sheets, by key. */
    private record MemberStatisticSheets(MonthEndStatisticDto answer) {
        MonthEndStatisticDto.Sheet sheet(String key) {
            return StatisticsExpectations.sheet(answer, key);
        }
    }

    @Test
    @DisplayName("🔒 MD62, MV-55: below 'all' a sheet cut by where members work is the totals and one row, with no staff number")
    void belowAll() {
        MonthEndStatistics atNames = new MonthEndStatistics(takes, facts, codes, new WorkplaceSwitch("names"),
                environment, seedNumbers, gate);

        MonthEndStatisticDto branches = atNames.branches("2026-09", null, null);
        assertThat(branches.workplacesServed()).isEqualTo("NAMES");
        assertThat(rows(sheet(branches, "by-area"))).containsExactly("~ALL: - 16 - 0.00 - -");
        assertThat(rows(sheet(atNames.membership("2026-09", null, null), "by-table-code")))
                .containsExactly("~ALL: 17 16 1 0.00 0 0.00 0 0.00 16 0.00 -");
        assertThat(rows(sheet(atNames.newAndDeleted("2026-09", null, null), "month-joined")))
                .containsExactly("~ALL: - - 2");
        assertThat(rows(sheet(atNames.newAndDeleted("2026-09", "A", null), "month-joined")))
                .as("an area asked below 'all' holds nobody").isEmpty();
        assertThat(rows(sheet(atNames.membership("2026-09", null, null), "suspended-by-code")))
                .as("a code is no place: the same at every switch").containsExactly("~NO_CODE: 1");
        List<String> notes = branches.notes();
        assertThat(notes).anyMatch(n -> n.contains("not served in full"));
    }

    /** The sheets cut by a code, not by where members work: the same at every switch. */
    private static final List<String> CUT_BY_A_CODE = List.of("membership suspended-by-code",
            "recruitment by-recruiter", "recruitment by-channel");

    @Test
    @DisplayName("🔒 MD62, MV-55: below 'all' EVERY sheet cut by where members work is one row -- the whole, equal to its total and to the whole at 'all'")
    void belowAllEverySheet() {
        MonthEndStatistics atNames = new MonthEndStatistics(takes, facts, codes, new WorkplaceSwitch("names"),
                environment, seedNumbers, gate);
        // The pack's statistics and every one outside it. 04.9's is not in the pack, so this loop never asked it below
        // 'all' -- and on the link, its switch unset, it answered 500 (1 Oct): every member has no workplace there, and
        // the empty map of last visits refused the null key.
        assertThatCode(() -> atNames.visits("2026-09", null, null))
                .as("04.9 below 'all' answers: a member with no workplace has no last visit to look up")
                .doesNotThrowAnyException();
        List<MonthEndStatisticDto> below = new java.util.ArrayList<>(atNames.all("2026-09"));
        below.add(atNames.visits("2026-09", null, null));
        List<MonthEndStatisticDto> atAll = new java.util.ArrayList<>(statistics.all("2026-09"));
        atAll.add(statistics.visits("2026-09", null, null));
        assertThat(below).extracting(MonthEndStatisticDto::statistic)
                .containsExactlyElementsOf(atAll.stream().map(MonthEndStatisticDto::statistic).toList());
        assertThat(below).extracting(MonthEndStatisticDto::statistic)
                .as("every statistic the controller serves is asked here -- a new one outside the pack is added above")
                .containsExactlyInAnyOrderElementsOf(servedStatistics());

        List<String> cut = new java.util.ArrayList<>();
        for (int i = 0; i < below.size(); i++) {
            for (MonthEndStatisticDto.Sheet sheet : below.get(i).sheets()) {
                String name = below.get(i).statistic() + " " + sheet.key();
                if (CUT_BY_A_CODE.contains(name)) {
                    continue;
                }
                cut.add(name);
                assertThat(sheet.rows()).as("%s: one row, the whole", name)
                        .extracting(MonthEndStatisticDto.Row::key).containsExactly("~ALL");
                assertThat(sheet.rows().getFirst().cells()).as("%s: the one row is the total", name)
                        .isEqualTo(sheet.total().cells());
                MonthEndStatisticDto.Sheet whole = sheet(atAll.get(i), sheet.key());
                for (int c = 0; c < sheet.columns().size(); c++) {
                    java.math.BigDecimal cell = sheet.total().cells().get(c);
                    if (cell != null) {
                        assertThat(cell).as("%s %s: the whole is the same at every switch", name,
                                sheet.columns().get(c).key()).isEqualByComparingTo(whole.total().cells().get(c));
                    }
                }
            }
        }
        assertThat(cut).as("every sheet cut by where members work -- a new sheet is placed here, or among the cut by a code")
                .containsExactlyInAnyOrder("new-and-deleted month-joined", "new-and-deleted month-left",
                        "new-and-deleted year-joined", "new-and-deleted year-left", "membership by-group",
                        "membership by-table-code", "leavers by-area", "leavers by-employer", "leavers by-table-code",
                        "leavers joined-deleted", "leavers resigned", "branches by-area", "branches by-employer",
                        "recruitment by-table-code", "representatives representatives",
                        "representatives committee-members", "representatives memnet-members", "visits due-a-visit");
    }

    /** The statistics {@link MonthEndStatisticsController} serves, by their routes: each a GET of a name, no variable. */
    private static List<String> servedStatistics() {
        return java.util.Arrays.stream(MonthEndStatisticsController.class.getDeclaredMethods())
                .map(m -> m.getAnnotation(org.springframework.web.bind.annotation.GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> java.util.Arrays.stream(mapping.value()))
                .filter(route -> !route.contains("{"))
                .map(route -> route.substring(1))
                .toList();
    }
}

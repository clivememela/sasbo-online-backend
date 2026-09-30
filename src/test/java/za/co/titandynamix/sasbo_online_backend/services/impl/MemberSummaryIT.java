package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.MemberStatusKind;
import za.co.titandynamix.sasbo_online_backend.dto.summary.MemberSummaryDto;
import za.co.titandynamix.sasbo_online_backend.enums.SummaryGrouping;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MemberSummaryRepository;
import za.co.titandynamix.sasbo_online_backend.services.MemberSummaryService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.MemberStatuses;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The figures of September 2026, on PostgreSQL (02.2): {@link SummaryFixture}'s one population and one expected
 * answer -- the same H2 is held to, figure for figure (MV-49, BR65); the partial index's predicate held to the
 * vocabulary's leaving statuses (MD66); and a figure query that outlives its timeout answered
 * {@code SUMMARY_UNAVAILABLE}, proved with a statement made to wait on a lock and never with a sleep (MV-25).
 */
@DisplayName("The figures of a month, on PostgreSQL")
class MemberSummaryIT extends AbstractSwitchedOnIT {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MemberSummaryService summaries;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private MemberSummaryRepository repository;

    static final List<String> GROUPINGS = SummaryFixture.GROUPINGS;

    @BeforeEach
    void thePopulation() {
        SummaryFixture.insert(jdbc, members);
    }

    @ParameterizedTest(name = "by {0}")
    @FieldSource("GROUPINGS")
    @DisplayName("🔒 MV-49: every figure of every row, for each grouping -- the answer H2 gives, from the same fixture")
    void everyGrouping(String by) {
        SummaryFixture.assertAnswer(summaries.summary("2026-09", by, null, null), by);
    }

    @Autowired
    private za.co.titandynamix.sasbo_online_backend.services.MonthEndTakes takes;

    @Test
    @DisplayName("🔒 rule F, MV-49: a TAKEN month is read from its facts on PostgreSQL -- every grouping, as H2 answers it")
    void aTakenMonth() {
        takes.take(java.time.YearMonth.of(2026, 6), "seed");
        takes.take(java.time.YearMonth.of(2026, 8), "seed");
        takes.take(SummaryFixture.MONTH, "seed");

        for (String by : GROUPINGS) {
            MemberSummaryDto answer = summaries.summary("2026-09", by, null, null);
            SummaryFixture.assertAnswer(answer, by);
            assertThat(answer.takenBy()).isEqualTo("seed");
        }
        jdbc.update("UPDATE workplaces SET area_id = (SELECT id FROM sasbo_areas WHERE letter = 'B') "
                + "WHERE ra_number = 900001");
        SummaryFixture.assertAnswer(summaries.summary("2026-09", "area", null, null), "area");
    }

    @Test
    @DisplayName("MemberSummaryRepository's one statement on PostgreSQL: a line a row, the complement, the status nobody knows, one check")
    void theStatementsOwnLines() {
        List<MemberSummaryRepository.Line> lines = repository.figures(new MemberSummaryRepository.Ask(
                MonthRange.of(SummaryFixture.MONTH), SummaryGrouping.AREA, true, true, null, null, List.of("M-800001")));

        assertThat(lines).filteredOn(l -> l.part().equals(MemberSummaryRepository.CHECK)).hasSize(1)
                .allSatisfy(check -> assertThat(check.known()).isEqualTo(1));
        assertThat(lines).filteredOn(l -> l.part().equals(MemberSummaryRepository.UNKNOWN_STATUS))
                .extracting(MemberSummaryRepository.Line::status).containsExactly("Inactive");
        assertThat(lines).filteredOn(l -> l.part().equals(MemberSummaryRepository.COMPLEMENT))
                .extracting(MemberSummaryRepository.Line::key, MemberSummaryRepository.Line::complement)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("A", 30L),
                        org.assertj.core.groups.Tuple.tuple("B", 8L));
        assertThat(lines).filteredOn(l -> l.part().equals(MemberSummaryRepository.ROW))
                .extracting(MemberSummaryRepository.Line::seeded).as("the one number asked for, on record").contains(1L);
    }

    @Test
    @DisplayName("🔒 MD62 on PostgreSQL: below the switch's all the statement is accepted, and its one row is the whole -- live and taken")
    void belowAllTheStatementIsOneRowOnPostgres() {
        // Below "all" every member is the one row of those with no workplace (MD62), and the taken half groups its facts
        // by that one key. PostgreSQL refuses a bare literal in GROUP BY -- "non-integer constant in GROUP BY" -- where
        // H2 takes it, and every other test here runs with the switch at all: found by MV-26's probe. Blank is none
        // (MD50), so this is the statement every hosted stack whose switch is not set asks.
        MemberSummaryRepository.Ask belowAll = new MemberSummaryRepository.Ask(MonthRange.of(SummaryFixture.MONTH),
                SummaryGrouping.GROUP, false, false, null, null, List.of());

        assertThat(repository.figures(belowAll)).filteredOn(l -> l.part().equals(MemberSummaryRepository.ROW))
                .extracting(MemberSummaryRepository.Line::key, MemberSummaryRepository.Line::onRecord)
                .as("worked out now").containsExactly(org.assertj.core.groups.Tuple.tuple(
                        MemberSummaryRepository.NO_WORKPLACE, SummaryFixture.WHOLE.onRecord()));

        takes.take(java.time.YearMonth.of(2026, 6), "seed");
        takes.take(java.time.YearMonth.of(2026, 8), "seed");
        takes.take(SummaryFixture.MONTH, "seed");
        assertThat(repository.figures(belowAll)).filteredOn(l -> l.part().equals(MemberSummaryRepository.ROW))
                .extracting(MemberSummaryRepository.Line::key, MemberSummaryRepository.Line::onRecord)
                .as("read from the month's facts (rule F)").containsExactly(org.assertj.core.groups.Tuple.tuple(
                        MemberSummaryRepository.NO_WORKPLACE, SummaryFixture.WHOLE.onRecord()));
    }

    @Test
    @DisplayName("🔒 MV-26, MD19: members no figure counts still answer the rows they stand in and the first Date Joined -- live and taken")
    void membersNoFigureCountsStillAnswerTheirRowsAndTheFirstDateJoined() {
        // The statement reads only the members a figure can count (MV-26); a leaver who left before the year began and
        // joined before it counts in none. Two of them: one at a closed workplace, in a committee nobody else is in; one
        // with no workplace -- and the others with none are placed, so theirs is the only row of it. Their rows are
        // noughts and still rows, as the month's facts have them once taken (MD19), and the first Date Joined is theirs.
        jdbc.update("INSERT INTO committees (code, province_id, name, recruiting_number) SELECT 'Z99', id, "
                + "'INVENTED CLOSED COMMITTEE', NULL FROM committee_provinces WHERE letter = 'Q'");
        jdbc.update("INSERT INTO workplaces (ra_number, name, employer_printed, table_code_id, table_code_by, area_id, "
                + "committee_code, committee_id, staff_complement, cl_code, closed_on) VALUES (900099, "
                + "'INVENTED CLOSED BRANCH', 'INVENTED EMPLOYER', NULL, NULL, (SELECT id FROM sasbo_areas WHERE letter = 'A'), "
                + "'Z99', (SELECT id FROM committees WHERE code = 'Z99'), 0, '00000000', DATE '2015-01-31')");
        Integer atAClosedWorkplace = leaverLongAgo("M-899001", "Resigned", "1990-01-01", 900099, 2, "2010-06-01T08:00:00Z");
        Integer withNoWorkplace = leaverLongAgo("M-899002", "Retired", "1995-01-01", null, 1, "2012-01-01T08:00:00Z");
        jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = 900001) "
                + "WHERE workplace_id IS NULL AND id NOT IN (?, ?)", atAClosedWorkplace, withNoWorkplace);

        for (String how : List.of("worked out now", "taken")) {
            if (how.equals("taken")) {
                takes.take(java.time.YearMonth.of(2026, 6), "seed");
                takes.take(java.time.YearMonth.of(2026, 8), "seed");
                takes.take(SummaryFixture.MONTH, "seed");
            }
            MemberSummaryDto answer = summaries.summary("2026-09", "committee", null, null);
            for (String key : List.of("Z99", MemberSummaryRepository.NO_WORKPLACE)) {
                assertThat(answer.rows()).filteredOn(r -> r.key().equals(key)).as("%s: the row %s", how, key)
                        .singleElement().satisfies(r -> assertThat(List.of(r.figures().onRecord(), r.figures().opening(),
                                r.figures().joined(), r.figures().left(), r.figures().leftYearToDate()))
                                .as("%s: %s is noughts", how, key).containsOnly(0L));
            }
            assertThat(answer.membersFrom()).as("%s: the first member joined in 1990, and left in 2010", how)
                    .isEqualTo("1990-01-01");
            assertThat(answer.totals().onRecord()).as("%s: nobody counted twice or lost", how)
                    .isEqualTo(SummaryFixture.WHOLE.onRecord());
        }
    }

    /** A member who left before the year began and joined before it -- invented, as the fixture's are. */
    private Integer leaverLongAgo(String number, String status, String joined, Integer ra, int deletionCode,
                                  String leftAt) {
        Integer id = jdbc.queryForObject("INSERT INTO members (membership_number, surname, status, join_date, workplace_id,"
                        + " created_at, updated_at) VALUES (?, 'INVENTED LEAVER', ?, CAST(? AS DATE),"
                        + " (SELECT id FROM workplaces WHERE ra_number = ?), TIMESTAMP '2026-01-01 00:00:00',"
                        + " TIMESTAMP '2026-01-01 00:00:00') RETURNING id", Integer.class, number, status, joined, ra);
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at, "
                        + "deletion_code_id) VALUES (?, 'Active', ?, 'DELETION_CODE', 'system', ?, "
                        + "(SELECT id FROM deletion_codes WHERE sasbo_code = ?))", id, status,
                java.time.OffsetDateTime.parse(leftAt), deletionCode);
        return id;
    }

    @Test
    @DisplayName("🔒 MD66: the partial index's predicate is the vocabulary's leaving statuses, and members_join_date is there")
    void theIndexIsHeldToTheVocabulary() {
        String definition = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'members_on_record'",
                String.class);
        List<String> excluded = new ArrayList<>();
        Matcher quoted = Pattern.compile("'([^']+)'").matcher(definition.substring(definition.indexOf("WHERE")));
        while (quoted.find()) {
            excluded.add(quoted.group(1));
        }
        List<String> leaving = MemberStatuses.KINDS.entrySet().stream()
                .filter(e -> e.getValue() == MemberStatusKind.LEAVING).map(e -> e.getKey()).toList();

        assertThat(excluded).as("the statuses the index leaves out").containsExactlyInAnyOrderElementsOf(leaving);
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename = 'members'", String.class))
                .contains("members_on_record", "members_join_date");
    }

    @Test
    @DisplayName("🔒 MD47, MV-25: a figure query that waits on a lock past its timeout is SUMMARY_UNAVAILABLE -- never a hang")
    void aQueryThatOutlivesItsTimeout() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        TransactionTemplate holding = new TransactionTemplate(transactions);
        Thread holder = new Thread(() -> holding.executeWithoutResult(status -> {
            jdbc.execute("LOCK TABLE members IN ACCESS EXCLUSIVE MODE");
            locked.countDown();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }), "month-end-holds-the-members");
        holder.start();
        assertThat(locked.await(10, TimeUnit.SECONDS)).as("the lock was taken").isTrue();
        try {
            assertThatThrownBy(() -> summaries.summary("2026-09", "group", null, null))
                    .isInstanceOfSatisfying(MonthEndRefusal.class,
                            refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.SUMMARY_UNAVAILABLE));
        } finally {
            release.countDown();
            holder.join(30_000);
        }
        SummaryFixture.assertAnswer(summaries.summary("2026-09", "group", null, null), "group");
    }
}

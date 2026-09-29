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

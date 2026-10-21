package za.co.titandynamix.sasbo_online_backend.services.impl;

import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.AcbJobLauncher;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRateRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbRunRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.utils.BankSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.DebitOrderMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SubscriptionSeeder;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The seed carries a returned month (R3-15, D33): the two months before the clock's -- one CLOSED,
 * one RETURNED with its unpaids, decisions and letters -- seeded once, never in production, and the
 * current and next months left free for a live Prepare.
 *
 * <p>{@code AbstractPostgresIT} neutralises {@code DataSeeder} (F49), so the seeders are called here
 * directly, in the order {@code DataSeeder} calls them, against one real PostgreSQL. The clock is
 * AbstractAcbIT's frozen 18 September 2026 -- this month is 2026-09 -- and every other clock this
 * class uses is pinned inside the range the rate and the calendar cover (SD4-D): no seeder test here
 * reads the wall clock.
 */
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("AcbHistorySeeder -- the seed's returned month")
class AcbHistorySeederIT extends AbstractAcbIT {

    @Autowired
    private AcbHistorySeeder seeder;

    @Autowired
    private BankSeeder bankSeeder;

    @Autowired
    private SubscriptionSeeder subscriptionSeeder;

    @Autowired
    private DebitOrderMemberSeeder memberSeeder;

    @Autowired
    private AcbRunService service;

    @Autowired
    private AcbReturnsService returns;

    @Autowired
    private AcbRunRepository runs;

    @Autowired
    private AcbRateRepository rates;

    @Autowired
    private AcbPreparationJob job;

    @Autowired
    private AcbAuditTrail audit;

    @Autowired
    private HolidayCalendarSource calendars;

    @Autowired
    private PlatformTransactionManager transactions;

    @BeforeEach
    void theSeedsPopulationIsThere() throws Exception {
        bankSeeder.seed();
        subscriptionSeeder.seed();
        memberSeeder.seed();
    }

    @Test
    @DisplayName("July closed and August returned; September and October free")
    void theTwoMonthsBeforeTheClocksAreOnRecord() {
        assertThat(seeder.seed()).isEqualTo(AcbHistorySeeder.Outcome.SEEDED);
        assertThat(statusByMonth()).containsExactly(
                Map.entry("2026-07", AcbRunStatus.CLOSED.name()),
                Map.entry("2026-08", AcbRunStatus.RETURNED.name()));
        assertThat(count("SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL AND kind = 'SEED_COMPLETE'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM acb_runs WHERE created_by <> 'seed'")).isZero();
    }

    @Test
    @DisplayName("August's unpaids: present again for four, suspend for three -- an unknown code among them, never re-debited -- one undecided, and it cannot close")
    void augustHasItsUnpaids() {
        seeder.seed();
        long august = runId("2026-08");
        List<AcbRunLineDto> unpaids = returns.unpaids(august, null, 0, 50).content();
        assertThat(unpaids).hasSize(8);
        Map<String, Long> decisions = unpaids.stream().collect(Collectors.groupingBy(
                l -> l.decision() == null ? "UNDECIDED" : l.decision().name(), Collectors.counting()));
        assertThat(decisions).containsEntry("RE_PRESENT", 4L).containsEntry("SUSPEND", 3L).containsEntry("UNDECIDED", 1L);
        assertThat(unpaids).extracting(AcbRunLineDto::unpaidCode).contains("2", "4", AcbHistorySeeder.UNKNOWN_CODE);
        assertThat(unpaids).filteredOn(l -> AcbHistorySeeder.UNKNOWN_CODE.equals(l.unpaidCode()))
                .extracting(AcbRunLineDto::unpaidReason).containsExactly("OTHER");

        AcbRunDto run = service.get(august);
        AcbRefusal refused = refusal(() -> service.close(august, run.version(), "someone", null));
        assertThat(refused.code()).as("the undecided unpaid keeps the month open").isEqualTo(AcbRefusalCode.UNPAIDS_UNDECIDED);
    }

    @Test
    @DisplayName("🔬 F62: seeded letters are SENT in their own past month -- one held NO_EMAIL, none a sender would pick up")
    void lettersAreAlreadySent() {
        seeder.seed();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'RENDERED'")).isZero();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'QUEUED' AND status_reason = 'NO_EMAIL'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'QUEUED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'SENT'")).isPositive();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'SENT' AND sent_at >= TIMESTAMPTZ '2026-09-01 00:00:00+02'"))
                .as("every seeded letter is dated in the past").isZero();
    }

    @Test
    @DisplayName("preparing September offers exactly August's unpaids decided present-again, as second lines")
    void theLivePrepareShowsTheReDebits() {
        seeder.seed();
        long august = runId("2026-08");
        List<Long> presentAgain = returns.unpaids(august, null, 0, 50).content().stream()
                .filter(l -> l.decision() != null && l.decision().name().equals("RE_PRESENT"))
                .map(AcbRunLineDto::id).sorted().toList();
        assertThat(presentAgain).hasSize(4);

        AcbRunDto september = service.prepare("2026-09", "system");
        List<Long> secondLines = service.lines(september.id(), 0, 200).content().stream()
                .map(AcbRunLineDto::rePresentsLineId).filter(java.util.Objects::nonNull).sorted().toList();
        assertThat(secondLines).isEqualTo(presentAgain);
    }

    @Test
    @DisplayName("a second start seeds nothing; a database somebody has used is never seeded over")
    void seededOnce() {
        assertThat(seeder.seed()).isEqualTo(AcbHistorySeeder.Outcome.SEEDED);
        assertThat(seeder.seed()).isEqualTo(AcbHistorySeeder.Outcome.ALREADY_SEEDED);
        assertThat(count("SELECT count(*) FROM acb_runs")).isEqualTo(2);
    }

    @Test
    @DisplayName("a database somebody has used: no history, and no error")
    void aUsedDatabaseIsLeftAlone() {
        insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        try (LogCapture log = LogCapture.on(AcbHistorySeeder.class)) {
            assertThat(seeder.seed()).isEqualTo(AcbHistorySeeder.Outcome.RUNS_EXIST);
            assertThat(log.renderedAt(Level.ERROR)).isEmpty();
        }
        assertThat(count("SELECT count(*) FROM acb_runs")).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 SD4-H: a seed that died between months is said once, at ERROR, and nothing is touched")
    void anUnfinishedSeedIsSaid() {
        insertRun(java.time.LocalDate.of(2026, 7, 1), "CLOSED", FROZEN, AcbHistorySeeder.ACTOR);
        try (LogCapture log = LogCapture.on(AcbHistorySeeder.class)) {
            assertThat(seeder.seed()).isEqualTo(AcbHistorySeeder.Outcome.RUNS_EXIST);
            assertThat(log.renderedAt(Level.ERROR)).contains("never finished").contains("rebuild the demo database");
        }
        assertThat(count("SELECT count(*) FROM acb_runs")).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 SD3-A: where the production marker is set, no run is ever seeded")
    void productionIsNeverSeeded() {
        assertThat(seederAt(Clock.fixed(FROZEN, AcbTime.ZONE), "production").seed())
                .isEqualTo(AcbHistorySeeder.Outcome.PRODUCTION);
        assertThat(count("SELECT count(*) FROM acb_runs")).isZero();
    }

    @Test
    @DisplayName("T2-E: at 00:30 SAST on 1 November -- UTC still says October -- the history is September and October, "
            + "and October's 31st, a Saturday, is debited on Friday the 30th")
    void theFirstOfAMonthJustAfterMidnight() {
        Clock justAfterMidnight = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), AcbTime.ZONE);
        assertThat(seederAt(justAfterMidnight, null).seed()).isEqualTo(AcbHistorySeeder.Outcome.SEEDED);
        assertThat(statusByMonth()).containsOnlyKeys("2026-09", "2026-10");
        assertThat(count("SELECT count(*) FROM acb_run_lines l JOIN acb_runs r ON r.id = l.run_id "
                + "WHERE r.action_month = DATE '2026-10-01' AND l.debit_day = 31 AND l.action_date = DATE '2026-10-30'"))
                .as("members on the 31st, moved back because forward would leave the month").isPositive();
        // 🔒 F62: nothing the seed writes is dated after the clock -- October 30th's returns, noted two
        // days on, would otherwise be noted on 1 November and their letters "sent" at 10:00 that day.
        assertThat(count("SELECT count(*) FROM acb_run_lines WHERE unpaid_noted_on >= DATE '2026-11-01'"))
                .as("no return noted after the clock's day").isZero();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE sent_at > TIMESTAMPTZ '2026-10-31 22:30:00+00'"))
                .as("no letter sent after the clock").isZero();
        assertThat(count("SELECT count(*) FROM acb_run_lines l JOIN acb_runs r ON r.id = l.run_id "
                + "WHERE r.action_month = DATE '2026-10-01' AND l.unpaid_noted_on = DATE '2026-10-31'"))
                .as("the twin: October's late returns are noted on its last day, not dropped").isPositive();
    }

    @Test
    @DisplayName("T2-E, SD4-D: at 1 January 2027 both months are in the previous year")
    void atNewYear() {
        Clock newYear = Clock.fixed(Instant.parse("2027-01-01T08:00:00Z"), AcbTime.ZONE);
        assertThat(seederAt(newYear, null).seed()).isEqualTo(AcbHistorySeeder.Outcome.SEEDED);
        assertThat(statusByMonth()).containsOnlyKeys("2026-11", "2026-12");
    }

    @Test
    @DisplayName("🔒 SD4-D: a clock whose history has no rate in force skips it with one WARN -- and nothing is written")
    void outsideTheKnownRangeIsSkipped() {
        // Before V70's provisional R166.65 of January 2025, nothing is in force (V41, V70).
        Clock tooEarly = Clock.fixed(Instant.parse("2025-02-15T08:00:00Z"), AcbTime.ZONE);
        try (LogCapture log = LogCapture.on(AcbHistorySeeder.class)) {
            assertThat(seederAt(tooEarly, null).seed()).isEqualTo(AcbHistorySeeder.Outcome.SKIPPED);
            assertThat(log.renderedAt(Level.WARN)).contains("skipped").contains("2024-12");
        }
        assertThat(count("SELECT count(*) FROM acb_runs")).isZero();
    }

    @Test
    @DisplayName("🔒 SD4-B: the seeder never holds the job launcher -- it runs the preparation inline, on its own thread")
    void theLauncherIsNeverUsed() {
        assertThat(Arrays.stream(AcbHistorySeeder.class.getDeclaredFields()).map(Field::getType).toList())
                .doesNotContain(AcbJobLauncher.class)
                .contains(AcbPreparationJob.class);
    }

    // ------------------------------------------------------------------------------------------

    private AcbHistorySeeder seederAt(Clock clock, String environment) {
        return new AcbHistorySeeder(runs, rates, job, service, returns, audit, calendars,
                new SasboEnvironment(environment), clock, jdbc, transactions);
    }

    private Map<String, String> statusByMonth() {
        Map<String, String> byMonth = new java.util.TreeMap<>();
        jdbc.query("SELECT action_month, status FROM acb_runs ORDER BY action_month", rs -> {
            byMonth.put(YearMonth.from(rs.getDate("action_month").toLocalDate()).toString(), rs.getString("status"));
        });
        return byMonth;
    }

    private long runId(String month) {
        return jdbc.queryForObject("SELECT id FROM acb_runs WHERE action_month = ?", Long.class,
                YearMonth.parse(month).atDay(1));
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        return fail("expected an AcbRefusal and the call returned normally");
    }
}

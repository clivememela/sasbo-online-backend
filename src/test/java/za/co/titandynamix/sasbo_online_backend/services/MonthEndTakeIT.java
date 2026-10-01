package za.co.titandynamix.sasbo_online_backend.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.entity.monthend.MonthEndCount;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.support.AbstractSwitchedOnIT;
import za.co.titandynamix.sasbo_online_backend.support.DatabaseReset;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.MonthEndTakeSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SeedMemberNumbers;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Taking a month, on PostgreSQL (month end 04.2): {@link SummaryFixture}'s September frozen the same as on H2 (MV-49);
 * the month's record and its facts append-only by trigger and taken once by a partial unique index, each asserted by
 * name, and the lane's reset still emptying them (MD46, MV-29, BR64); a member captured while the month is taken in
 * every sheet of it or in none (MV-27); two takes released together, one refused by the index (MV-28); a workplace
 * moved afterwards leaving the month as it stood (MV-30); and the seed's three months.
 */
@DisplayName("Taking a month, on PostgreSQL")
class MonthEndTakeIT extends AbstractSwitchedOnIT {

    @Autowired
    private MemberRepository members;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndFactsRepository facts;

    @Autowired
    private SasboEnvironment environment;

    @Autowired
    private SeedMemberNumbers seedNumbers;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private Clock clock;

    @Autowired
    private MonthEndTakeSeeder seeder;

    @BeforeEach
    void thePopulation() {
        SummaryFixture.insert(jdbc, members);
    }

    @Test
    @DisplayName("🔒 MV-49: September frozen on PostgreSQL -- the rows and counts H2 freezes, from the same fixture")
    void septemberFrozen() {
        TakenMonth taken = takes.take(SummaryFixture.MONTH, "seed");

        assertThat(taken.takenAt()).isEqualTo(SummaryFixture.NOW);
        SummaryFixture.assertTakenSeptember(facts.facts(taken.id()), facts.counts(taken.id()));
    }

    @Test
    @DisplayName("🔒 MD46, MV-29: the record, the facts and the counts are append-only by their own triggers -- by name")
    void appendOnlyByTrigger() {
        takes.take(SummaryFixture.MONTH, "seed");

        for (String table : List.of("month_end_months", "month_end_workplaces", "month_end_counts")) {
            assertThat(jdbc.queryForList("SELECT tgname FROM pg_trigger WHERE tgrelid = '" + table + "'::regclass",
                    String.class)).as(table).contains(table + "_append_only");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + table)).as("a DELETE of %s", table)
                    .hasMessageContaining("append-only");
        }
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_months SET taken_by = 'someone else'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_workplaces SET on_record = on_record + 1"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE month_end_counts SET members = members + 1"))
                .hasMessageContaining("append-only");
    }

    @Test
    @DisplayName("🔒 MD46: taken once by a PARTIAL unique index, by name; the facts' index is (month, workplace)")
    void theIndexesByName() {
        String once = jdbc.queryForObject("SELECT indexdef FROM pg_indexes WHERE indexname = 'month_end_months_taken_once'",
                String.class);
        assertThat(once).contains("UNIQUE").contains("(month_start)").contains("WHERE (replaces_id IS NULL)");
        String facts = jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'month_end_workplaces_one_a_workplace'", String.class);
        assertThat(facts).contains("UNIQUE").contains("(month_start, workplace_key)");
    }

    @Test
    @DisplayName("🔒 BR64, MV-48: the twin -- the lane's reset still empties the month taken, because a TRUNCATE is not a row")
    void theResetStillEmptiesThem() {
        takes.take(SummaryFixture.MONTH, "seed");

        DatabaseReset.reset(jdbc);

        for (String table : List.of("month_end_months", "month_end_workplaces", "month_end_counts")) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).as(table).isZero();
        }
    }

    @Test
    @DisplayName("🔒 MD64, MV-27: a member captured while the month is being taken is in every sheet of it or in none")
    void oneSnapshot() throws Exception {
        TakenMonth taken = takeWithACaptureBetweenItsStatements(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        long headOffice = workplaceKey(900002);
        assertThat(joinedAt(taken, headOffice)).as("the facts, read before the capture").isZero();
        assertThat(recruitedAt(taken, headOffice)).as("the recruits, read after it -- in the same snapshot").isZero();
    }

    @Test
    @DisplayName("canary: a take in two snapshots -- READ COMMITTED -- puts the member in one sheet and not the other")
    void twoSnapshotsDisagree() throws Exception {
        TakenMonth taken = takeWithACaptureBetweenItsStatements(TransactionDefinition.ISOLATION_READ_COMMITTED);

        long headOffice = workplaceKey(900002);
        assertThat(joinedAt(taken, headOffice)).isZero();
        assertThat(recruitedAt(taken, headOffice)).as("what MV-27 exists to catch").isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 MD46, MV-28: two takes released together -- one takes the month, the index refuses the other")
    void twoTakesReleasedTogether() throws Exception {
        CountDownLatch firstWrote = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MonthEndTakes first = new MonthEndTakes(facts, environment, seedNumbers, transactions, clock,
                TransactionDefinition.ISOLATION_REPEATABLE_READ, () -> {
                    firstWrote.countDown();
                    await(release);
                });
        ExecutorService two = Executors.newFixedThreadPool(2);
        try {
            Future<TakenMonth> a = two.submit(() -> first.take(SummaryFixture.MONTH, "first"));
            assertThat(firstWrote.await(30, TimeUnit.SECONDS)).as("the first take wrote its record").isTrue();
            Future<TakenMonth> b = two.submit(() -> takes.take(SummaryFixture.MONTH, "second"));
            // The second waits on the index for the first to finish -- or, with no index to stop it, finishes.
            waitUntil(() -> waitingOnTheIndex() || b.isDone(), Duration.ofSeconds(30));
            release.countDown();

            assertThat(a.get(60, TimeUnit.SECONDS).takenBy()).isEqualTo("first");
            assertThatThrownBy(() -> b.get(60, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
                    .cause().isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_ALREADY_TAKEN);
                        assertThat(refusal.details()).containsEntry("takenBy", "first");
                    });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_workplaces", Long.class))
                    .isEqualTo(SummaryFixture.SEPTEMBER_FACTS.size());
        } finally {
            release.countDown();
            two.shutdownNow();
        }
    }

    @Test
    @DisplayName("🔒 MV-30: a workplace moved to another area after the month is taken leaves the month as it stood")
    void theLineageAsItStood() {
        TakenMonth taken = takes.take(SummaryFixture.MONTH, "seed");
        jdbc.update("UPDATE workplaces SET area_id = (SELECT id FROM sasbo_areas WHERE letter = 'B') WHERE ra_number = 900001");

        MonthEndFactsRepository.Fact main = facts.facts(taken.id()).stream()
                .filter(f -> Integer.valueOf(900001).equals(f.raNumber())).findFirst().orElseThrow();
        assertThat(main.areaLetter()).isEqualTo("A");
        assertThat(main.regionCode()).isEqualTo("NORTH");
    }

    @Test
    @DisplayName("🔒 MD54: the seed takes the three months before the clock's, the oldest first; a second start takes none")
    void theSeedsThreeMonths() {
        assertThat(seeder.seed()).isEqualTo(MonthEndTakeSeeder.MONTHS);

        assertThat(takes.takenMonths()).extracting(TakenMonth::monthStart).containsExactly(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 6, 1));
        assertThat(takes.takenMonths()).extracting(TakenMonth::takenBy).containsOnly("seed");
        assertThat(seeder.seed()).as("a second start").isZero();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    /** Takes September with a member captured -- committed -- while the take is held between its statements. */
    private TakenMonth takeWithACaptureBetweenItsStatements(int isolation) throws Exception {
        CountDownLatch paused = new CountDownLatch(1);
        CountDownLatch captured = new CountDownLatch(1);
        MonthEndTakes held = new MonthEndTakes(facts, environment, seedNumbers, transactions, clock, isolation, () -> {
            paused.countDown();
            await(captured);
        });
        ExecutorService taker = Executors.newSingleThreadExecutor();
        try {
            Future<TakenMonth> take = taker.submit(() -> held.take(SummaryFixture.MONTH, "seed"));
            assertThat(paused.await(30, TimeUnit.SECONDS)).as("the take is held after its facts").isTrue();
            Member late = MemberFixture.withSurname("InventedDuringTheTake");
            late.setMembershipNumber("M-899998");
            late.setStatus("Active");
            late.setJoinDate(LocalDate.of(2026, 9, 14));
            Integer id = members.save(late).getId();
            jdbc.update("UPDATE members SET workplace_id = (SELECT id FROM workplaces WHERE ra_number = 900002) "
                    + "WHERE id = ?", id);
            captured.countDown();
            return take.get(60, TimeUnit.SECONDS);
        } finally {
            captured.countDown();
            taker.shutdownNow();
        }
    }

    private long workplaceKey(int ra) {
        Long id = jdbc.queryForObject("SELECT id FROM workplaces WHERE ra_number = ?", Long.class, ra);
        return id == null ? 0 : id;
    }

    private long joinedAt(TakenMonth taken, long workplaceKey) {
        return facts.facts(taken.id()).stream().filter(f -> f.workplaceKey() == workplaceKey)
                .mapToLong(MonthEndFactsRepository.Fact::joined).sum();
    }

    private long recruitedAt(TakenMonth taken, long workplaceKey) {
        return facts.counts(taken.id()).stream()
                .filter(c -> c.workplaceKey() == workplaceKey && c.measure() == MonthEndCount.Measure.RECRUITED_BY)
                .mapToLong(MonthEndFactsRepository.Count::members).sum();
    }

    /** Whether a take is waiting on another's uncommitted record -- a lock, as PostgreSQL reports it. */
    private boolean waitingOnTheIndex() {
        Long waiting = jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname = current_database() "
                + "AND wait_event_type = 'Lock' AND query LIKE 'INSERT INTO month_end_months%'", Long.class);
        return waiting != null && waiting > 0;
    }

    /** Waits for a condition, bounded -- never a sleep standing in for one. */
    private static void waitUntil(BooleanSupplier condition, Duration limit) {
        long deadline = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("the condition did not hold within " + limit);
            }
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the latch was never released");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}

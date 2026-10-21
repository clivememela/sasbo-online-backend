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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.titandynamix.sasbo_online_backend.acb.AcbTime;
import za.co.titandynamix.sasbo_online_backend.controller.MonthEndController;
import za.co.titandynamix.sasbo_online_backend.entity.member.Member;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.MemberFixture;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndFactsRepository.TakenMonth;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.MonthRange;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * Taking a month, on H2 (month end 04.2): {@link SummaryFixture}'s September frozen as facts -- one row a workplace
 * with its lineage as it stood, the counts at their own grain -- held to the same arithmetic the integration lane holds
 * PostgreSQL to (MV-49, BR65); taken once; refused where it has not begun or nothing could be frozen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-takes;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndTakesTest.AtTheFixturesClock.class)
@DisplayName("Taking a month, on H2")
class MonthEndTakesTest {

    @TestConfiguration
    static class AtTheFixturesClock {
        @Bean
        @Primary
        MovableClock theFixturesClock() {
            return new MovableClock();
        }
    }

    /**
     * The fixture's instant -- moved by a test of the cut-off (MEN-14), which takes a month on one day and reads the
     * next on another, and put back before every test.
     */
    static final class MovableClock extends Clock {
        private volatile Instant now = SummaryFixture.NOW;

        void at(Instant instant) {
            this.now = instant;
        }

        @Override
        public ZoneId getZone() {
            return AcbTime.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(this.now, zone);
        }

        @Override
        public Instant instant() {
            return this.now;
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
    private MonthEndTakes takes;

    @Autowired
    private MonthEndFactsRepository facts;

    @Autowired
    private PlatformTransactionManager transactions;

    @Autowired
    private MovableClock clock;

    @BeforeEach
    void thePopulationAndNothingTaken() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
        // H2 has no trigger: the months taken by the test before are emptied here, row by row.
        jdbc.update("DELETE FROM month_end_counts");
        jdbc.update("DELETE FROM month_end_workplaces");
        jdbc.update("DELETE FROM month_end_months");
        jdbc.update("DELETE FROM members WHERE membership_number IN ('M-899999', 'M-899998', 'M-899997')");
        clock.at(SummaryFixture.NOW);
    }

    /**
     * 🔒 THE TAKE AND ITS LINE IN THE RECORD OF WHO DID WHAT ARE ONE TRANSACTION (the sign-in review, 7 Oct). The route
     * took the month, committed, and only then wrote the line: a line that failed left a month taken by nobody on record.
     * Through the route itself, with a record that cannot be written: nothing is taken.
     */
    @Test
    @DisplayName("🔒 the take and the record of who took it are one: a line that cannot be written takes nothing")
    void aTakeIsNeverWithoutItsLine() {
        AuditTrail unwritable = mock(AuditTrail.class);
        doThrow(new IllegalStateException("the record of who did what cannot be written")).when(unwritable)
                .record(anyString(), anyString(), anyString(), anyString());
        MonthEndController route = new MonthEndController(takes, unwritable);

        assertThatThrownBy(() -> route.take(SummaryFixture.MONTH.toString(), null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(facts.taken(SummaryFixture.MONTH.atDay(1))).as("a month taken by nobody on record").isEmpty();
    }

    @Test
    @DisplayName("🔒 MD43, MV-49: September frozen -- a row a workplace with its lineage as it stood, the counts at their own grain")
    void septemberFrozen() {
        TakenMonth taken = takes.take(SummaryFixture.MONTH, "seed");

        assertThat(taken.monthStart()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(taken.takenBy()).isEqualTo("seed");
        assertThat(taken.takenAt()).as("the injected clock's instant (BR40)").isEqualTo(SummaryFixture.NOW);
        assertThat(taken.changesFrom()).as("the earliest change on record then").hasToString("2026-06-01T08:00:00Z");
        SummaryFixture.assertTakenSeptember(facts.facts(taken.id()), facts.counts(taken.id()));
    }

    @Test
    @DisplayName("🔒 MR-ROB-4: a member captured afterwards with a Date Joined inside the month does not move what was taken")
    void aLateCaptureMovesNothing() {
        TakenMonth taken = takes.take(SummaryFixture.MONTH, "seed");
        Member late = MemberFixture.withSurname("InventedLate");
        late.setMembershipNumber("M-899999");
        late.setStatus("Active");
        late.setJoinDate(LocalDate.of(2026, 9, 14));
        members.save(late);

        SummaryFixture.assertTakenSeptember(facts.facts(taken.id()), facts.counts(taken.id()));
    }

    @Test
    @DisplayName("🔒 MD64: the take the application wires is one snapshot -- REPEATABLE READ, never the database's default")
    void theWiredTakeIsOneSnapshot() {
        // MV-27's own test hands its isolation in; this is the one the route and the seed are given.
        assertThat(takes.isolation()).isEqualTo(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Test
    @DisplayName("MV-28: a month is taken once -- the second take is MONTH_ALREADY_TAKEN, naming who took it and when")
    void takenOnce() {
        takes.take(SummaryFixture.MONTH, "seed");

        assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "someone else"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_ALREADY_TAKEN);
                    assertThat(refusal.details()).containsEntry("month", "2026-09").containsEntry("takenBy", "seed")
                            .containsEntry("takenAt", SummaryFixture.NOW.toString());
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_workplaces", Long.class))
                .as("the refused take wrote nothing of its own").isEqualTo(SummaryFixture.SEPTEMBER_FACTS.size());
    }

    @Test
    @DisplayName("🔒 04.11, MD70: a month taken again is a new take beside the first -- read in its place, the late member counted, the first kept as it was")
    void aMonthTakenAgain() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        Member late = MemberFixture.withSurname("InventedLate");
        late.setMembershipNumber("M-899999");
        late.setStatus("Active");
        late.setJoinDate(LocalDate.of(2026, 9, 14));
        members.save(late);

        TakenMonth again = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThat(again.id()).isNotEqualTo(first.id());
        assertThat(again.takeNumber()).isEqualTo(2);
        assertThat(again.takenBy()).isEqualTo("corrected");
        assertThat(again.firstTakenBy()).as("the first take's who and when, beside the newest").isEqualTo("seed");
        assertThat(again.firstTakenAt()).isEqualTo(first.takenAt());
        assertThat(takes.taken(SummaryFixture.MONTH)).as("the month is read from its newest take").get()
                .extracting(TakenMonth::id).isEqualTo(again.id());
        assertThat(takes.takenMonths()).extracting(TakenMonth::id).containsExactly(again.id());
        SummaryFixture.assertTakenSeptember(facts.facts(first.id()), facts.counts(first.id()));
        assertThat(facts.facts(again.id()).stream().mapToLong(MonthEndFactsRepository.Fact::joined).sum())
                .as("the late member, joined on 14 September, counted by the take that corrects the first")
                .isEqualTo(facts.facts(first.id()).stream().mapToLong(MonthEndFactsRepository.Fact::joined).sum() + 1);
    }

    @Test
    @DisplayName("🔒 04.11: a take again names the month's newest take -- an older one is MONTH_TAKEN_SINCE naming the newest, a month never taken is MONTH_NOT_TAKEN; neither writes")
    void aTakeAgainNamesTheNewest() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        TakenMonth second = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "late", first.id(), "Invented: corrected the workplaces"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_TAKEN_SINCE);
                    assertThat(refusal.details()).containsEntry("month", "2026-09").containsEntry("takenBy", "corrected")
                            .containsEntry("takeId", String.valueOf(second.id()));
                });
        assertThatThrownBy(() -> takes.take(YearMonth.of(2026, 7), "late", first.id(), "Invented: corrected the workplaces"))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_TAKEN));
        // Another month's take is never the one a take again replaces -- the look names it so before anything is
        // written; on PostgreSQL a foreign key would refuse it too, and H2 has none.
        TakenMonth august = takes.take(YearMonth.of(2026, 8), "seed");
        assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "late", august.id(), "Invented: corrected the workplaces"))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_TAKEN_SINCE));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isEqualTo(3);
        assertThat(takes.taken(SummaryFixture.MONTH)).get().extracting(TakenMonth::id).isEqualTo(second.id());
    }

    @Test
    @DisplayName("🔒 04.11: two takes again of one take -- the second written loses to the database, never to a look first (H2: one take number a month)")
    void aTakeIsReplacedOnce() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        facts.insertMonth(LocalDate.of(2026, 9, 1), "one", SummaryFixture.NOW, first, "Invented: one", MonthRange.of(SummaryFixture.MONTH));

        assertThatThrownBy(() -> facts.insertMonth(LocalDate.of(2026, 9, 1), "two", SummaryFixture.NOW, first, "Invented: two", MonthRange.of(SummaryFixture.MONTH)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a month that has not begun is MONTH_NOT_BEGUN, and nothing is written")
    void notBegun() {
        assertThatThrownBy(() -> takes.take(YearMonth.of(2026, 10), "seed"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_BEGUN);
                    assertThat(refusal.details()).containsEntry("month", "2026-10").containsEntry("current", "2026-09");
                });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isZero();
    }

    @Test
    @DisplayName("🔒 MD54, MV-50: with no workplace on record a month is MONTH_NOT_READY, naming what is missing -- nothing frozen")
    void notReadyWithoutWorkplaces() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            status.setRollbackOnly();
            jdbc.update("UPDATE members SET workplace_id = NULL");
            jdbc.update("DELETE FROM workplaces");

            assertThatThrownBy(() -> takes.take(YearMonth.of(2026, 8), "seed"))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_READY);
                        assertThat(refusal.details()).containsEntry("missing", "workplaces");
                    });
            assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isZero();
        });
        assertThat(jdbc.queryForObject("SELECT count(*) FROM workplaces", Long.class)).as("rolled back").isEqualTo(4);
    }

    @Test
    @DisplayName("an earlier month is frozen as it stood then -- August: W3's leaver of 20 August, and September's joiners not yet members")
    void augustAsItStood() {
        TakenMonth taken = takes.take(YearMonth.of(2026, 8), "seed");

        java.util.Map<Integer, MonthEndFactsRepository.Fact> byRa = new java.util.HashMap<>();
        facts.facts(taken.id()).forEach(f -> byRa.put(f.raNumber() == null ? 0 : f.raNumber(), f));
        MonthEndFactsRepository.Fact harbour = byRa.get(900003);
        assertThat(harbour.left()).as("m16 retired on 20 August").isEqualTo(1);
        assertThat(harbour.onRecord()).as("m13, m14 and m15 -- m15 left in September, m17 had not joined")
                .isEqualTo(3);
        MonthEndFactsRepository.Fact main = byRa.get(900001);
        assertThat(main.onRecord()).as("m1-m7 -- m8 joined in September, m9 had resigned in June").isEqualTo(7);
        assertThat(main.joined() + main.reinstated()).isZero();
        assertThat(facts.counts(taken.id())).filteredOn(c -> c.workplaceKey() == harbour.workplaceKey())
                .extracting(MonthEndFactsRepository.Count::code).containsExactly(1);
    }

    @Test
    @DisplayName("the months taken are read back the newest first, and a month not taken is not found")
    void readBack() {
        takes.take(YearMonth.of(2026, 7), "seed");
        takes.take(YearMonth.of(2026, 8), "seed");

        assertThat(takes.takenMonths()).extracting(TakenMonth::monthStart)
                .containsExactly(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 7, 1));
        assertThat(takes.taken(YearMonth.of(2026, 6))).isEmpty();
        assertThat(takes.openMonth()).isEqualTo(SummaryFixture.MONTH);
    }

    @Test
    @DisplayName("a month a request names: unreadable is REQUEST_MALFORMED naming the field, never giving it back")
    void aMonthThatCannotBeRead() {
        for (String unreadable : new String[] {"2026-13", "last-month", "2026-9", "<script>"}) {
            assertThatThrownBy(() -> takes.month(unreadable))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.REQUEST_MALFORMED);
                        assertThat(refusal.details()).containsOnlyKeys("field").containsEntry("field", "month");
                    });
        }
        assertThat(takes.month(" 2026-08 ")).isEqualTo(YearMonth.of(2026, 8));
    }

    // ------------------------------------------------------------------------------------------------ MEN-10, MEN-11

    @Test
    @DisplayName("🔒 MEN-11, 'This will help to understand why the file was replaced!': a take again without a reason is TAKE_REASON_REQUIRED, and nothing is written")
    void aTakeAgainSaysWhy() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");

        for (String none : new String[] {null, "", "   "}) {
            assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "corrected", first.id(), none))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.TAKE_REASON_REQUIRED);
                        assertThat(refusal.details()).containsOnly(Map.entry("month", "2026-09"));
                    });
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("🔒 MEN-11: a reason past 500 characters, or with a control character, is TAKE_REASON_INVALID -- the field and the limit, never the words")
    void aReasonIsOrdinaryText() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        String tooLong = "Invented correction ".repeat(26);

        for (String refused : new String[] {tooLong, "Invented" + (char) 7 + "correction"}) {
            assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "corrected", first.id(), refused))
                    .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                        assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.TAKE_REASON_INVALID);
                        assertThat(refusal.details()).containsOnly(Map.entry("field", "reason"),
                                Map.entry("limit", "500"));
                        assertThat(refusal.getMessage()).doesNotContain("Invented");
                    });
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM month_end_months", Long.class)).isEqualTo(1);
        assertThat(takes.take(SummaryFixture.MONTH, "corrected", first.id(), "x".repeat(500)).reason())
                .as("must-pass: five hundred characters exactly is a reason").hasSize(500);
    }

    @Test
    @DisplayName("🔒 MEN-11: the reason is said before anything else is asked of the take -- before MONTH_TAKEN_SINCE and MONTH_NOT_BEGUN")
    void theReasonIsSaidFirst() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThatThrownBy(() -> takes.take(SummaryFixture.MONTH, "late", first.id(), null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.TAKE_REASON_REQUIRED));
        assertThatThrownBy(() -> takes.take(YearMonth.of(2026, 10), "late", first.id(), null))
                .isInstanceOfSatisfying(MonthEndRefusal.class,
                        refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.TAKE_REASON_REQUIRED));
    }

    @Test
    @DisplayName("MEN-10, MEN-11: the reason stands beside the take -- a first take's too, where one is given -- and the month answers it")
    void theReasonIsKept() {
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed", null, "  Invented: the first take  ");
        TakenMonth again = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThat(first.reason()).as("trimmed").isEqualTo("Invented: the first take");
        assertThat(takes.take(YearMonth.of(2026, 8), "seed").reason()).as("a first take need not say why").isNull();
        assertThat(takes.record("2026-09")).satisfies(month -> {
            assertThat(month.takeNumber()).isEqualTo(2);
            assertThat(month.reason()).isEqualTo("Invented: corrected the workplaces");
            assertThat(month.takenBy()).isEqualTo("corrected");
            assertThat(month.firstTakenBy()).isEqualTo("seed");
            assertThat(month.takeId()).isEqualTo(again.id());
        });
    }

    // ------------------------------------------------------------------------------------------------ MEN-14

    /** September taken at 07:10 SAST on its last working day, Wednesday 30 September -- the deck's world. */
    private static final Instant SEPTEMBER_TAKEN = Instant.parse("2026-09-30T05:10:00Z");
    private static final Instant SEPTEMBER_STARTS = Instant.parse("2026-08-31T22:00:00Z");
    private static final Instant OCTOBER_STARTS = Instant.parse("2026-09-30T22:00:00Z");
    private static final Instant OCTOBER_ENDS = Instant.parse("2026-10-31T22:00:00Z");
    /** The deck's clock: Wednesday 14 October 2026 at 09:30 SAST. */
    private static final Instant DECK_CLOCK = Instant.parse("2026-10-14T07:30:00Z");
    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

    @Test
    @DisplayName("🔒 MEN-14, 'After month end is done - all capturing and changes move to the next month': September counts up to its take, October from it")
    void theCutOff() {
        clock.at(SEPTEMBER_TAKEN);
        TakenMonth september = takes.take(SummaryFixture.MONTH, "seed");

        assertThat(september.countsFrom()).as("August was never taken: from the 1st at 00:00").isEqualTo(SEPTEMBER_STARTS);
        assertThat(september.countsUntil()).as("up to its own take").isEqualTo(SEPTEMBER_TAKEN);
        assertThat(september.cutOff()).isEqualTo(SEPTEMBER_TAKEN);
        clock.at(DECK_CLOCK);
        MonthRange october = takes.counted(OCTOBER);
        assertThat(october.start()).as("October counts from September's take").isEqualTo(SEPTEMBER_TAKEN);
        assertThat(october.next()).as("the open month, to its calendar end").isEqualTo(OCTOBER_ENDS);
        assertThat(october.firstDay()).as("the dates stay the calendar's").isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(takes.counted(SummaryFixture.MONTH).next()).isEqualTo(SEPTEMBER_TAKEN);
        assertThat(takes.openSince()).isEqualTo(SEPTEMBER_TAKEN);
        assertThat(takes.record("2026-10").countsFrom()).isEqualTo(SEPTEMBER_TAKEN.toString());
        assertThat(takes.record("2026-10").countsUntil()).isNull();
        assertThat(takes.record("2026-09").countsFrom()).isEqualTo(SEPTEMBER_STARTS.toString());
        assertThat(takes.record("2026-09").countsUntil()).isEqualTo(SEPTEMBER_TAKEN.toString());
    }

    @Test
    @DisplayName("🔒 MEN-14: a member captured after September was taken counts in October, Date Joined in September or not -- one captured before, in September")
    void aCaptureAfterTheTakeCountsInTheNextMonth() {
        long before = joined(facts.liveFacts(MonthRange.counted(SummaryFixture.MONTH, SEPTEMBER_STARTS, SEPTEMBER_TAKEN,
                MonthRange.of(SummaryFixture.MONTH).yearStart()), List.of()));
        captured("M-899997", LocalDate.of(2026, 9, 29), SEPTEMBER_TAKEN.minusSeconds(3_600));
        clock.at(SEPTEMBER_TAKEN);
        TakenMonth september = takes.take(SummaryFixture.MONTH, "seed");
        captured("M-899998", LocalDate.of(2026, 9, 30), SEPTEMBER_TAKEN.plusSeconds(3_600));
        clock.at(DECK_CLOCK);

        assertThat(joined(facts.facts(september.id()))).as("captured at 06:10 on the 30th: in September")
                .isEqualTo(before + 1);
        assertThat(joined(facts.liveFacts(takes.counted(OCTOBER), List.of())))
                .as("captured at 08:10 on the 30th, after the take: in October, and nobody else is").isEqualTo(1);
        assertThat(joined(facts.liveFacts(MonthRange.of(OCTOBER), List.of())))
                .as("canary: the calendar's October would not count them -- the window does").isZero();
    }

    @Test
    @DisplayName("🔒 MEN-10, MEN-14: a take again on the same day counts up to its own instant -- the member captured to correct the first is in it")
    void aTakeAgainTheSameDay() {
        clock.at(SEPTEMBER_TAKEN);
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        captured("M-899998", LocalDate.of(2026, 9, 30), SEPTEMBER_TAKEN.plusSeconds(1_800));
        clock.at(SEPTEMBER_TAKEN.plusSeconds(3_600));

        TakenMonth again = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: captured the one forgotten");

        assertThat(again.countsFrom()).isEqualTo(SEPTEMBER_STARTS);
        assertThat(again.countsUntil()).isEqualTo(SEPTEMBER_TAKEN.plusSeconds(3_600));
        assertThat(joined(facts.facts(again.id()))).isEqualTo(joined(facts.facts(first.id())) + 1);
        assertThat(takes.counted(OCTOBER).start()).as("October counts from the newest take")
                .isEqualTo(SEPTEMBER_TAKEN.plusSeconds(3_600));
    }

    @Test
    @DisplayName("🔒 MEN-14: a take again after the month never counts past its calendar end")
    void aTakeAgainLaterStopsAtTheMonthsEnd() {
        clock.at(SEPTEMBER_TAKEN);
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        clock.at(DECK_CLOCK);

        TakenMonth again = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThat(again.countsUntil()).as("October's changes are October's").isEqualTo(OCTOBER_STARTS);
        assertThat(takes.counted(OCTOBER).start()).isEqualTo(OCTOBER_STARTS);
    }

    @Test
    @DisplayName("🔒 MEN-14: a take again never counts into a month taken after it -- the windows follow one another")
    void aTakeAgainNeverReachesIntoATakenMonth() {
        clock.at(SEPTEMBER_TAKEN);
        TakenMonth first = takes.take(SummaryFixture.MONTH, "seed");
        clock.at(Instant.parse("2026-10-30T05:10:00Z"));
        TakenMonth october = takes.take(OCTOBER, "seed");
        clock.at(Instant.parse("2026-11-05T07:00:00Z"));

        TakenMonth again = takes.take(SummaryFixture.MONTH, "corrected", first.id(), "Invented: corrected the workplaces");

        assertThat(october.countsFrom()).isEqualTo(SEPTEMBER_TAKEN);
        assertThat(again.countsUntil()).as("where October started counting").isEqualTo(SEPTEMBER_TAKEN);
    }

    private Member captured(String number, LocalDate joinedOn, Instant at) {
        Member member = MemberFixture.withSurname("InventedCaptured");
        member.setMembershipNumber(number);
        member.setStatus("Active");
        member.setJoinDate(joinedOn);
        member.setCapturedAt(at);
        return members.save(member);
    }

    private static long joined(List<MonthEndFactsRepository.Fact> facts) {
        return facts.stream().mapToLong(MonthEndFactsRepository.Fact::joined).sum();
    }
}

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
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    private MonthEndTakes takes;

    @Autowired
    private MonthEndFactsRepository facts;

    @Autowired
    private PlatformTransactionManager transactions;

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
        jdbc.update("DELETE FROM members WHERE membership_number = 'M-899999'");
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
}

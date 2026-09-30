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
import za.co.titandynamix.sasbo_online_backend.dto.monthend.MonthEndPackDto;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.MonthEndRefusalCode;
import za.co.titandynamix.sasbo_online_backend.repository.MemberRepository;
import za.co.titandynamix.sasbo_online_backend.repository.MonthEndPackRepository;
import za.co.titandynamix.sasbo_online_backend.support.SummaryFixture;
import za.co.titandynamix.sasbo_online_backend.utils.DataSeeder;

import java.time.Clock;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The statistics pack as a job (month end 04.10, ME-21), on H2 -- the launcher on the caller's thread, as every test
 * lane sets it: asked, prepared and READY; a taken month's pack reused; the one in hand answered, another month's
 * refused PACK_BUSY (no queue); a job that died found and failed; a job that fails says so once.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "sasbo.workplaces=all",
        "spring.datasource.url=jdbc:h2:mem:month-end-packs;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@Import(MonthEndPacksTest.AtTheFixturesClock.class)
@DisplayName("The statistics pack, a job")
class MonthEndPacksTest {

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
    private MonthEndPacks packs;

    @Autowired
    private MonthEndTakes takes;

    @Autowired
    private MonthEndPackRepository repository;

    @Autowired
    private Clock clock;

    @BeforeEach
    void thePopulationAndNothingTakenOrAsked() {
        Long present = jdbc.queryForObject("SELECT count(*) FROM workplaces WHERE ra_number = 900001", Long.class);
        if (present == null || present == 0) {
            SummaryFixture.insert(jdbc, members);
        }
        jdbc.update("DELETE FROM month_end_packs");
        jdbc.update("DELETE FROM month_end_counts");
        jdbc.update("DELETE FROM month_end_workplaces");
        jdbc.update("DELETE FROM month_end_months");
    }

    @Test
    @DisplayName("a pack of the open month: asked, prepared, READY -- both workbooks, each an .xlsx")
    void theOpenMonth() {
        MonthEndPacks.Asked asked = packs.ask("2026-09");

        assertThat(asked.started()).isTrue();
        MonthEndPackDto pack = asked.pack();
        assertThat(pack.status()).as("prepared on the caller's thread under test").isEqualTo("READY");
        assertThat(pack.month()).isEqualTo("2026-09");
        assertThat(pack.askedBy()).isEqualTo("system");
        assertThat(pack.startedAt()).isEqualTo(SummaryFixture.NOW.toString());
        assertThat(pack.workbooks()).containsExactly("statistics", "monthly");
        MonthEndPacks.File statistics = packs.file(pack.id(), "statistics");
        assertThat(statistics.name()).isEqualTo("month-end-2026-09-statistics.xlsx");
        assertThat(statistics.bytes()).startsWith((byte) 'P', (byte) 'K');
        assertThat(packs.file(pack.id(), "monthly").bytes()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    @DisplayName("a taken month's pack, once READY, is that month's: asked again, it is the same pack")
    void aTakenMonthsPackIsKept() {
        takes.take(YearMonth.of(2026, 8), "seed");
        MonthEndPacks.Asked first = packs.ask("2026-08");

        MonthEndPacks.Asked again = packs.ask("2026-08");

        assertThat(again.started()).isFalse();
        assertThat(again.pack().id()).isEqualTo(first.pack().id());
    }

    @Test
    @DisplayName("🔒 MR-PERF-11: no queue -- the same month's pack in hand is the answer; another month's is PACK_BUSY")
    void noQueue() {
        takes.take(YearMonth.of(2026, 8), "seed");
        long july = repository.insertPreparing(YearMonth.of(2026, 7).atDay(1), "system", clock.instant());

        assertThatThrownBy(() -> packs.ask("2026-08"))
                .isInstanceOfSatisfying(MonthEndRefusal.class, refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.PACK_BUSY);
                    assertThat(refusal.details()).containsEntry("month", "2026-08").containsEntry("inHand", "2026-07")
                            .containsEntry("pack", String.valueOf(july));
                });
        jdbc.update("DELETE FROM month_end_packs");
        long september = repository.insertPreparing(SummaryFixture.MONTH.atDay(1), "system", clock.instant());

        MonthEndPacks.Asked inHand = packs.ask("2026-09");
        assertThat(inHand.started()).isFalse();
        assertThat(inHand.pack().id()).isEqualTo(september);
        assertThat(inHand.pack().status()).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("🔒 ME-21: a job that died is found -- FAILED, interrupted -- and the next ask starts afresh")
    void aJobThatDiedIsFound() {
        long dead = repository.insertPreparing(SummaryFixture.MONTH.atDay(1), "system",
                clock.instant().minus(MonthEndPacks.HEARTBEAT_LIMIT).minusSeconds(1));

        MonthEndPacks.Asked asked = packs.ask("2026-09");

        assertThat(asked.started()).isTrue();
        assertThat(asked.pack().id()).isNotEqualTo(dead);
        MonthEndPackDto swept = packs.status(dead);
        assertThat(swept.status()).isEqualTo("FAILED");
        assertThat(swept.failure()).isEqualTo(MonthEndPacks.INTERRUPTED);
        assertThat(swept.workbooks()).isEmpty();
    }

    @Test
    @DisplayName("a job that fails says so once, in one sentence -- and holds no workbook")
    void aJobThatFails() {
        MonthEndWorkbooks failing = mock(MonthEndWorkbooks.class);
        when(failing.statistics(any())).thenThrow(new IllegalStateException("planted"));
        MonthEndPacks withAFailingWriter = new MonthEndPacks(repository, takes, failing, MonthEndPackLauncher.inline(),
                clock);

        MonthEndPackDto pack = withAFailingWriter.ask("2026-09").pack();

        assertThat(pack.status()).isEqualTo("FAILED");
        assertThat(pack.failure()).startsWith("Failed: the workbooks could not be written (IllegalStateException)")
                .doesNotContain("planted");
        assertThatThrownBy(() -> packs.file(pack.id(), "statistics")).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.PACK_NOT_FOUND));
    }

    @Test
    @DisplayName("the refusals: a past month not taken, no such pack, no such workbook")
    void theRefusals() {
        assertThatThrownBy(() -> packs.ask("2026-07")).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.MONTH_NOT_TAKEN));
        assertThatThrownBy(() -> packs.status(999_999)).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.PACK_NOT_FOUND));
        long id = packs.ask("2026-09").pack().id();
        assertThatThrownBy(() -> packs.file(id, "payroll")).isInstanceOfSatisfying(MonthEndRefusal.class,
                refusal -> {
                    assertThat(refusal.code()).isEqualTo(MonthEndRefusalCode.PACK_NOT_FOUND);
                    assertThat(refusal.details()).containsEntry("workbook", "payroll");
                });
    }
}

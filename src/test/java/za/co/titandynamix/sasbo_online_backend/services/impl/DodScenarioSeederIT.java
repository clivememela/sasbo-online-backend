package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import za.co.titandynamix.sasbo_online_backend.acb.HolidayCalendarSource;
import za.co.titandynamix.sasbo_online_backend.config.SasboEnvironment;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.services.AcbCalendarService;
import za.co.titandynamix.sasbo_online_backend.services.AcbDebitDayService;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;
import za.co.titandynamix.sasbo_online_backend.utils.BankSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.DebitOrderMemberSeeder;
import za.co.titandynamix.sasbo_online_backend.utils.SubscriptionSeeder;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Debit Order Deductions demonstration on PostgreSQL: every rule of Stephne's that it seeds has something to show
 * -- no zeros (the owner, 6 October) -- all of it dated before the clock's month, once, and never where the production
 * marker is set.
 *
 * <p>{@code AbstractPostgresIT} neutralises {@code DataSeeder} (F49), so the seeders are called here in the order it
 * calls them, against the frozen 18 September 2026: the seeded history is July (closed) and August (returned), and the
 * demonstration's changes are dated 31 August. The reset empties SASBO's lists, which V70 and the reference-list
 * seeder fill on a real database, so V70's suspension codes are put back where a test needs them.
 */
@DisplayName("DodScenarioSeeder -- the Debit Order demonstration, no zeros")
class DodScenarioSeederIT extends AbstractAcbIT {

    /** Midnight on 1 September in Johannesburg: nothing the demonstration writes is dated at or after it. */
    private static final String CLOCKS_MONTH = "TIMESTAMPTZ '2026-09-01 00:00:00+02'";

    @Autowired
    private DodScenarioSeeder seeder;
    @Autowired
    private AcbHistorySeeder history;
    @Autowired
    private BankSeeder bankSeeder;
    @Autowired
    private SubscriptionSeeder subscriptionSeeder;
    @Autowired
    private DebitOrderMemberSeeder memberSeeder;
    @Autowired
    private AcbRunService runs;
    @Autowired
    private AcbReturnsService returns;
    @Autowired
    private AcbUnpaidCountService counts;
    @Autowired
    private AcbDebitDayService debitDays;
    @Autowired
    private AcbCalendarService calendar;
    @Autowired
    private HolidayCalendarSource calendars;
    @Autowired
    private AcbAuditTrail audit;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private Clock clock;

    @BeforeEach
    void theSeedsPopulationIsThere() throws Exception {
        bankSeeder.seed();
        subscriptionSeeder.seed();
        memberSeeder.seed();
    }

    private void sasbosCodesAreOnRecord() {
        for (int code : MigrationSeed.SUSPENSION_CODES) {
            jdbc.update("INSERT INTO suspension_codes (code, name) SELECT ?, ? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM suspension_codes WHERE code = ?)", code, "CODE " + code, code);
        }
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    @Test
    @DisplayName("every rule seeded has something to show: returns by debit day under her sheet's codes, an ending with "
            + "its letter and one left to confirm, SASBO's codes on suspensions, a return that withdrew its re-debit, a "
            + "bank moved, counts captured, a bank closure -- all before the clock's month")
    void everyRuleHasSomethingToShow() {
        assertThat(history.seed()).isEqualTo(AcbHistorySeeder.Outcome.SEEDED);
        sasbosCodesAreOnRecord();

        DodScenarioSeeder.Result seeded = seeder.seed();

        assertThat(seeded.outcome()).isEqualTo(DodScenarioSeeder.Outcome.SEEDED);
        assertThat(seeded.statusChanges()).as("an ending, three suspensions, a suspension and its return").isEqualTo(6);
        assertThat(count("SELECT count(*) FROM member_status_changes WHERE changed_by = 'seed'"))
                .as("what month end's take seeder is told").isEqualTo(seeded.statusChanges());
        assertThat(count("SELECT count(*) FROM acb_audit_events WHERE run_id IS NULL AND kind = 'DOD_SEED_COMPLETE'"))
                .isEqualTo(1);

        // DOD-06: the late returns, one report per debit day, under the codes on her sheet.
        assertThat(count("SELECT count(*) FROM acb_run_lines WHERE returns_batch_id IS NOT NULL"))
                .as("two members' debit and re-debit, and four more").isEqualTo(8);
        assertThat(count("SELECT COALESCE(SUM(recorded), 0) FROM acb_returns_batches")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM acb_returns_batches")).isEqualTo(count(
                "SELECT count(DISTINCT action_date) FROM acb_run_lines WHERE returns_batch_id IS NOT NULL"));
        assertThat(jdbc.queryForList("SELECT DISTINCT unpaid_code FROM acb_run_lines WHERE returns_batch_id IS NOT NULL",
                String.class)).containsExactlyInAnyOrder("2", "6", "15", "26", "30");

        // DOD-22, DOD-24: one ending confirmed, with its short letter, sent in its own month; one left at three.
        assertThat(jdbc.queryForList("SELECT reason FROM member_status_changes WHERE to_status = 'Terminated'",
                String.class)).containsExactly("SUBS_IN_ARREARS");
        assertThat(count("SELECT count(*) FROM acb_letters WHERE template = 'MEMBERSHIP_ENDED' AND status = 'SENT'"))
                .isEqualTo(1);
        List<AcbTerminationReportDto.Candidate> report = returns.terminationReport(0, 50).members().content();
        List<AcbTerminationReportDto.Candidate> leftToConfirm = report.stream()
                .filter(c -> c.terminate() && "Active".equals(c.status())).toList();
        assertThat(leftToConfirm).singleElement().satisfies(c -> assertThat(c.unpaidCount()).isEqualTo(3));

        // DOD-01, DOD-12: suspensions carry SASBO's code from her sheet -- bank 15 is 15, 26 is 19, 30 is 30, 4 is 16.
        assertThat(jdbc.queryForList("SELECT s.code FROM member_status_changes c JOIN suspension_codes s "
                + "ON s.id = c.suspension_code_id WHERE c.to_status = 'Suspended'", Integer.class))
                .containsExactlyInAnyOrder(15, 19, 30, 16);

        // DOD-26: the member made active again has the re-debit waiting for them withdrawn.
        assertThat(count("SELECT count(*) FROM member_status_changes WHERE reason = 'REINSTATED'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM acb_run_lines WHERE re_debit_withdrawn = 'REACTIVATED'")).isEqualTo(1);

        // DOD-19: the bank's debit-order members are all on the new day.
        String atTheBank = "FROM members WHERE LOWER(TRIM(bank_name)) = LOWER('" + DodScenarioSeeder.MOVED_BANK + "') "
                + "AND LOWER(TRIM(payment_type)) IN ('debit order', 'debit_order')";
        assertThat(count("SELECT count(*) " + atTheBank + " AND debit_day = " + DodScenarioSeeder.MOVED_TO_DAY))
                .as("non-vacuity: the bank has debit-order members").isPositive();
        assertThat(count("SELECT count(*) " + atTheBank + " AND (debit_day IS NULL OR debit_day <> "
                + DodScenarioSeeder.MOVED_TO_DAY + ")")).isZero();
        assertThat(count("SELECT count(*) FROM acb_audit_events WHERE kind = 'DEBIT_DAY_MOVED'")).isEqualTo(1);

        // DOD-28: counts captured by hand, as at the last day of August.
        assertThat(counts.list(0, 50).content()).extracting(AcbUnpaidCountDto::unpaidCount)
                .containsExactlyInAnyOrder(2, 1, 0);
        assertThat(counts.list(0, 50).content()).extracting(AcbUnpaidCountDto::countedOn).containsOnly("2026-08-31");

        // DOD-17: a day the bank was closed, in a month no run covers.
        assertThat(jdbc.queryForList("SELECT holiday_date FROM acb_holidays WHERE kind = 'BANK_CLOSED'", LocalDate.class))
                .singleElement().satisfies(day -> {
                    assertThat(day).isEqualTo(LocalDate.of(2026, 4, 15));
                    assertThat(count("SELECT count(*) FROM acb_runs WHERE action_month = DATE '2026-04-01'")).isZero();
                });

        // The clock's own month stays empty, for a live demonstration.
        assertThat(count("SELECT count(*) FROM member_status_changes WHERE changed_at >= " + CLOCKS_MONTH)).isZero();
        assertThat(count("SELECT count(*) FROM member_changes WHERE changed_at >= " + CLOCKS_MONTH)).isZero();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE sent_at >= " + CLOCKS_MONTH)).isZero();
        assertThat(count("SELECT count(*) FROM acb_letters WHERE status = 'RENDERED'"))
                .as("nothing a sender would pick up").isZero();
        assertThat(count("SELECT count(*) FROM acb_unpaid_counts WHERE counted_on >= DATE '2026-09-01'")).isZero();
        assertThat(count("SELECT count(*) FROM acb_runs WHERE action_month >= DATE '2026-09-01'"))
                .as("this month and next are free for a live Prepare").isZero();
    }

    @Test
    @DisplayName("Stephne, DOD-03: next month's preparation over the demonstration leaves the member at three out, "
            + "named UNPAID_LIMIT_REACHED")
    void theNextRunLeavesTheMemberAtThreeOut() {
        history.seed();
        sasbosCodesAreOnRecord();
        seeder.seed();
        Integer atThree = returns.terminationReport(0, 50).members().content().stream()
                .filter(c -> c.terminate() && "Active".equals(c.status())).map(AcbTerminationReportDto.Candidate::memberId)
                .findFirst().orElseThrow();

        AcbRunDto october = runs.get(runs.prepare("2026-10", "system").id());

        assertThat(allExclusions(october.id()))
                .filteredOn(e -> e.reason() == AcbExclusionReason.UNPAID_LIMIT_REACHED)
                .extracting(AcbExclusionDto::memberId).containsExactly(atThree);
    }

    /** Every page of a run's exclusions: they are ordered by reason, and this one sorts late. */
    private List<AcbExclusionDto> allExclusions(long runId) {
        List<AcbExclusionDto> all = new ArrayList<>();
        int number = 0;
        AcbPage<AcbExclusionDto> page;
        do {
            page = runs.exclusions(runId, number++, 200);
            all.addAll(page.content());
        } while (number < page.totalPages());
        assertThat(all).as("non-vacuity: the seeded population has exclusions").isNotEmpty();
        return all;
    }

    @Test
    @DisplayName("a second start seeds nothing more")
    void seededOnce() {
        history.seed();
        sasbosCodesAreOnRecord();
        assertThat(seeder.seed().outcome()).isEqualTo(DodScenarioSeeder.Outcome.SEEDED);
        long changes = count("SELECT count(*) FROM member_status_changes");
        long batches = count("SELECT count(*) FROM acb_returns_batches");

        assertThat(seeder.seed()).isEqualTo(new DodScenarioSeeder.Result(DodScenarioSeeder.Outcome.ALREADY_SEEDED, 0));
        assertThat(count("SELECT count(*) FROM member_status_changes")).isEqualTo(changes);
        assertThat(count("SELECT count(*) FROM acb_returns_batches")).isEqualTo(batches);
    }

    @Test
    @DisplayName("SD3-A: where the production marker is set, nothing is seeded")
    void productionIsNeverSeeded() {
        history.seed();
        sasbosCodesAreOnRecord();
        DodScenarioSeeder inProduction = new DodScenarioSeeder(new SasboEnvironment("production"), jdbc, transactions,
                clock, returns, counts, debitDays, calendar, calendars, audit);

        assertThat(inProduction.seed().outcome()).isEqualTo(DodScenarioSeeder.Outcome.PRODUCTION);
        assertThat(count("SELECT count(*) FROM acb_returns_batches")).isZero();
        assertThat(count("SELECT count(*) FROM acb_audit_events WHERE kind = 'DOD_SEED_COMPLETE'")).isZero();
    }

    @Test
    @DisplayName("without the seeded history, or without V70's codes, it is left alone -- and nothing is written")
    void withoutWhatItNeedsNothingIsWritten() {
        sasbosCodesAreOnRecord();
        assertThat(seeder.seed().outcome()).as("no history").isEqualTo(DodScenarioSeeder.Outcome.NO_HISTORY);

        jdbc.update("DELETE FROM suspension_codes");
        history.seed();
        assertThat(seeder.seed().outcome()).as("SASBO's codes not on record").isEqualTo(DodScenarioSeeder.Outcome.SKIPPED);
        assertThat(count("SELECT count(*) FROM acb_returns_batches")).isZero();
        assertThat(count("SELECT count(*) FROM member_status_changes")).isZero();
        assertThat(count("SELECT count(*) FROM acb_audit_events WHERE kind = 'DOD_SEED_COMPLETE'")).isZero();

        sasbosCodesAreOnRecord();
        assertThat(seeder.seed().outcome()).as("the must-pass twin: with them, it seeds")
                .isEqualTo(DodScenarioSeeder.Outcome.SEEDED);
    }
}

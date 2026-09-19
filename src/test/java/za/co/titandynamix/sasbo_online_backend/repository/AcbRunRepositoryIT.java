package za.co.titandynamix.sasbo_online_backend.repository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import za.co.titandynamix.sasbo_online_backend.acb.BankRow;
import za.co.titandynamix.sasbo_online_backend.acb.LineTotals;
import za.co.titandynamix.sasbo_online_backend.acb.MemberDebitRow;
import za.co.titandynamix.sasbo_online_backend.acb.RePresentationCandidate;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbExclusionDto;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbAuditEvent;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbRun;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.SubscriptionsStatus;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ACB's tables and queries on real PostgreSQL, through the full migration chain.
 *
 * <p>Everything here is the database's to decide -- the partial unique indexes, the CHECK
 * constraints, the named indexes, the ordering and the JPQL -- which is exactly what the fast
 * lane's H2 cannot be evidence about. The service's behaviour over these tables is
 * {@code AcbPreparationJobIT}.
 */
@DisplayName("ACB's tables and repositories, on PostgreSQL")
class AcbRunRepositoryIT extends AbstractAcbIT {

    private static final Set<AcbRunStatus> RELEASED = EnumSet.of(AcbRunStatus.FAILED, AcbRunStatus.CANCELLED);
    private static final Set<AcbRunStatus> SENT =
            EnumSet.of(AcbRunStatus.SUBMITTED, AcbRunStatus.RETURNED, AcbRunStatus.CLOSED);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
    private static final BigDecimal RATE = new BigDecimal("174.00");

    @Autowired
    private AcbRunRepository runs;
    @Autowired
    private AcbRunLineRepository lines;
    @Autowired
    private AcbRunExclusionRepository exclusions;
    @Autowired
    private AcbRunExclusionCountRepository exclusionCounts;
    @Autowired
    private AcbAuditEventRepository auditEvents;
    @Autowired
    private AcbRateRepository rates;
    @Autowired
    private AcbPopulationRepository population;

    // ── the schema V41 to V43 build ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("exactly seven ACB tables -- which is what takes the chain from 25 tables to 32")
    void theSevenTables() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name LIKE 'acb\\_%' ORDER BY table_name", String.class))
                .containsExactly("acb_audit_events", "acb_rates", "acb_run_exclusion_counts",
                        "acb_run_exclusions", "acb_run_files", "acb_run_lines", "acb_runs");
    }

    @Test
    @DisplayName("every hot predicate is indexed, by name, in the migration that made its table")
    void theNamedIndexes() {
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE tablename LIKE 'acb\\_%'",
                String.class)).contains(
                "acb_runs_one_live_run_per_month",
                "acb_run_lines_run",
                "acb_run_lines_member_outcome",
                "acb_run_lines_one_current_line_per_member",
                "acb_run_lines_re_presented_once",
                "acb_run_lines_file_sequence",
                "acb_audit_events_run_occurred");
    }

    @Test
    @DisplayName("V41 seeds the flat rate, and a month before it has none")
    void theRateIsSeeded() {
        assertThat(rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(NEXT_MONTH))
                .hasValueSatisfying(rate -> {
                    assertThat(rate.getAmount()).isEqualByComparingTo("174.00");
                    assertThat(rate.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 1, 1));
                });
        assertThat(rates.findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate.of(2025, 12, 1)))
                .isEmpty();
    }

    @Test
    @DisplayName("a run's month is the first of a month -- the 15th is refused by the database")
    void anActionMonthIsAMonth() {
        assertThatThrownBy(() -> insertRun(LocalDate.of(2026, 10, 15), "PREPARED", FROZEN, "system"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── one live run per month, and a failed or cancelled one releases it ───────────────────────

    @Test
    @DisplayName("a second live run for the month is refused by the index")
    void aSecondLiveRunIsRefused() {
        insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        assertThatThrownBy(() -> insertRun(NEXT_MONTH, "PREPARING", FROZEN, "system"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("FAILED and CANCELLED runs stay on record beside the live one that replaced them")
    void deadRunsReleaseTheirMonth() {
        insertRun(NEXT_MONTH, "FAILED", FROZEN, "system");
        insertRun(NEXT_MONTH, "CANCELLED", FROZEN, "system");
        long live = insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");

        assertThat(runs.findLive(NEXT_MONTH, RELEASED)).map(AcbRun::getId).contains(live);
        assertThat(runs.count()).isEqualTo(3);
    }

    // ── a member's lines in one run ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("a current line and a re-presentation share a run; two current lines do not")
    void onlyOneCurrentLine() {
        AcbRunFixture.insert(jdbc, 1, 1);
        int member = AcbRunFixture.FIRST_MEMBER_ID;
        long august = insertRun(AUGUST, "SUBMITTED", FROZEN, "system");
        long unpaid = insertLine(august, member, null, "UNPAID", "RE_PRESENT", RATE);
        long september = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");

        insertLine(september, member, null, "PENDING", null, RATE);
        insertLine(september, member, unpaid, "PENDING", null, RATE);

        assertThatThrownBy(() -> insertLine(september, member, null, "PENDING", null, RATE))
                .as("a second CURRENT line for the member")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("an unpaid is re-presented at most once -- until the run that claimed it is released")
    void rePresentedOnceUntilReleased() {
        AcbRunFixture.insert(jdbc, 1, 1);
        int member = AcbRunFixture.FIRST_MEMBER_ID;
        long august = insertRun(AUGUST, "SUBMITTED", FROZEN, "system");
        long unpaid = insertLine(august, member, null, "UNPAID", "RE_PRESENT", RATE);
        long september = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        insertLine(september, member, unpaid, "PENDING", null, RATE);

        long october = insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        assertThatThrownBy(() -> insertLine(october, member, unpaid, "PENDING", null, RATE))
                .as("the same unpaid, re-presented by two live runs")
                .isInstanceOf(DataIntegrityViolationException.class);

        // Releasing September's claim -- what cancelling it does -- lets October offer it again.
        assertThat(lines.releaseRun(september)).isEqualTo(1);
        insertLine(october, member, unpaid, "PENDING", null, RATE);
    }

    @Test
    @DisplayName("the account number is digits, eleven at most, by the database's own check")
    void theAccountIsChecked() {
        AcbRunFixture.insert(jdbc, 1, 1);
        long run = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO acb_run_lines (run_id, member_id, membership_number, "
                + "surname, bank_name, branch_code, account_number, debit_day, action_date, amount) "
                + "VALUES (?, ?, 'F-1', 'Surname', 'Bank', '250000', '62 1234', 25, ?, 174.00)",
                run, AcbRunFixture.FIRST_MEMBER_ID, LocalDate.of(2026, 9, 25)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── the queries a run is made and read with ─────────────────────────────────────────────────

    @Test
    @DisplayName("runs are paged newest month first")
    void runsArePaged() {
        insertRun(AUGUST, "CLOSED", FROZEN, "system");
        insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        assertThat(runs.findPage(PageRequest.of(0, 2)))
                .extracting(AcbRun::getActionMonth)
                .containsExactly(NEXT_MONTH, SEPTEMBER);
        assertThat(runs.findPage(PageRequest.of(0, 2)).getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("the heartbeat moves without bumping the version -- a pulse is not a change")
    void theHeartbeatIsNotAVersion() {
        long id = insertRun(NEXT_MONTH, "PREPARING", FROZEN.minusSeconds(600), "system");
        assertThat(runs.findByStatusAndHeartbeatAtBefore(AcbRunStatus.PREPARING, FROZEN.minusSeconds(300)))
                .as("stale before the pulse").hasSize(1);

        int updated = runs.touchHeartbeat(id, AcbRunStatus.PREPARING, FROZEN);

        AcbRun run = runs.findById(id).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(run.getHeartbeatAt()).isEqualTo(FROZEN);
        assertThat(run.getVersion()).isZero();
        assertThat(runs.findByStatusAndHeartbeatAtBefore(AcbRunStatus.PREPARING, FROZEN.minusSeconds(300)))
                .as("alive after it").isEmpty();
    }

    @Test
    @DisplayName("a run whose heartbeat stopped is found by the sweep's query, and a live one is not")
    void staleRunsAreFound() {
        long stale = insertRun(NEXT_MONTH, "PREPARING", FROZEN.minusSeconds(600), "system");
        insertRun(SEPTEMBER, "PREPARING", FROZEN, "system");
        assertThat(runs.findByStatusAndHeartbeatAtBefore(AcbRunStatus.PREPARING, FROZEN.minusSeconds(300)))
                .extracting(AcbRun::getId).containsExactly(stale);
    }

    @Test
    @DisplayName("control totals are counted from the lines, one run or a page of them in one query")
    void totals() {
        AcbRunFixture.insert(jdbc, 1, 2);
        long run = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        long empty = insertRun(NEXT_MONTH, "PREPARED", FROZEN, "system");
        insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        insertLine(run, AcbRunFixture.FIRST_MEMBER_ID + 1, null, "PENDING", null, new BigDecimal("170.00"));

        LineTotals totals = lines.totalsFor(run);
        assertThat(totals.lineCount()).isEqualTo(2);
        assertThat(totals.total()).isEqualByComparingTo("344.00");
        assertThat(lines.totalsFor(empty).total()).isEqualByComparingTo("0.00");
        assertThat(lines.totalsForRuns(List.of(run, empty))).as("no row for a run with no lines").hasSize(1);
        assertThat(lines.findPage(run, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("re-presentation candidates: sent runs, earlier months, 'present again', not already claimed")
    void rePresentationCandidates() {
        AcbRunFixture.insert(jdbc, 1, 6);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        long augustSent = insertRun(AUGUST, "SUBMITTED", FROZEN, "system");
        long wanted = insertLine(augustSent, m, null, "UNPAID", "RE_PRESENT", new BigDecimal("170.00"));
        insertLine(augustSent, m + 1, null, "UNPAID", "SUSPEND", RATE);
        insertLine(augustSent, m + 2, null, "UNPAID", null, RATE);
        insertLine(augustSent, m + 3, null, "PAID", null, RATE);
        long claimed = insertLine(augustSent, m + 4, null, "UNPAID", "RE_PRESENT", RATE);
        long septemberLive = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        insertLine(septemberLive, m + 4, claimed, "PENDING", null, RATE);
        // A prepared run was never sent, so an "unpaid" on it is not real yet.
        long julyUnsent = insertRun(JULY, "PREPARED", FROZEN, "system");
        insertLine(julyUnsent, m + 5, null, "UNPAID", "RE_PRESENT", RATE);

        List<RePresentationCandidate> candidates = lines.findRePresentationCandidates(
                NEXT_MONTH, AcbLineOutcome.UNPAID, UnpaidDecision.RE_PRESENT, SENT);

        assertThat(candidates).singleElement().satisfies(c -> {
            assertThat(c.lineId()).isEqualTo(wanted);
            assertThat(c.memberId()).isEqualTo(m);
            assertThat(c.amount()).isEqualByComparingTo("170.00");
            assertThat(c.month()).isEqualTo(YearMonth.of(2026, 8));
        });
        // Never for the unpaid's own month or before it.
        assertThat(lines.findRePresentationCandidates(AUGUST, AcbLineOutcome.UNPAID,
                UnpaidDecision.RE_PRESENT, SENT)).isEmpty();
    }

    @Test
    @DisplayName("the exclusions are read with the member they name, and counted by reason")
    void exclusionsAndCounts() {
        AcbRunFixture.insert(jdbc, 1, 2);
        long run = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        jdbc.update("INSERT INTO acb_run_exclusions (run_id, member_id, reason, detail) VALUES (?, ?, ?, ?)",
                run, AcbRunFixture.FIRST_MEMBER_ID, "DAY_OUT_OF_WINDOW", "5");
        jdbc.update("INSERT INTO acb_run_exclusion_counts (run_id, reason, detail, member_count) VALUES (?, ?, ?, ?)",
                run, "NOT_DEBIT_ORDER", "Payroll Deduction", 48);

        List<AcbExclusionDto> page = exclusions.findPage(run, PageRequest.of(0, 10)).getContent();
        assertThat(page).singleElement().satisfies(e -> {
            assertThat(e.membershipNumber()).isEqualTo(AcbRunFixture.membershipNumber(AcbRunFixture.FIRST_MEMBER_ID));
            assertThat(e.reason()).isEqualTo(AcbExclusionReason.DAY_OUT_OF_WINDOW);
            assertThat(e.detail()).isEqualTo("5");
        });
        assertThat(exclusions.countByReason(run)).singleElement()
                .satisfies(row -> assertThat(row[0]).isEqualTo(AcbExclusionReason.DAY_OUT_OF_WINDOW));
        assertThat(exclusionCounts.findByRunIdOrderByReasonAscDetailAsc(run)).singleElement()
                .satisfies(c -> assertThat(c.getMemberCount()).isEqualTo(48));
    }

    @Test
    @DisplayName("the audit trail reads back in the order it happened")
    void theAuditTrail() {
        long run = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        jdbc.update("INSERT INTO acb_audit_events (run_id, occurred_at, actor, kind, to_state) "
                + "VALUES (?, ?, 'system', 'LATER', 'PREPARED')", run, OffsetDateTime.ofInstant(FROZEN, ZoneOffset.UTC));
        jdbc.update("INSERT INTO acb_audit_events (run_id, occurred_at, actor, kind, to_state) "
                + "VALUES (?, ?, 'system', 'FIRST', 'PREPARING')", run,
                OffsetDateTime.ofInstant(FROZEN.minusSeconds(60), ZoneOffset.UTC));
        assertThat(auditEvents.findByRunIdOrderByOccurredAtAscIdAsc(run))
                .extracting(AcbAuditEvent::getKind).containsExactly("FIRST", "LATER");
    }

    @Test
    @DisplayName("the population is read a page at a time, keyed on the last id seen")
    void thePopulation() {
        AcbRunFixture.Built built = AcbRunFixture.insert(jdbc, 15, 2).assertSize();

        List<MemberDebitRow> first = population.findPageAfter(0, PageRequest.of(0, 10));
        List<MemberDebitRow> second = population.findPageAfter(first.get(first.size() - 1).id(), PageRequest.of(0, 10));
        assertThat(first).hasSize(10);
        assertThat(second.get(0).id()).isEqualTo(first.get(9).id() + 1);
        assertThat(first.get(0).bankAccountNumber()).isEqualTo(AcbRunFixture.account(AcbRunFixture.FIRST_MEMBER_ID));

        assertThat(population.findBanks()).extracting(BankRow::name)
                .contains(AcbRunFixture.bankName(0), AcbRunFixture.UNCOLLECTED_BANK)
                .hasSize(built.banks() + 1);
        // One ACTIVE row per collectable bank; the uncollected bank's only row is PENDING.
        assertThat(population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.ACTIVE)).hasSize(built.banks());
        assertThat(population.findBankIdsOfSubscriptionRows(SubscriptionsStatus.PENDING)).hasSize(1);
    }
}

package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsBatchDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.MemberReturnRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.ReturnsBatchRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.SuspendMemberRequest;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Stephne's debit order rules of 6 October 2026, on PostgreSQL: the returns as one report per debit day, the re-debit
 * refused by code and at three, the run that leaves a member at three out, an ending confirmed with its letter, and a
 * member's return. Each test names her answer. The counts captured by hand are {@link AcbUnpaidCountServiceImplIT}'s,
 * the codes as data {@link AcbCodeServiceImplIT}'s, and a bank's move to a new debit day
 * {@link AcbDebitDayServiceImplIT}'s.
 */
@DisplayName("Debit Order Deductions, Stephne's rules (DOD-03 to DOD-28), on PostgreSQL")
class DodReturnsIT extends AbstractAcbIT {

    private static final BigDecimal RATE = new BigDecimal("174.00");
    private static final LocalDate JUNE = LocalDate.of(2026, 6, 1);
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);

    @Autowired
    private AcbReturnsService returns;
    @Autowired
    private AcbRunService runs;
    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsBatchRepository batches;
    @Autowired
    private AcbUnpaidCounter counter;

    private int member;

    private static AcbRefusal refusal(Runnable call) {
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, call::run);
        assertThat(refused).as("refused").isNotNull();
        return refused;
    }

    private int fixture() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        member = AcbRunFixture.FIRST_MEMBER_ID;
        return member;
    }

    /** An unpaid already on record in a sent run, with its code and decision. */
    private long unpaidLine(long runId, int memberId, String code, String decision) {
        long line = insertLine(runId, memberId, null, "UNPAID", decision, RATE);
        jdbc.update("UPDATE acb_run_lines SET unpaid_code = ?, unpaid_reason = ? WHERE id = ?", code,
                jdbc.queryForList("SELECT reason FROM acb_unpaid_codes WHERE code = ?", String.class, code).stream()
                        .findFirst().orElse("OTHER"), line);
        return line;
    }

    private void suspensionCodes(int... codesOnRecord) {
        for (int code : codesOnRecord) {
            jdbc.update("INSERT INTO suspension_codes (code, name) SELECT ?, ? WHERE NOT EXISTS "
                    + "(SELECT 1 FROM suspension_codes WHERE code = ?)", code, "CODE " + code, code);
        }
    }

    private String outcomeOf(long lineId) {
        return jdbc.queryForObject("SELECT outcome FROM acb_run_lines WHERE id = ?", String.class, lineId);
    }

    // ------------------------------------------------------------------------------------------
    // One report per debit day (DOD-05, DOD-06, DOD-07, DOD-12)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Stephne, DOD-06: one debit day's report recorded at once -- every unpaid, every letter -- and typed "
            + "again it changes nothing")
    void aDebitDaysReportIsRecordedOnce() {
        int m = fixture();
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long first = insertLine(september, m, null, "PENDING", null, RATE);
        long second = insertLine(september, m + 1, null, "PENDING", null, RATE);
        long third = insertLine(september, m + 2, null, "PENDING", null, RATE);
        ReturnsBatchRequest report = new ReturnsBatchRequest("2026-09-25", "2026-09-28", List.of(
                new ReturnsBatchRequest.Entry(first, null, "2", UnpaidDecision.RE_PRESENT),
                new ReturnsBatchRequest.Entry(second, null, "4", UnpaidDecision.SUSPEND),
                new ReturnsBatchRequest.Entry(third, null, "99", null)));

        AcbReturnsBatchDto recorded = returns.recordReturnsBatch(september, report, "administrator");

        assertThat(recorded.recorded()).isEqualTo(3);
        assertThat(recorded.alreadyRecorded()).isZero();
        assertThat(recorded.lettersQueued()).as("code 2 and code 4 each send a letter; an unknown code none").isEqualTo(2);
        assertThat(recorded.lines()).extracting(AcbRunLineDto::returnsBatchId).containsOnly(recorded.id());
        assertThat(jdbc.queryForObject("SELECT unpaid_noted_on FROM acb_run_lines WHERE id = ?", LocalDate.class, first))
                .as("noted on the report's date").isEqualTo(LocalDate.of(2026, 9, 28));
        assertThat(jdbc.queryForList("SELECT template FROM acb_letters ORDER BY id", String.class))
                .containsExactly(LetterTemplate.RE_DEBIT.name(), LetterTemplate.PAYMENT_STOPPED.name());

        AcbReturnsBatchDto again = returns.recordReturnsBatch(september, report, "administrator");
        assertThat(again.recorded()).isZero();
        assertThat(again.alreadyRecorded()).isEqualTo(3);
        assertThat(again.lettersQueued()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_letters", Long.class)).as("no second letter").isEqualTo(2L);
        assertThat(returns.returnsBatches(september, 0, 50).totalElements()).isEqualTo(2);
        assertThat(batches.findByRunIdOrderByActionDateDescIdDesc(september,
                org.springframework.data.domain.PageRequest.of(0, 10)).getContent())
                .extracting(za.co.titandynamix.sasbo_online_backend.entity.acb.AcbReturnsBatch::getRecorded)
                .as("newest first: the retry recorded nothing, the first report three").containsExactly(0, 3);
        assertThat(returns.debitDays(september).days()).singleElement().satisfies(day -> {
            assertThat(day.actionDate()).isEqualTo("2026-09-25");
            assertThat(day.lines()).isEqualTo(3);
            assertThat(day.unpaid()).isEqualTo(3);
            assertThat(day.batches()).isEqualTo(2);
        });
    }

    @Test
    @DisplayName("Stephne, DOD-06: a report with problems records nothing, and names each problem by entry")
    void aReportWithProblemsRecordsNothing() {
        int m = fixture();
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long august = insertRun(AUGUST, "CLOSED", FROZEN, "system");
        long good = insertLine(september, m, null, "PENDING", null, RATE);
        long otherRun = insertLine(august, m + 1, null, "PENDING", null, RATE);
        long otherDay = insertLine(september, m + 2, null, "PENDING", null, RATE);
        jdbc.update("UPDATE acb_run_lines SET action_date = DATE '2026-09-28' WHERE id = ?", otherDay);

        AcbRefusal refused = refusal(() -> returns.recordReturnsBatch(september, new ReturnsBatchRequest("2026-09-25",
                null, List.of(
                new ReturnsBatchRequest.Entry(good, null, "2", null),
                new ReturnsBatchRequest.Entry(otherRun, null, "2", null),
                new ReturnsBatchRequest.Entry(otherDay, null, "2", null),
                new ReturnsBatchRequest.Entry(good, null, "2", null))), "administrator"));

        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RETURNS_BATCH_INVALID);
        assertThat(refused.details()).containsEntry("problems", "3");
        assertThat(refused.details().get("problem.1")).contains("entry 2").contains("not in this run");
        assertThat(refused.details().get("problem.2")).contains("entry 3").contains("2026-09-28");
        assertThat(refused.details().get("problem.3")).contains("entry 4").contains("twice");
        assertThat(outcomeOf(good)).as("all or nothing").isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_returns_batches", Long.class)).isZero();
    }

    @Test
    @DisplayName("Stephne, DOD-03: a report that would present a code-4 debit again is refused whole -- the code-2 "
            + "entry beside it is not recorded either")
    void aReportBreakingTheRulesIsRefusedWhole() {
        int m = fixture();
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long allowed = insertLine(september, m, null, "PENDING", null, RATE);
        long stopped = insertLine(september, m + 1, null, "PENDING", null, RATE);

        AcbRefusal refused = refusal(() -> returns.recordReturnsBatch(september, new ReturnsBatchRequest("2026-09-25",
                null, List.of(new ReturnsBatchRequest.Entry(allowed, null, "2", UnpaidDecision.RE_PRESENT),
                new ReturnsBatchRequest.Entry(stopped, null, "4", UnpaidDecision.RE_PRESENT))), "administrator"));

        assertThat(refused.code()).isEqualTo(AcbRefusalCode.RETURNS_BATCH_INVALID);
        assertThat(refused.details().get("problem.1")).contains("entry 2").contains("code 4");
        assertThat(outcomeOf(allowed)).isEqualTo("PENDING");
        assertThat(outcomeOf(stopped)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_letters", Long.class)).as("no letter either").isZero();
    }

    // ------------------------------------------------------------------------------------------
    // The re-debit rules (DOD-03, DOD-04, DOD-20)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Stephne, DOD-03 and DOD-04: present again a code-4, a code-30 or an unknown-code debit -- "
            + "RE_DEBIT_NOT_ALLOWED, and the unpaid recorded with the decision rolls back; code 2 is allowed")
    void reDebitsAreRefusedByCode() {
        int m = fixture();
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long stopped = insertLine(september, m, null, "PENDING", null, RATE);
        long provisional = insertLine(september, m + 1, null, "PENDING", null, RATE);
        long allowed = insertLine(september, m + 2, null, "PENDING", null, RATE);

        AcbRefusal four = refusal(() -> returns.recordUnpaid(september, stopped,
                new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.RE_PRESENT), "administrator"));
        assertThat(four.code()).isEqualTo(AcbRefusalCode.RE_DEBIT_NOT_ALLOWED);
        assertThat(four.details()).containsEntry("reDebit", "NO").containsEntry("unpaidCode", "4");
        assertThat(outcomeOf(stopped)).as("nothing is half-recorded").isEqualTo("PENDING");

        AcbRefusal six = refusal(() -> returns.recordUnpaid(september, provisional,
                new RecordUnpaidRequest("6", "2026-09-28", UnpaidDecision.RE_PRESENT), "administrator"));
        assertThat(six.code()).isEqualTo(AcbRefusalCode.RE_DEBIT_NOT_ALLOWED);
        assertThat(six.details()).containsEntry("reDebit", "UNKNOWN");

        returns.recordUnpaid(september, provisional, new RecordUnpaidRequest("30", "2026-09-28", null), "administrator");
        assertThat(refusal(() -> returns.decide(september, provisional, UnpaidDecision.RE_PRESENT, "administrator"))
                .code()).isEqualTo(AcbRefusalCode.RE_DEBIT_NOT_ALLOWED);

        AcbRunLineDto two = returns.recordUnpaid(september, allowed,
                new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), "administrator");
        assertThat(two.decision()).as("the must-pass twin").isEqualTo(UnpaidDecision.RE_PRESENT);
        assertThat(two.reDebit()).isEqualTo(ReDebit.YES);
        assertThat(two.suspensionCode()).as("bank 2 records SASBO 18 (her sheet)").isEqualTo(18);
    }

    @Test
    @DisplayName("Stephne, DOD-03: \"Cannot present more then 3 unpaid debits\" -- a third unpaid is not presented again "
            + "(UNPAID_LIMIT_REACHED); after a paid debit the count starts again (DOD-20) and it is")
    void neverMoreThanThree() {
        int m = fixture();
        long july = insertRun(JULY, "CLOSED", FROZEN, "system");
        long august = insertRun(AUGUST, "CLOSED", FROZEN, "system");
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        unpaidLine(july, m, "2", "RE_PRESENT");
        unpaidLine(august, m, "2", "RE_PRESENT");
        long third = insertLine(september, m, null, "PENDING", null, RATE);
        // The twin: the same history with a paid debit between.
        unpaidLine(july, m + 1, "2", "RE_PRESENT");
        insertLine(august, m + 1, null, "PAID", null, RATE);
        long afterPaid = insertLine(september, m + 1, null, "PENDING", null, RATE);

        AcbRefusal refused = refusal(() -> returns.recordUnpaid(september, third,
                new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), "administrator"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.UNPAID_LIMIT_REACHED);
        assertThat(refused.details()).containsEntry("unpaidCount", "3");
        assertThat(outcomeOf(third)).isEqualTo("PENDING");

        AcbRunLineDto allowed = returns.recordUnpaid(september, afterPaid,
                new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), "administrator");
        assertThat(allowed.memberUnpaidCount()).as("July's unpaid was wiped by August's payment").isEqualTo(1);

        // Recorded without a decision, the third suggests SUSPEND: nothing is presented at three.
        AcbRunLineDto undecided = returns.recordUnpaid(september, third, new RecordUnpaidRequest("2", "2026-09-28", null),
                "administrator");
        assertThat(undecided.memberUnpaidCount()).isEqualTo(3);
        assertThat(undecided.suggestedDecision()).isEqualTo(UnpaidDecision.SUSPEND);
    }

    @Test
    @DisplayName("Stephne, DOD-03: a member at three is left out of the next run, named UNPAID_LIMIT_REACHED, and their "
            + "waiting re-debit with them")
    void aMemberAtThreeIsLeftOutOfTheRun() {
        int m = fixture();
        unpaidLine(insertRun(JULY, "CLOSED", FROZEN, "system"), m, "2", "SUSPEND");
        unpaidLine(insertRun(AUGUST, "CLOSED", FROZEN, "system"), m, "2", "SUSPEND");
        unpaidLine(insertRun(SEPTEMBER, "RETURNED", FROZEN, "system"), m, "2", "RE_PRESENT");

        AcbRunDto october = runs.get(runs.prepare("2026-10", "system").id());

        assertThat(runs.lines(october.id(), 0, 200).content()).extracting(AcbRunLineDto::memberId).doesNotContain(m);
        assertThat(runs.exclusions(october.id(), 0, 200).content())
                .filteredOn(e -> e.memberId() == m).singleElement()
                .satisfies(e -> assertThat(e.reason()).isEqualTo(AcbExclusionReason.UNPAID_LIMIT_REACHED));
    }

    // ------------------------------------------------------------------------------------------
    // After the third unpaid (DOD-22 to DOD-27)
    // ------------------------------------------------------------------------------------------

    private int atThree() {
        int m = fixture();
        unpaidLine(insertRun(JULY, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        unpaidLine(insertRun(AUGUST, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        unpaidLine(insertRun(SEPTEMBER, "RETURNED", FROZEN, "system"), m, "2", "SUSPEND");
        return m;
    }

    @Test
    @DisplayName("Stephne, DOD-22 and DOD-24: a staff member confirms the ending, and the member's short letter is queued "
            + "on the third unpaid -- asked again, nothing more")
    void anEndingIsConfirmedWithItsLetter() {
        int m = atThree();
        long thirdLine = jdbc.queryForObject("SELECT MAX(id) FROM acb_run_lines WHERE member_id = ?", Long.class, m);

        AcbMemberStatusDto ended = returns.terminate(m, "administrator");

        assertThat(ended.status()).isEqualTo("Terminated");
        assertThat(ended.reason()).isEqualTo("SUBS_IN_ARREARS");
        assertThat(ended.thirdUnpaidOn()).isEqualTo("2026-09-25");
        assertThat(ended.letterId()).isNotNull();
        assertThat(jdbc.queryForMap("SELECT line_id, template FROM acb_letters WHERE id = ?", ended.letterId()))
                .containsEntry("template", LetterTemplate.MEMBERSHIP_ENDED.name())
                .containsEntry("line_id", thirdLine);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE kind = 'MEMBER_ENDED'", Long.class))
                .isEqualTo(1L);

        returns.terminate(m, "administrator");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_letters WHERE template = 'MEMBERSHIP_ENDED'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_status_changes WHERE member_id = ?", Long.class, m))
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Stephne, DOD-26: \"Only the current month\" -- a member made active again has the re-debit waiting "
            + "from before withdrawn, and the next run does not present it")
    void aReactivatedMemberPaysOnlyTheCurrentMonth() {
        int m = fixture();
        long august = insertRun(AUGUST, "CLOSED", FROZEN, "system");
        long waiting = unpaidLine(august, m, "2", "RE_PRESENT");
        jdbc.update("UPDATE members SET status = 'Suspended' WHERE id = ?", m);

        AcbMemberStatusDto back = returns.reactivate(m, new MemberReturnRequest(null, "new banking details"),
                "administrator");

        assertThat(back.status()).isEqualTo("Active");
        assertThat(back.reason()).isEqualTo("REINSTATED");
        assertThat(back.withdrawnReDebits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT re_debit_withdrawn FROM acb_run_lines WHERE id = ?", String.class, waiting))
                .isEqualTo("REACTIVATED");
        AcbRunDto october = runs.get(runs.prepare("2026-10", "system").id());
        assertThat(runs.lines(october.id(), 0, 200).content()).extracting(AcbRunLineDto::rePresentsLineId)
                .doesNotContain(waiting);
        assertThat(runs.lines(october.id(), 0, 200).content()).extracting(AcbRunLineDto::memberId)
                .as("the current month is collected").contains(m);
        assertThat(refusal(() -> returns.reactivate(m, null, "administrator")).code())
                .as("only a suspended member is reactivated").isEqualTo(AcbRefusalCode.MEMBER_NOT_SUSPENDED);
    }

    @Test
    @DisplayName("Stephne, DOD-26: a member brought back through month end's status screen -- the next preparation "
            + "withdraws the waiting re-debit all the same")
    void aReturnByAnyRouteWithdrawsTheReDebit() {
        int m = fixture();
        long august = insertRun(AUGUST, "CLOSED", FROZEN, "system");
        long waiting = unpaidLine(august, m, "2", "RE_PRESENT");
        jdbc.update("INSERT INTO member_status_changes (member_id, from_status, to_status, reason, changed_by, changed_at) "
                + "VALUES (?, 'Suspended', 'Active', 'REINSTATED', 'someone', ?)", m,
                Timestamp.from(Instant.parse("2026-09-10T08:00:00Z")));

        AcbRunDto october = runs.get(runs.prepare("2026-10", "system").id());

        assertThat(runs.lines(october.id(), 0, 200).content()).extracting(AcbRunLineDto::rePresentsLineId)
                .doesNotContain(waiting);
        assertThat(jdbc.queryForObject("SELECT re_debit_withdrawn FROM acb_run_lines WHERE id = ?", String.class, waiting))
                .isEqualTo("REACTIVATED");
    }

    @Test
    @DisplayName("Stephne, DOD-27: \"Yes, freely\" -- a member whose membership ended rejoins with a new join date, and "
            + "starts again at nought")
    void aMemberRejoinsFreely() {
        int m = fixture();
        // All three unpaids before the day they rejoin: June, July and August, each debited on the 25th.
        unpaidLine(insertRun(JUNE, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        unpaidLine(insertRun(JULY, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        unpaidLine(insertRun(AUGUST, "CLOSED", FROZEN, "system"), m, "2", "SUSPEND");
        assertThat(counter.of(m).count().unpaids()).as("at three when the ending is confirmed").isEqualTo(3);
        returns.terminate(m, "administrator");

        AcbMemberStatusDto rejoined = returns.rejoin(m, new MemberReturnRequest("2026-09-18", null), "administrator");

        assertThat(rejoined.status()).isEqualTo("Active");
        assertThat(rejoined.joinedOn()).isEqualTo("2026-09-18");
        assertThat(jdbc.queryForObject("SELECT join_date FROM members WHERE id = ?", LocalDate.class, m))
                .isEqualTo(LocalDate.of(2026, 9, 18));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_changes WHERE member_id = ? AND field = 'joinDate'",
                Long.class, m)).isEqualTo(1L);
        assertThat(counter.of(m).count().unpaids()).as("nothing before the new join date counts").isZero();
        assertThat(refusal(() -> returns.rejoin(m, null, "administrator")).code())
                .as("an active member is not rejoined").isEqualTo(AcbRefusalCode.MEMBER_NOT_ENDED);
        assertThat(refusal(() -> returns.rejoin(m + 1, new MemberReturnRequest("2026-09-19", null), "administrator"))
                .code()).as("never a date in the future").isEqualTo(AcbRefusalCode.DATE_INVALID);
    }

    @Test
    @DisplayName("Stephne, DOD-12 and her sheet: a suspension takes SASBO's code -- the bank's 18 (\"Deceased\") names "
            + "none, so one must be chosen; a chosen code must be on record")
    void suspensionsTakeSasbosCode() {
        int m = fixture();
        suspensionCodes(13, 16);
        long september = insertRun(SEPTEMBER, "RETURNED", FROZEN, "system");
        long deceased = unpaidLine(september, m, "18", "SUSPEND");

        AcbRefusal noCode = refusal(() -> returns.suspendMember(september, deceased, null, "administrator"));
        assertThat(noCode.code()).isEqualTo(AcbRefusalCode.SUSPENSION_CODE_REQUIRED);
        assertThat(refusal(() -> returns.suspendMember(september, deceased, new SuspendMemberRequest(77, null),
                "administrator")).code()).isEqualTo(AcbRefusalCode.SUSPENSION_CODE_UNKNOWN);

        AcbMemberStatusDto chosen = returns.suspendMember(september, deceased, new SuspendMemberRequest(13, null),
                "administrator");
        assertThat(chosen.status()).isEqualTo("Suspended");
        assertThat(chosen.suspensionCode()).isEqualTo(13);
    }

    @Test
    @DisplayName("a waiting re-debit whose code no longer allows one is not presented: the code table decides at every "
            + "preparation")
    void aCodeChangedLaterIsHonoured() {
        int m = fixture();
        long waiting = unpaidLine(insertRun(AUGUST, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        jdbc.update("UPDATE acb_unpaid_codes SET re_debit_allowed = FALSE WHERE code = '2'");

        AcbRunDto october = runs.get(runs.prepare("2026-10", "system").id());

        assertThat(runs.lines(october.id(), 0, 200).content()).extracting(AcbRunLineDto::rePresentsLineId)
                .doesNotContain(waiting);
        assertThat(returns.heldOver(october.id(), 0, 50).content()).as("and it is not held over either").isEmpty();
    }
}

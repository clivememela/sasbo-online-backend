package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbHeldLetterDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbMemberStatusDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
import za.co.titandynamix.sasbo_online_backend.enums.AcbRunStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterStatus;
import za.co.titandynamix.sasbo_online_backend.enums.LetterTemplate;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidReason;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbLetterRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbReturnsRepository;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCodeRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbRunService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;
import za.co.titandynamix.sasbo_online_backend.support.LogCapture;
import za.co.titandynamix.sasbo_online_backend.support.MigrationSeed;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.fail;

/**
 * The returns loop on real PostgreSQL, against runs seeded straight into the tables.
 *
 * <p>An unpaid belongs to a SUBMITTED run, and nothing can approve or submit a run while the API is
 * anonymous -- so the loop cannot be driven end to end through the application yet. Its rules are
 * proved here instead, against runs put in the state they would be in; the build does not wait, only
 * its use does.
 */
// @Isolated for the reason PiiInLogsTest gives: this class captures a Logback logger, and Spring Boot resets
// Logback whenever a context starts -- again after the test-context cache has closed one -- so a capture beside
// that reset can come back empty. Seen once in five runs of SeedStartupTest, 2026-09-30.
@Isolated
@DisplayName("The returns loop, on PostgreSQL")
class AcbReturnsServiceImplIT extends AbstractAcbIT {

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
    private static final BigDecimal RATE = new BigDecimal("174.00");

    @Autowired
    private AcbReturnsService returns;
    @Autowired
    private AcbRunService runService;
    @Autowired
    private AcbLetterRepository letters;
    @Autowired
    private AcbReturnsRepository returnsRepository;
    @Autowired
    private AcbUnpaidCodeRepository codes;
    @Autowired
    private za.co.titandynamix.sasbo_online_backend.repository.MemberStatusChangeRepository statusChangeRepository;

    private long run;
    private int member;

    /** A sent September run with one pending line for the fixture's first member. */
    private long sentLine() {
        AcbRunFixture.insert(jdbc, 15, 2);
        member = AcbRunFixture.FIRST_MEMBER_ID;
        run = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        return insertLine(run, member, null, "PENDING", null, RATE);
    }

    private void giveEmail(int memberId, String email) {
        jdbc.update("UPDATE members SET personal_email = ?, preferred_email = 'personal' WHERE id = ?", email, memberId);
    }

    @Test
    @DisplayName("recording an unpaid twice records it once; a different code for it is refused")
    void recordingTwiceRecordsOnce() {
        long line = sentLine();
        RecordUnpaidRequest stopped = new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.RE_PRESENT);

        AcbRunLineDto first = returns.recordUnpaid(run, line, stopped, "system");
        AcbRunLineDto again = returns.recordUnpaid(run, line, stopped, "system");

        assertThat(first.outcome()).isEqualTo(AcbLineOutcome.UNPAID);
        assertThat(first.unpaidCode()).isEqualTo("4");
        assertThat(first.unpaidReason()).isEqualTo(UnpaidReason.PAYMENT_STOPPED.name());
        assertThat(again).isEqualTo(first);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE line_id = ? AND kind = 'UNPAID_RECORDED'",
                Integer.class, line)).isEqualTo(1);
        assertThat(letters.findAll()).hasSize(1);

        AcbRefusal refused = refusal(() -> returns.recordUnpaid(run, line,
                new RecordUnpaidRequest("2", "2026-09-28", null), "system"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.UNPAID_ALREADY_RECORDED);
        assertThat(refused.details()).containsEntry("unpaidCode", "4");
    }

    @Test
    @DisplayName("a member with no email address: the letter waits, QUEUED with NO_EMAIL -- not failed, not dropped")
    void noEmailWaits() {
        long line = sentLine();
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("4", "2026-09-28", null), "system");

        AcbLetter letter = letters.findByLineId(line).orElseThrow();
        assertThat(letter.getStatus()).isEqualTo(LetterStatus.QUEUED);
        assertThat(letter.getStatusReason()).isEqualTo("NO_EMAIL");
        assertThat(letter.getTemplate()).isEqualTo(LetterTemplate.PAYMENT_STOPPED);
        assertThat(returns.summary(run).lettersByStatus()).containsEntry("QUEUED", 1);
    }

    @Test
    @DisplayName("a member with an email address: the letter is rendered at once, its hash and address kept -- not its body")
    void anEmailedLetterIsRendered() {
        long line = sentLine();
        giveEmail(member, "synthetic.member@example.org");
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), "system");

        AcbLetter letter = letters.findByLineId(line).orElseThrow();
        assertThat(letter.getStatus()).isEqualTo(LetterStatus.RENDERED);
        assertThat(letter.getTemplate()).isEqualTo(LetterTemplate.RE_DEBIT);
        assertThat(letter.getBodySha256()).matches("[0-9a-f]{64}");
        assertThat(letter.getAddressSnapshot()).isEqualTo("synthetic.member@example.org");
        assertThat(letter.getSentAt()).as("sending waits for the API to know who is calling").isNull();
    }

    @Test
    @DisplayName("a bank code nobody has seen is kept as OTHER with its raw value, and counted")
    void anUnknownCodeIsKept() {
        long line = sentLine();
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("99", "2026-09-28", null), "system");

        AcbReturnsSummaryDto summary = returns.summary(run);
        assertThat(summary.unrecognisedCodes()).isEqualTo(1);
        assertThat(summary.undecided()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT unpaid_code FROM acb_run_lines WHERE id = ?", String.class, line)).isEqualTo("99");
        assertThat(letters.findByLineId(line)).as("no letter is known for a code nobody has seen").isEmpty();
    }

    @Test
    @DisplayName("unpaids are recorded only against a run whose file went to the bank")
    void onlyASentRunTakesReturns() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long prepared = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        long line = insertLine(prepared, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        assertThat(refusal(() -> returns.recordUnpaid(prepared, line,
                new RecordUnpaidRequest("4", "2026-09-28", null), "system")).code())
                .isEqualTo(AcbRefusalCode.RUN_NOT_IN_STATE);
    }

    @Test
    @DisplayName("the decision decides the next run: present again is offered, suspend and undecided are not")
    void theDecisionDecidesTheNextRun() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long again = insertLine(september, m, null, "PENDING", null, RATE);
        long suspended = insertLine(september, m + 1, null, "PENDING", null, RATE);
        long undecided = insertLine(september, m + 2, null, "PENDING", null, RATE);
        returns.recordUnpaid(september, again, new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.RE_PRESENT), "system");
        returns.recordUnpaid(september, suspended, new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.SUSPEND), "system");
        returns.recordUnpaid(september, undecided, new RecordUnpaidRequest("2", "2026-09-28", null), "system");

        long october = runService.prepare("2026-10", "system").id();
        List<AcbRunLineDto> octoberLines = runService.lines(october, 0, 200).content();
        assertThat(octoberLines).filteredOn(l -> l.rePresentsLineId() != null)
                .extracting(AcbRunLineDto::rePresentsLineId).containsExactly(again);
        assertThat(returns.summary(september).undecided()).isEqualTo(1);

        // Deciding later: once, and not twice.
        returns.decide(september, undecided, UnpaidDecision.RE_PRESENT, "system");
        assertThat(refusal(() -> returns.decide(september, undecided, UnpaidDecision.SUSPEND, "system")).code())
                .isEqualTo(AcbRefusalCode.UNPAID_ALREADY_RECORDED);
    }

    @Test
    @DisplayName("the Invalid Accounts view is the run's unpaids with that reason -- a filter, not a check")
    void theInvalidAccountsView() {
        long line = sentLine();
        // A code the bank's own list would supply; added the way SASBO's answer will add it -- as data.
        jdbc.update("INSERT INTO acb_unpaid_codes (code, reason, wording, inferred, source) "
                + "VALUES ('26', 'INVALID_ACCOUNT_NUMBER', 'Invalid Account Number', TRUE, 'test')");
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("26", "2026-09-28", UnpaidDecision.SUSPEND), "system");

        assertThat(returns.unpaids(run, UnpaidReason.INVALID_ACCOUNT_NUMBER, 0, 50).content())
                .extracting(AcbRunLineDto::id).containsExactly(line);
        assertThat(returns.unpaids(run, UnpaidReason.PAYMENT_STOPPED, 0, 50).content()).isEmpty();
        assertThat(codes.count()).isEqualTo(5);
        assertThat(returns.summary(run).undecided()).isZero();
    }

    @Test
    @DisplayName("three unpaid months are reported -- and nobody's status moves")
    void theTerminationReport() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        for (int month = 7; month <= 9; month++) {
            long run = insertRun(LocalDate.of(2026, month, 1), "SUBMITTED", FROZEN, "system");
            insertLine(run, m, null, "UNPAID", null, RATE);
            insertLine(run, m + 1, null, month == 8 ? "PAID" : "UNPAID", null, RATE);
        }
        String statusBefore = jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m);

        AcbTerminationReportDto report = returns.terminationReport(0, 50);

        assertThat(report.countingFrom()).isEqualTo("2026-07");
        assertThat(report.blindness()).contains("old system");
        assertThat(report.members().content()).singleElement().satisfies(candidate -> {
            assertThat(candidate.memberId()).isEqualTo(m);
            assertThat(candidate.unpaidCount()).isEqualTo(3);
            assertThat(candidate.terminate()).isTrue();
            assertThat(candidate.unpaidMonths()).containsExactly("2026-07", "2026-08", "2026-09");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m))
                .as("a report terminates nobody").isEqualTo(statusBefore);
    }

    /** Three unpaid months for the fixture's first member, two (August, September) for the second, none for the third. */
    private int threeUnpaidMonths() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        for (int month = 7; month <= 9; month++) {
            long run = insertRun(LocalDate.of(2026, month, 1), "SUBMITTED", FROZEN, "system");
            insertLine(run, m, null, "UNPAID", null, RATE);
            if (month >= 8) {
                insertLine(run, m + 1, null, "UNPAID", null, RATE);
            }
        }
        return m;
    }

    private List<java.util.Map<String, Object>> statusChanges(int memberId) {
        return jdbc.queryForList("SELECT from_status, to_status, reason, run_id, line_id, changed_by "
                + "FROM member_status_changes WHERE member_id = ? ORDER BY id", memberId);
    }

    @Test
    @DisplayName("03.5 a member three unpaids have reached is terminated -- SUBS_IN_ARREARS, recorded -- and the report says so")
    void aDueMemberIsTerminated() {
        int m = threeUnpaidMonths();

        AcbMemberStatusDto done = returns.terminate(m, "administrator");

        assertThat(done.status()).isEqualTo("Terminated");
        assertThat(done.reason()).isEqualTo("SUBS_IN_ARREARS");
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m)).isEqualTo("Terminated");
        assertThat(statusChanges(m)).singleElement().satisfies(row -> {
            assertThat(row.get("to_status")).isEqualTo("Terminated");
            assertThat(row.get("reason")).isEqualTo("SUBS_IN_ARREARS");
            assertThat(row.get("run_id")).isNull();
            assertThat(row.get("changed_by")).isEqualTo("administrator");
        });
        assertThat(returns.terminationReport(0, 50).members().content())
                .filteredOn(c -> c.memberId() == m).singleElement()
                .satisfies(c -> assertThat(c.status()).isEqualTo("Terminated"));

        // Twice is once: the second call finds the member terminated and records nothing more.
        returns.terminate(m, "administrator");
        assertThat(statusChanges(m)).hasSize(1);
        // Q25: the count SASBO keeps -- "exactly how many members we cancelled on debit orders" -- and the history.
        assertThat(statusChangeRepository.countByReason(
                za.co.titandynamix.sasbo_online_backend.enums.MemberStatusReason.SUBS_IN_ARREARS)).isEqualTo(1);
        assertThat(statusChangeRepository.findByMemberIdOrderByChangedAtAscIdAsc(m)).singleElement()
                .satisfies(change -> assertThat(change.getFromStatus()).isEqualTo("Active"));
    }

    @Test
    @DisplayName("🔴 03.5 R3-9: termination goes through changeStatus, never update -- a member update would refuse still ends")
    void terminationNeverTakesTheWholeRecordPath() {
        int m = threeUnpaidMonths();
        // COSATU without BEC: validateStructures refuses this member on any update, over nothing to do with arrears.
        jdbc.update("UPDATE members SET is_cosatu = 'Y', is_bec = false WHERE id = ?", m);

        assertThat(returns.terminate(m, "administrator").status()).isEqualTo("Terminated");
    }

    @Test
    @DisplayName("03.5 a member the rule does not reach is refused TERMINATION_NOT_DUE, and nothing moves")
    void anUndueMemberIsNotTerminated() {
        int m = threeUnpaidMonths();

        AcbRefusal atTwo = refusal(() -> returns.terminate(m + 1, "administrator"));
        assertThat(atTwo.code()).isEqualTo(AcbRefusalCode.TERMINATION_NOT_DUE);
        assertThat(atTwo.details()).as("listed at two, one short").containsEntry("unpaidCount", "2");

        AcbRefusal atNone = refusal(() -> returns.terminate(m + 2, "administrator"));
        assertThat(atNone.code()).isEqualTo(AcbRefusalCode.TERMINATION_NOT_DUE);
        assertThat(atNone.details()).containsEntry("unpaidCount", "fewer than two");

        assertThat(statusChanges(m + 1)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m + 1)).isEqualTo("Active");
    }

    @Test
    @DisplayName("03.5 an unpaid decided SUSPEND suspends its member -- UNPAID_SUSPENDED, with its run and line")
    void aSuspendDecisionSuspendsTheMember() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long suspended = insertLine(september, m, null, "UNPAID", "SUSPEND", RATE);
        long again = insertLine(september, m + 1, null, "UNPAID", "RE_PRESENT", RATE);
        long undecided = insertLine(september, m + 2, null, "UNPAID", null, RATE);

        AcbMemberStatusDto done = returns.suspendMember(september, suspended, "administrator");

        assertThat(done.status()).isEqualTo("Suspended");
        assertThat(statusChanges(m)).singleElement().satisfies(row -> {
            assertThat(row.get("reason")).isEqualTo("UNPAID_SUSPENDED");
            assertThat(((Number) row.get("run_id")).longValue()).isEqualTo(september);
            assertThat(((Number) row.get("line_id")).longValue()).isEqualTo(suspended);
        });
        assertThat(refusal(() -> returns.suspendMember(september, again, "administrator")).code())
                .isEqualTo(AcbRefusalCode.UNPAID_NOT_SUSPENDED);
        assertThat(refusal(() -> returns.suspendMember(september, undecided, "administrator")).code())
                .isEqualTo(AcbRefusalCode.UNPAID_NOT_SUSPENDED);
        assertThat(statusChanges(m + 1)).isEmpty();
    }

    @Test
    @DisplayName("03.5 a terminated member is not suspended -- MEMBER_TERMINATED")
    void aTerminatedMemberIsNotSuspended() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        jdbc.update("UPDATE members SET status = 'Terminated' WHERE id = ?", m);
        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long suspended = insertLine(september, m, null, "UNPAID", "SUSPEND", RATE);

        assertThat(refusal(() -> returns.suspendMember(september, suspended, "administrator")).code())
                .isEqualTo(AcbRefusalCode.MEMBER_TERMINATED);
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m)).isEqualTo("Terminated");
    }

    @Test
    @DisplayName("🔒 Q27: a debit, the next month's debit and the re-debit of the first are three -- inside two months")
    void threeInsideTwoMonths() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        long july = insertRun(LocalDate.of(2026, 7, 1), "SUBMITTED", FROZEN, "system");
        long julyDebit = insertLine(july, m, null, "UNPAID", "RE_PRESENT", RATE);
        long august = insertRun(LocalDate.of(2026, 8, 1), "SUBMITTED", FROZEN, "system");
        insertLine(august, m, null, "UNPAID", null, RATE);
        insertLine(august, m, julyDebit, "UNPAID", null, RATE);
        // The must-pass twin: the same two months with the re-debit paid -- not reported.
        long twinJuly = insertLine(july, m + 1, null, "UNPAID", "RE_PRESENT", RATE);
        insertLine(august, m + 1, null, "UNPAID", null, RATE);
        insertLine(august, m + 1, twinJuly, "PAID", null, RATE);

        AcbTerminationReportDto report = returns.terminationReport(0, 50);

        assertThat(report.members().content()).singleElement().satisfies(candidate -> {
            assertThat(candidate.memberId()).isEqualTo(m);
            assertThat(candidate.unpaidCount()).isEqualTo(3);
            assertThat(candidate.unpaidMonths()).containsExactly("2026-07", "2026-08", "2026-08");
        });
    }

    @Test
    @DisplayName("🔒 SD3-K: a member at two is listed, not terminated -- and the twelve-month total counts across a reset (Q49)")
    void atTwoAndTheTwelveMonthTotal() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int atTwo = AcbRunFixture.FIRST_MEMBER_ID;
        int reset = AcbRunFixture.FIRST_MEMBER_ID + 1;
        String[][] outcomes = {
                // month, member at two, member with a reset in between
                {"2026-01", null, "UNPAID"}, {"2026-02", null, "UNPAID"}, {"2026-03", null, "PAID"},
                {"2026-07", "UNPAID", "UNPAID"}, {"2026-08", "UNPAID", "UNPAID"}};
        for (String[] month : outcomes) {
            long run = insertRun(java.time.YearMonth.parse(month[0]).atDay(1), "CLOSED", FROZEN, "system");
            if (month[1] != null) {
                insertLine(run, atTwo, null, month[1], null, RATE);
            }
            insertLine(run, reset, null, month[2], null, RATE);
        }
        AcbTerminationReportDto report = returns.terminationReport(0, 50);
        assertThat(report.members().totalElements()).isEqualTo(2);
        assertThat(report.members().content()).allSatisfy(c -> {
            assertThat(c.unpaidCount()).isEqualTo(2);
            assertThat(c.terminate()).as("two is not three").isFalse();
        });
        assertThat(report.members().content()).filteredOn(c -> c.memberId() == reset).singleElement()
                .satisfies(c -> assertThat(c.twelveMonthTotal()).as("January and February count in the total").isEqualTo(4));
    }

    @Test
    @DisplayName("🔒 R3-12's report half: one opening unpaid and two new ones make three -- the old system's arrears are counted")
    void theOpeningPositionIsCounted() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        jdbc.update("INSERT INTO acb_opening_position (member_id, membership_number, unpaids_standing, last_unpaid_month, "
                        + "source, entered_by, entered_at) VALUES (?, ?, 1, DATE '2026-06-01', 'legacy profile', 'admin-a', now())",
                m, AcbRunFixture.membershipNumber(m));
        for (int month = 7; month <= 8; month++) {
            long run = insertRun(LocalDate.of(2026, month, 1), "CLOSED", FROZEN, "system");
            insertLine(run, m, null, "UNPAID", null, RATE);
        }
        AcbTerminationReportDto report = returns.terminationReport(0, 50);
        assertThat(report.members().content()).singleElement().satisfies(c -> {
            assertThat(c.unpaidCount()).isEqualTo(3);
            assertThat(c.terminate()).isTrue();
            assertThat(c.openingUnpaids()).isEqualTo(1);
            assertThat(c.unpaidMonths()).containsExactly("2026-06", "2026-07", "2026-08");
        });
        assertThat(report.blindness()).contains("opening arrears position").contains("1 member(s)");
    }

    @Test
    @DisplayName("🔒 R3-17's edges: a member whose unpaids are all older than thirteen months is absent; the twin just inside is present")
    void theWindowsEdges() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int outside = AcbRunFixture.FIRST_MEMBER_ID;
        int inside = AcbRunFixture.FIRST_MEMBER_ID + 1;
        // The frozen clock's month is September 2026: the window is September 2025 to September 2026.
        long july2025 = insertRun(LocalDate.of(2025, 7, 1), "CLOSED", FROZEN, "system");
        long august2025 = insertRun(LocalDate.of(2025, 8, 1), "CLOSED", FROZEN, "system");
        long september2025 = insertRun(LocalDate.of(2025, 9, 1), "CLOSED", FROZEN, "system");
        long october2025 = insertRun(LocalDate.of(2025, 10, 1), "CLOSED", FROZEN, "system");
        insertLine(july2025, outside, null, "UNPAID", null, RATE);
        insertLine(august2025, outside, null, "UNPAID", null, RATE);
        insertLine(september2025, inside, null, "UNPAID", null, RATE);
        insertLine(october2025, inside, null, "UNPAID", null, RATE);
        assertThat(returns.terminationReport(0, 50).members().content())
                .extracting(AcbTerminationReportDto.Candidate::memberId).containsExactly(inside);
    }

    // ------------------------------------------------------------------------------------------
    // Taking the returns in, and closing the month (D28)
    // ------------------------------------------------------------------------------------------

    private String outcome(long line) {
        return jdbc.queryForObject("SELECT outcome FROM acb_run_lines WHERE id = ?", String.class, line);
    }

    private int version(long runId) {
        return runService.get(runId).version();
    }

    @Test
    @DisplayName("taking the returns in marks every debit the report did not list PAID, and leaves the unpaids")
    void takingTheReturnsInMarksTheRestPaid() {
        long unpaid = sentLine();
        long paid = insertLine(run, member + 1, null, "PENDING", null, RATE);
        long alsoPaid = insertLine(run, member + 2, null, "PENDING", null, RATE);
        returns.recordUnpaid(run, unpaid, new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.SUSPEND), "system");

        assertThat(runService.recordReturns(run, version(run), "system", null).status()).isEqualTo(AcbRunStatus.RETURNED);

        assertThat(outcome(unpaid)).isEqualTo("UNPAID");
        assertThat(outcome(paid)).isEqualTo("PAID");
        assertThat(outcome(alsoPaid)).isEqualTo("PAID");
        assertThat(returns.summary(run).linesByOutcome()).containsEntry("PAID", 2).containsEntry("UNPAID", 1)
                .doesNotContainKey("PENDING");
        assertThat(returnsRepository.countByOutcome(run)).as("the GROUP BY on PostgreSQL, not H2")
                .extracting(row -> row[0].toString() + "=" + row[1]).containsExactlyInAnyOrder("PAID=2", "UNPAID=1");
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE run_id = ? AND kind = 'LINES_PAID'",
                String.class, run)).startsWith("2 ");
    }

    @Test
    @DisplayName("only a submitted run takes its returns in; a prepared one is refused and nothing is marked paid")
    void onlyASubmittedRunTakesItsReturnsIn() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long prepared = insertRun(SEPTEMBER, "PREPARED", FROZEN, "system");
        long line = insertLine(prepared, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);

        assertThat(refusal(() -> runService.recordReturns(prepared, version(prepared), "system", null)).code())
                .isEqualTo(AcbRefusalCode.RUN_NOT_IN_STATE);
        assertThat(outcome(line)).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("a return that arrives after the returns were taken in overturns the presumed payment, and says so")
    void aLateReturnOverturnsAPresumedPayment() {
        long line = sentLine();
        runService.recordReturns(run, version(run), "system", null);
        assertThat(outcome(line)).isEqualTo("PAID");

        AcbRunLineDto late = returns.recordUnpaid(run, line,
                new RecordUnpaidRequest("2", "2026-10-02", UnpaidDecision.RE_PRESENT), "system");

        assertThat(late.outcome()).isEqualTo(AcbLineOutcome.UNPAID);
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE line_id = ? AND kind = 'UNPAID_RECORDED'",
                String.class, line)).contains("late return");
        assertThat(letters.findByLineId(line)).as("the late unpaid gets its letter like any other").isPresent();
    }

    @Test
    @DisplayName("a month does not close while an unpaid in it is undecided; decided, it closes and takes no more returns")
    void aMonthClosesOnlyWhenEveryUnpaidIsDecided() {
        long line = sentLine();
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("4", "2026-09-28", null), "system");
        runService.recordReturns(run, version(run), "system", null);

        AcbRefusal refused = refusal(() -> runService.close(run, version(run), "system", null));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.UNPAIDS_UNDECIDED);
        assertThat(refused.details()).containsEntry("undecided", "1").containsEntry("status", "RETURNED");
        assertThat(runService.get(run).status()).isEqualTo(AcbRunStatus.RETURNED);

        returns.decide(run, line, UnpaidDecision.SUSPEND, "system");
        assertThat(runService.close(run, version(run), "system", "September done").status()).isEqualTo(AcbRunStatus.CLOSED);

        long other = insertLine(run, member + 1, null, "PAID", null, RATE);
        assertThat(refusal(() -> returns.recordUnpaid(run, other,
                new RecordUnpaidRequest("4", "2026-10-05", null), "system")).code())
                .as("a closed month takes no more returns").isEqualTo(AcbRefusalCode.RUN_NOT_IN_STATE);
    }

    private static AcbRefusal refusal(Runnable call) {
        try {
            call.run();
        } catch (AcbRefusal refusal) {
            return refusal;
        }
        return fail("expected an AcbRefusal and the call returned normally");
    }

    // ------------------------------------------------------------------------------------------
    // 03.2's new box: the code's re-debit flag, and the member's permission (Q40, Q44, D32)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("🔬 F50: V45's codes ARE MigrationSeed.ACB_UNPAID_CODES -- each meaning and its re-debit flag, every flag inferred")
    void theCodesAreTheOneList() {
        List<MigrationSeed.UnpaidCode> migrated = jdbc.query(
                "SELECT code, reason, re_debit_allowed, re_debit_inferred FROM acb_unpaid_codes ORDER BY length(code), code",
                (rs, row) -> {
                    assertThat(rs.getBoolean("re_debit_inferred")).as("code %s marked inferred", rs.getString("code")).isTrue();
                    Object allowed = rs.getObject("re_debit_allowed");
                    return new MigrationSeed.UnpaidCode(rs.getString("code"), rs.getString("reason"), (Boolean) allowed);
                });
        assertThat(migrated).containsExactlyElementsOf(MigrationSeed.ACB_UNPAID_CODES);
    }

    @Test
    @DisplayName("🔒 R3-14: an undecided unpaid carries the decision its code suggests -- present again, suspend, or none")
    void theCodeSuggestsTheDecision() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long notProvided = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        long stopped = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID + 1, null, "PENDING", null, RATE);
        long unknown = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID + 2, null, "PENDING", null, RATE);

        assertThat(returns.recordUnpaid(run, notProvided, new RecordUnpaidRequest("2", "2026-09-28", null), "system")
                .suggestedDecision()).isEqualTo(UnpaidDecision.RE_PRESENT);
        assertThat(returns.recordUnpaid(run, stopped, new RecordUnpaidRequest("4", "2026-09-28", null), "system")
                .suggestedDecision()).isEqualTo(UnpaidDecision.SUSPEND);
        assertThat(returns.recordUnpaid(run, unknown, new RecordUnpaidRequest("30", "2026-09-28", null), "system")
                .suggestedDecision()).isNull();
        // A suggestion, never a decision: all three are undecided until the administrator decides.
        assertThat(returns.summary(run).undecided()).isEqualTo(3);
        assertThat(returns.unpaids(run, null, 0, 50).content())
                .extracting(AcbRunLineDto::suggestedDecision).containsExactlyInAnyOrder(UnpaidDecision.RE_PRESENT,
                        UnpaidDecision.SUSPEND, null);
        // 04.1: where each line's letter is -- a known code's waits for an address; an unknown code has none.
        assertThat(returns.unpaids(run, null, 0, 50).content())
                .extracting(AcbRunLineDto::letterStatus).containsExactlyInAnyOrder(LetterStatus.QUEUED,
                        LetterStatus.QUEUED, null);
    }

    @Test
    @DisplayName("🔒 R3-14's twin: the administrator's own choice wins over the code's suggestion")
    void theAdministratorsChoiceWins() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        AcbRunLineDto recorded = returns.recordUnpaid(run, line,
                new RecordUnpaidRequest("2", "2026-09-28", UnpaidDecision.SUSPEND), "system");
        assertThat(recorded.decision()).isEqualTo(UnpaidDecision.SUSPEND);
        assertThat(recorded.suggestedDecision()).as("decided: nothing left to suggest").isNull();
    }

    @Test
    @DisplayName("🔒 D32: a permission without a note is refused PERMISSION_NOTE_REQUIRED, naming the line")
    void aPermissionNeedsItsNote() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 8, 1), "CLOSED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "UNPAID", "RE_PRESENT", RATE);
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, () -> returns.permit(run, line, "  ", "system"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.PERMISSION_NOTE_REQUIRED);
        assertThat(refused.details()).containsEntry("lineId", String.valueOf(line));
        assertThat(catchThrowableOfType(AcbRefusal.class, () -> returns.permit(run, line, "x".repeat(301), "system")).code())
                .as("capped, never cut").isEqualTo(AcbRefusalCode.REQUEST_MALFORMED);
    }

    @Test
    @DisplayName("🔒 SD3-D: the permission note is kept on its line alone -- never in a log line, never in an audit row")
    void thePermissionNoteGoesNowhereElse() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 8, 1), "CLOSED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "UNPAID", null, RATE);
        String note = "Agreed by phone with the Synthetic Member on 000 000 0000, 18 September";
        try (LogCapture service = LogCapture.on(AcbReturnsServiceImpl.class);
             LogCapture trail = LogCapture.on(AcbAuditTrail.class)) {
            AcbRunLineDto permitted = returns.permit(run, line, note, "system");
            assertThat(permitted.decision()).as("permission decides it present-again").isEqualTo(UnpaidDecision.RE_PRESENT);
            assertThat(permitted.permissionNote()).isEqualTo(note);
            assertThat(service.rendered()).doesNotContain("Synthetic Member").doesNotContain("000 000 0000");
            assertThat(trail.rendered()).doesNotContain("Synthetic Member");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE reason LIKE '%Synthetic Member%'", Long.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events WHERE kind = 'PERMISSION_RECORDED'", Long.class))
                .isEqualTo(1L);
    }

    // ------------------------------------------------------------------------------------------
    // 03.3's held letters: the worklist, marking handled, one validated recipient (Q46, SD3-C)
    // ------------------------------------------------------------------------------------------

    @Test
    @DisplayName("🔒 R3-13: a NO_EMAIL letter is on the worklist; once the member has an address it can be sent; marked handled it leaves, audited without its note, and is never held again")
    void theHeldLetterWorklist() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.SUSPEND), "system");

        List<AcbHeldLetterDto> held = returns.heldLetters(run, 0, 50).content();
        assertThat(held).singleElement().satisfies(letter -> {
            assertThat(letter.status()).isEqualTo(LetterStatus.QUEUED);
            assertThat(letter.reason()).isEqualTo("NO_EMAIL");
            assertThat(letter.canSend()).isFalse();
        });
        jdbc.update("UPDATE members SET personal_email = 'member@example.org' WHERE id = ?", AcbRunFixture.FIRST_MEMBER_ID);
        assertThat(returns.heldLetters(run, 0, 50).content()).singleElement()
                .satisfies(letter -> assertThat(letter.canSend()).as("an address now, so it can go -- once B1 lands").isTrue());

        long letterId = held.get(0).letterId();
        String note = "Handed to the Synthetic Member at the branch on 000 000 0000";
        returns.markHandled(run, letterId, note, "admin-a");
        assertThat(returns.heldLetters(run, 0, 50).content()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT status, handled_note FROM acb_letters WHERE id = ?", letterId))
                .containsEntry("status", "HANDLED").containsEntry("handled_note", note);
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE kind = 'LETTER_HANDLED'", String.class))
                .isEqualTo("a held letter marked handled");
        AcbRefusal again = catchThrowableOfType(AcbRefusal.class, () -> returns.markHandled(run, letterId, "twice", "admin-a"));
        assertThat(again.code()).isEqualTo(AcbRefusalCode.LETTER_NOT_HELD);
        assertThat(again.details()).containsEntry("status", "HANDLED");
    }

    @Test
    @DisplayName("Q46: a held letter is sent once the member has an address -- rendered then, by whoever sends it -- never to no address, and never twice")
    void aHeldLetterIsSentOnceThereIsAnAddress() {
        AcbRunFixture.insert(jdbc, 15, 2);
        long run = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.SUSPEND), "system");
        long letterId = returns.heldLetters(run, 0, 50).content().get(0).letterId();

        AcbRefusal noAddress = catchThrowableOfType(AcbRefusal.class, () -> returns.sendHeldLetter(run, letterId, "admin-a"));
        assertThat(noAddress.code()).isEqualTo(AcbRefusalCode.LETTER_NO_ADDRESS);
        assertThat(noAddress.details()).containsEntry("letterId", String.valueOf(letterId));
        assertThat(jdbc.queryForObject("SELECT status FROM acb_letters WHERE id = ?", String.class, letterId))
                .as("nothing moved").isEqualTo("QUEUED");

        jdbc.update("UPDATE members SET personal_email = 'member@example.org' WHERE id = ?", AcbRunFixture.FIRST_MEMBER_ID);
        returns.sendHeldLetter(run, letterId, "admin-a");

        // Sending is switched off in this context -- the default until the letters review -- so it waits RENDERED.
        assertThat(jdbc.queryForMap("SELECT status, status_reason, queued_by, address_snapshot, "
                + "body_sha256 IS NOT NULL AS hashed FROM acb_letters WHERE id = ?", letterId))
                .containsEntry("status", "RENDERED").containsEntry("status_reason", null)
                .containsEntry("queued_by", "admin-a").containsEntry("address_snapshot", "member@example.org")
                .containsEntry("hashed", true);
        assertThat(returns.heldLetters(run, 0, 50).content()).as("it left the worklist").isEmpty();
        assertThat(jdbc.queryForObject("SELECT reason FROM acb_audit_events WHERE kind = 'LETTER_SENT_AGAIN'", String.class))
                .isEqualTo("a queued letter sent again");

        AcbRefusal twice = catchThrowableOfType(AcbRefusal.class, () -> returns.sendHeldLetter(run, letterId, "admin-a"));
        assertThat(twice.code()).isEqualTo(AcbRefusalCode.LETTER_NOT_HELD);
        assertThat(twice.details()).containsEntry("status", "RENDERED");
    }

    @Test
    @DisplayName("🔒 SD3-C: an address that could carry a header or a second recipient holds the letter INVALID_ADDRESS -- never sent to")
    void anUnsafeAddressHoldsTheLetter() {
        AcbRunFixture.insert(jdbc, 15, 2);
        jdbc.update("UPDATE members SET personal_email = ? WHERE id = ?",
                "member@example.org\r\nBcc: other@example.org", AcbRunFixture.FIRST_MEMBER_ID);
        long run = insertRun(LocalDate.of(2026, 9, 1), "SUBMITTED", FROZEN, "system");
        long line = insertLine(run, AcbRunFixture.FIRST_MEMBER_ID, null, "PENDING", null, RATE);
        returns.recordUnpaid(run, line, new RecordUnpaidRequest("4", "2026-09-28", UnpaidDecision.SUSPEND), "system");
        assertThat(jdbc.queryForMap("SELECT status, status_reason, address_snapshot FROM acb_letters"))
                .containsEntry("status", "QUEUED").containsEntry("status_reason", "INVALID_ADDRESS")
                .containsEntry("address_snapshot", null);
        assertThat(returns.heldLetters(run, 0, 50).content()).singleElement()
                .satisfies(letter -> assertThat(letter.canSend()).isFalse());
    }
}

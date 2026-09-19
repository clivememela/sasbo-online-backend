package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbReturnsSummaryDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbRunLineDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbLetter;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The returns loop on real PostgreSQL, against runs seeded straight into the tables.
 *
 * <p>An unpaid belongs to a SUBMITTED run, and nothing can approve or submit a run while the API is
 * anonymous -- so the loop cannot be driven end to end through the application yet. Its rules are
 * proved here instead, against runs put in the state they would be in; the build does not wait, only
 * its use does.
 */
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
        assertThat(letter.getTemplate()).isEqualTo(LetterTemplate.RETURN_DEBIT);
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
        assertThat(returnsRepository.countUndecided(run, AcbLineOutcome.UNPAID)).isZero();
    }

    @Test
    @DisplayName("three unpaid months are reported under the first reading, not the second -- and nobody's status moves")
    void theTerminationReport() {
        AcbRunFixture.insert(jdbc, 15, 2);
        int m = AcbRunFixture.FIRST_MEMBER_ID;
        for (int month = 7; month <= 9; month++) {
            long run = insertRun(LocalDate.of(2026, month, 1), "SUBMITTED", FROZEN, "system");
            insertLine(run, m, null, "UNPAID", null, RATE);
            insertLine(run, m + 1, null, month == 8 ? "PAID" : "UNPAID", null, RATE);
        }
        String statusBefore = jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m);

        AcbTerminationReportDto report = returns.terminationReport();

        assertThat(report.countingFrom()).isEqualTo("2026-07");
        assertThat(report.blindness()).contains("old system");
        assertThat(report.members()).singleElement().satisfies(candidate -> {
            assertThat(candidate.memberId()).isEqualTo(m);
            assertThat(candidate.everyUnpaidCounts()).isTrue();
            assertThat(candidate.everyUnpaidCount()).isEqualTo(3);
            assertThat(candidate.retriesOnly()).as("none of the three was a retry").isFalse();
            assertThat(candidate.unpaidMonths()).containsExactly("2026-07", "2026-08", "2026-09");
        });
        assertThat(jdbc.queryForObject("SELECT status FROM members WHERE id = ?", String.class, m))
                .as("a report terminates nobody").isEqualTo(statusBefore);
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

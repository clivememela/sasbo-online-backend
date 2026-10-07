package za.co.titandynamix.sasbo_online_backend.services.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbPage;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbTerminationReportDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.AcbUnpaidCountsResultDto;
import za.co.titandynamix.sasbo_online_backend.dto.acb.RecordUnpaidRequest;
import za.co.titandynamix.sasbo_online_backend.dto.acb.UnpaidCountsRequest;
import za.co.titandynamix.sasbo_online_backend.entity.acb.AcbUnpaidCount;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusal;
import za.co.titandynamix.sasbo_online_backend.exception.AcbRefusalCode;
import za.co.titandynamix.sasbo_online_backend.fixtures.AcbRunFixture;
import za.co.titandynamix.sasbo_online_backend.repository.AcbUnpaidCountRepository;
import za.co.titandynamix.sasbo_online_backend.services.AcbReturnsService;
import za.co.titandynamix.sasbo_online_backend.services.AcbUnpaidCountService;
import za.co.titandynamix.sasbo_online_backend.support.AbstractAcbIT;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Members' unpaid counts captured by hand, on PostgreSQL (Stephne, DOD-28: "Unpaids for re-debits are captured manually
 * a day before we run ACB"; usable in any month, since whether it is once at switch-over or every month is asked). A
 * count is the member's as at the day it is captured: it replaces their debits before that day, never those after.
 */
@DisplayName("AcbUnpaidCountService -- the counts captured by hand (DOD-28)")
class AcbUnpaidCountServiceImplIT extends AbstractAcbIT {

    private static final BigDecimal RATE = new BigDecimal("174.00");
    private static final LocalDate JULY = LocalDate.of(2026, 7, 1);
    private static final LocalDate AUGUST = LocalDate.of(2026, 8, 1);
    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 1);
    /** The frozen clock's day: what every capture here is as at. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 18);

    @Autowired
    private AcbUnpaidCountService counts;
    @Autowired
    private AcbUnpaidCountRepository captured;
    @Autowired
    private AcbReturnsService returns;
    @Autowired
    private AcbUnpaidCounter counter;

    private static AcbRefusal refusal(Runnable call) {
        AcbRefusal refused = catchThrowableOfType(AcbRefusal.class, call::run);
        assertThat(refused).as("refused").isNotNull();
        return refused;
    }

    private int fixture() {
        AcbRunFixture.insert(jdbc, 15, 2).assertSize();
        return AcbRunFixture.FIRST_MEMBER_ID;
    }

    private long unpaidLine(long runId, int memberId, String code, String decision) {
        long line = insertLine(runId, memberId, null, "UNPAID", decision, RATE);
        jdbc.update("UPDATE acb_run_lines SET unpaid_code = ?, unpaid_reason = 'NOT_PROVIDED_FOR' WHERE id = ?", code,
                line);
        return line;
    }

    @Test
    @DisplayName("Stephne, DOD-28: counts captured by hand -- on the report at once, the same list twice changes nothing, "
            + "and a list with problems is refused by row with nothing written")
    void countsCapturedByHand() {
        int m = fixture();
        String first = AcbRunFixture.membershipNumber(m);
        String second = AcbRunFixture.membershipNumber(m + 1);
        UnpaidCountsRequest list = new UnpaidCountsRequest("Re-debit list, September", List.of(
                new UnpaidCountsRequest.Entry(first, 2), new UnpaidCountsRequest.Entry(second, 3)));

        AcbUnpaidCountsResultDto result = counts.capture(list, "administrator");
        assertThat(result.captured()).isEqualTo(2);
        assertThat(result.atThree()).isEqualTo(1);
        assertThat(result.countedOn()).isEqualTo(TODAY.toString());
        assertThat(captured.findByMemberId(m + 1)).get().satisfies(row -> {
            assertThat(row.getUnpaidCount()).isEqualTo(3);
            assertThat(row.getCountedOn()).isEqualTo(TODAY);
            assertThat(row.getMembershipNumber()).isEqualTo(second);
        });
        assertThat(captured.findByCountedOnGreaterThanEqual(TODAY)).hasSize(2);
        assertThat(captured.findByCountedOnGreaterThanEqual(TODAY.plusDays(1))).as("the window's edge").isEmpty();
        AcbTerminationReportDto report = returns.terminationReport(0, 50);
        assertThat(report.members().content()).extracting(AcbTerminationReportDto.Candidate::memberId,
                        AcbTerminationReportDto.Candidate::unpaidCount, AcbTerminationReportDto.Candidate::terminate)
                .containsExactlyInAnyOrder(tuple(m + 1, 3, true), tuple(m, 2, false));

        AcbUnpaidCountsResultDto again = counts.capture(list, "administrator");
        assertThat(again.captured()).isZero();
        assertThat(again.unchanged()).isEqualTo(2);

        AcbRefusal refused = refusal(() -> counts.capture(new UnpaidCountsRequest("Re-debit list", List.of(
                new UnpaidCountsRequest.Entry("F-999999", 1),
                new UnpaidCountsRequest.Entry(first, 4),
                new UnpaidCountsRequest.Entry(second, 1),
                new UnpaidCountsRequest.Entry(second, 1))), "administrator"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.UNPAID_COUNTS_INVALID);
        assertThat(refused.details().get("problems")).isEqualTo("2");
        assertThat(String.join(" ", refused.details().values())).as("a problem never echoes what was typed")
                .doesNotContain("F-999999");
        assertThat(jdbc.queryForObject("SELECT unpaid_count FROM acb_unpaid_counts WHERE member_id = ?", Integer.class,
                m + 1)).as("nothing of the refused list was written").isEqualTo(3);
    }

    @Test
    @DisplayName("a number nobody holds is refused by its row once every row is well formed -- and nothing is written")
    void aNumberNobodyHoldsIsRefused() {
        int m = fixture();
        AcbRefusal refused = refusal(() -> counts.capture(new UnpaidCountsRequest("Re-debit list", List.of(
                new UnpaidCountsRequest.Entry(AcbRunFixture.membershipNumber(m), 1),
                new UnpaidCountsRequest.Entry("F-999999", 1))), "administrator"));
        assertThat(refused.code()).isEqualTo(AcbRefusalCode.UNPAID_COUNTS_INVALID);
        assertThat(refused.details()).containsEntry("problems", "1");
        assertThat(refused.details().get("problem.1")).contains("row 2").contains("no member");
        assertThat(captured.findByMemberIdIn(List.of(m))).as("all or nothing").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM acb_audit_events", Long.class)).isZero();
    }

    @Test
    @DisplayName("Stephne, DOD-28: a count is as at its day -- July's and August's unpaids captured as nought count "
            + "nothing, and a debit returned after the capture counts one")
    void aCountReplacesTheDebitsBeforeItsDay() {
        int m = fixture();
        unpaidLine(insertRun(JULY, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        unpaidLine(insertRun(AUGUST, "CLOSED", FROZEN, "system"), m, "2", "RE_PRESENT");
        assertThat(counter.of(m).count().unpaids()).as("before the capture").isEqualTo(2);

        counts.capture(new UnpaidCountsRequest("Re-debit list, September", List.of(
                new UnpaidCountsRequest.Entry(AcbRunFixture.membershipNumber(m), 0))), "administrator");
        assertThat(counter.of(m).count().unpaids()).as("the list says nought as at today").isZero();
        assertThat(counter.of(m).capturedUnpaids()).isZero();

        long september = insertRun(SEPTEMBER, "SUBMITTED", FROZEN, "system");
        long line = insertLine(september, m, null, "PENDING", null, RATE);
        returns.recordUnpaid(september, line, new RecordUnpaidRequest("2", "2026-09-28", null), "administrator");
        assertThat(counter.of(m).count().unpaids()).as("September's debit, on the 25th, is after the count's day")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a count captured again with a different number replaces the first; the list reads by membership number, "
            + "and each audit row says how many -- never whose")
    void aCountCapturedAgainReplacesTheFirst() {
        int m = fixture();
        String number = AcbRunFixture.membershipNumber(m);
        counts.capture(new UnpaidCountsRequest("Re-debit list", List.of(
                new UnpaidCountsRequest.Entry(number, 1),
                new UnpaidCountsRequest.Entry(AcbRunFixture.membershipNumber(m + 2), 0))), "administrator");
        AcbUnpaidCountsResultDto corrected = counts.capture(new UnpaidCountsRequest("Re-debit list, corrected",
                List.of(new UnpaidCountsRequest.Entry(number, 3))), "cfo");
        assertThat(corrected.captured()).isEqualTo(1);
        assertThat(corrected.atThree()).isEqualTo(1);
        assertThat(captured.findByMemberId(m)).get().extracting(AcbUnpaidCount::getUnpaidCount, AcbUnpaidCount::getSource,
                AcbUnpaidCount::getEnteredBy).containsExactly(3, "Re-debit list, corrected", "cfo");

        AcbPage<AcbUnpaidCountDto> page = counts.list(0, 50);
        assertThat(page.content()).extracting(AcbUnpaidCountDto::membershipNumber)
                .containsExactly(number, AcbRunFixture.membershipNumber(m + 2));
        assertThat(page.content().get(0)).satisfies(row -> {
            assertThat(row.surname()).isEqualTo("Surname" + m);
            assertThat(row.unpaidCount()).isEqualTo(3);
            assertThat(row.countedOn()).isEqualTo(TODAY.toString());
        });
        List<String> reasons = jdbc.queryForList("SELECT reason FROM acb_audit_events WHERE kind = "
                + "'UNPAID_COUNTS_CAPTURED' ORDER BY id", String.class);
        assertThat(reasons).hasSize(2);
        assertThat(reasons.get(1)).contains("1 member(s)").contains("1 at three");
        assertThat(String.join(" ", reasons)).doesNotContain(number).doesNotContain("Surname").doesNotContain("corrected");
    }
}

package za.co.titandynamix.sasbo_online_backend.acb.returns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A member's unpaid count -- one rule the termination report, the refusal of a re-debit at three and the run's
 * exclusion at three all ask -- one test per answer of Stephne's it encodes.
 */
@DisplayName("The unpaid count (Stephne, DOD-03, DOD-20, DOD-27, DOD-28)")
class UnpaidCountingTest {

    private static long ids = 100;

    private static TerminationRule.Presentation unpaid(String day) {
        return new TerminationRule.Presentation(ids++, LocalDate.parse(day), AcbLineOutcome.UNPAID);
    }

    private static TerminationRule.Presentation paid(String day) {
        return new TerminationRule.Presentation(ids++, LocalDate.parse(day), AcbLineOutcome.PAID);
    }

    private static UnpaidCounting.Count count(List<TerminationRule.Presentation> history) {
        return UnpaidCounting.count(history, null, null, null);
    }

    @Test
    @DisplayName("Stephne, DOD-20: \"It starts again at nought\" -- after a debit that is paid, the count starts again")
    void aPaidDebitStartsTheCountAgain() {
        assertThat(count(List.of(unpaid("2026-07-25"), unpaid("2026-08-25"), paid("2026-09-25"))).unpaids()).isZero();
        assertThat(count(List.of(unpaid("2026-07-25"), paid("2026-08-25"), unpaid("2026-09-25"))).unpaids()).isEqualTo(1);
        assertThat(count(List.of(unpaid("2026-07-25"), unpaid("2026-08-25"), unpaid("2026-09-25"))).unpaids())
                .as("the twin: no payment, no reset").isEqualTo(3);
    }

    @Test
    @DisplayName("Stephne, DOD-03: at three the member is at the limit, and the day it reached three is known")
    void threeIsTheLimit() {
        TerminationRule.Presentation third = unpaid("2026-09-25");
        UnpaidCounting.Count count = count(List.of(unpaid("2026-07-25"), unpaid("2026-08-25"), third));
        assertThat(count.atTheLimit()).isTrue();
        assertThat(count.thirdUnpaidOn()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(count.lastUnpaidLineId()).as("where the ending's letter hangs (DOD-24)").isEqualTo(third.lineId());
        assertThat(count(List.of(unpaid("2026-08-25"), unpaid("2026-09-25"))).atTheLimit()).as("two is not three").isFalse();
    }

    @Test
    @DisplayName("Stephne, DOD-28: a count captured by hand replaces the member's debits before the day it was captured; "
            + "later ones count on from it")
    void aCapturedCountReplacesWhatCameBefore() {
        List<TerminationRule.Presentation> history = new ArrayList<>(List.of(unpaid("2026-07-25"), unpaid("2026-08-25")));
        UnpaidCounting.Captured captured = new UnpaidCounting.Captured(1, LocalDate.of(2026, 9, 14));
        assertThat(UnpaidCounting.count(history, null, captured, null).unpaids()).isEqualTo(1);
        history.add(unpaid("2026-09-25"));
        UnpaidCounting.Count after = UnpaidCounting.count(history, null, captured, null);
        assertThat(after.unpaids()).isEqualTo(2);
        assertThat(after.lastUnpaidLineId()).as("the last unpaid counted is this system's own line, never a captured one")
                .isPositive();
    }

    @Test
    @DisplayName("Stephne, DOD-28: a captured nought clears a member -- three before it count for nothing")
    void aCapturedNoughtClears() {
        List<TerminationRule.Presentation> history = List.of(unpaid("2026-06-25"), unpaid("2026-07-25"),
                unpaid("2026-08-25"));
        UnpaidCounting.Count count = UnpaidCounting.count(history, null, new UnpaidCounting.Captured(0,
                LocalDate.of(2026, 9, 1)), null);
        assertThat(count.unpaids()).isZero();
        assertThat(count.lastUnpaidLineId()).isNull();
    }

    @Test
    @DisplayName("Stephne, DOD-27: \"This member will now have a new joined date\" -- nothing before the new join date "
            + "counts, so a member who rejoins starts again at nought")
    void aRejoinedMemberStartsAgain() {
        List<TerminationRule.Presentation> history = List.of(unpaid("2026-06-25"), unpaid("2026-07-25"),
                unpaid("2026-08-25"));
        assertThat(UnpaidCounting.count(history, null, null, LocalDate.of(2026, 9, 1)).unpaids()).isZero();
        assertThat(UnpaidCounting.count(history, null, null, LocalDate.of(2010, 3, 1)).unpaids())
                .as("the twin: a member who joined long ago keeps every unpaid").isEqualTo(3);
        // A count captured before the rejoining belongs to the membership that ended.
        assertThat(UnpaidCounting.count(List.of(), null, new UnpaidCounting.Captured(2, LocalDate.of(2026, 8, 31)),
                LocalDate.of(2026, 9, 1)).unpaids()).isZero();
    }

    @Test
    @DisplayName("D24: the opening position still counts, dated in its last month -- until a captured count replaces it")
    void theOpeningPositionStillCounts() {
        UnpaidCounting.Opening opening = new UnpaidCounting.Opening(2, LocalDate.of(2026, 8, 1));
        assertThat(UnpaidCounting.count(List.of(unpaid("2026-09-25")), opening, null, null).unpaids()).isEqualTo(3);
        assertThat(UnpaidCounting.count(List.of(unpaid("2026-09-25")), opening,
                new UnpaidCounting.Captured(0, LocalDate.of(2026, 9, 14)), null).unpaids())
                .as("a later capture replaces it").isEqualTo(1);
    }
}

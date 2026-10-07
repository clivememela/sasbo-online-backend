package za.co.titandynamix.sasbo_online_backend.acb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import za.co.titandynamix.sasbo_online_backend.acb.RunPreparer.Preparation;
import za.co.titandynamix.sasbo_online_backend.enums.AcbExclusionReason;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stephne, DOD-03: "Cannot present more then 3 unpaid debits" -- a member already at three is left out of the run,
 * named, so a staff member confirms the ending (DOD-22) or captures a new count (DOD-28).
 */
@DisplayName("RunPreparer -- a member at three unpaids (Stephne, DOD-03)")
class RunPreparerLimitTest {

    private static final BigDecimal RATE = new BigDecimal("174.00");

    private static Preparation prepare(Set<Integer> atTheLimit, List<RePresentationCandidate> candidates) {
        RunPreparer preparer = new RunPreparer(YearMonth.of(2026, 7), new DebitWindow(HolidayCalendar.WEEKENDS_ONLY),
                RunPreparerTest.BANKS, RATE, candidates, atTheLimit);
        preparer.accept(RunPreparerTest.qualifying());
        return preparer.finish();
    }

    @Test
    @DisplayName("Stephne, DOD-03: a member at three is left out, listed as UNPAID_LIMIT_REACHED -- neither their own "
            + "debit nor a re-debit is presented")
    void aMemberAtThreeIsLeftOut() {
        RePresentationCandidate waiting = new RePresentationCandidate(501L, 7, RATE, YearMonth.of(2026, 6), false);
        Preparation prepared = prepare(Set.of(7), List.of(waiting));
        assertThat(prepared.lines()).isEmpty();
        assertThat(prepared.exclusions()).singleElement().satisfies(exclusion -> {
            assertThat(exclusion.memberId()).isEqualTo(7);
            assertThat(exclusion.reason()).isEqualTo(AcbExclusionReason.UNPAID_LIMIT_REACHED);
            assertThat(exclusion.reason().listed()).as("named, for a person to act on").isTrue();
        });
    }

    @Test
    @DisplayName("the must-pass twin: the same member below three is debited, and their re-debit is presented")
    void belowThreeTheMemberIsDebited() {
        RePresentationCandidate waiting = new RePresentationCandidate(501L, 7, RATE, YearMonth.of(2026, 6), false);
        Preparation prepared = prepare(Set.of(), List.of(waiting));
        assertThat(prepared.lines()).hasSize(2);
        assertThat(prepared.exclusions()).isEmpty();
    }

    @Test
    @DisplayName("the status rule comes first: a member who is not Active is counted as such, not named for the limit")
    void statusComesFirst() {
        RunPreparer preparer = new RunPreparer(YearMonth.of(2026, 7), new DebitWindow(HolidayCalendar.WEEKENDS_ONLY),
                RunPreparerTest.BANKS, RATE, List.of(), Set.of(7));
        preparer.accept(RunPreparerTest.with(RunPreparerTest.qualifying(), "status", "Suspended"));
        Preparation prepared = preparer.finish();
        assertThat(prepared.excludedFor(AcbExclusionReason.STATUS_NOT_ACTIVE)).isEqualTo(1);
        assertThat(prepared.excludedFor(AcbExclusionReason.UNPAID_LIMIT_REACHED)).isZero();
    }
}

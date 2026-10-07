package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A member's unpaid count, as every rule that needs one reads it -- the termination report, the refusal of a re-debit
 * at three, and the run that leaves a member at three out. One pure computation, so no two of them can count a member
 * differently. It reads no repository and no clock: its inputs are handed to it.
 *
 * <h2>The rules, each Stephne's (6 October 2026)</h2>
 * <ul>
 *   <li>Every unpaid presentation counts, a re-debit included (question 27), and a debit that is paid starts the
 *       count again at nought ("It starts again at nought", DOD-20) -- {@link TerminationRule};</li>
 *   <li>never more than three ("Cannot present more then 3 unpaid debits", DOD-03) -- {@link #LIMIT};</li>
 *   <li>a count captured by hand replaces what came before the day it was captured ("Unpaids for re-debits are
 *       captured manually a day before we run ACB", DOD-28);</li>
 *   <li>a member who rejoins starts again: "This member will now have a new joined date" (DOD-27) -- nothing before
 *       the join date counts.</li>
 * </ul>
 * The legacy opening position (D24) stands as before: its unpaids are dated in their last month, before any of this
 * system's own.
 */
public final class UnpaidCounting {

    /** "Cannot present more then 3 unpaid debits" (Stephne, DOD-03): at three, nothing is presented again. */
    public static final int LIMIT = TerminationRule.LIMIT;

    /** The opening arrears position captured at switch-over (D24): unpaids standing, and the last unpaid month. */
    public record Opening(int unpaids, LocalDate lastUnpaidMonth) {
    }

    /** A count captured by hand (DOD-28): {@code unpaids} as at {@code countedOn}. */
    public record Captured(int unpaids, LocalDate countedOn) {
    }

    /**
     * What the rules make of a member.
     *
     * @param verdict          the termination rule's verdict over what counts
     * @param lastUnpaidLineId this system's last unpaid line in the count -- where an ending's letter hangs (DOD-24);
     *                         null when every unpaid counted came from the opening position or a captured count
     * @param thirdUnpaidOn    the day the count reached three, when it has; null otherwise (DOD-23 asks which date an
     *                         ending carries -- the screen shows both)
     * @param counted          the presentations that counted, oldest first -- synthetic ones carry negative ids
     */
    public record Count(TerminationRule.Verdict verdict, Long lastUnpaidLineId, LocalDate thirdUnpaidOn,
                        List<TerminationRule.Presentation> counted) {

        public int unpaids() {
            return verdict.count();
        }

        /** Whether the member is at three: no debit is presented to them again (DOD-03). */
        public boolean atTheLimit() {
            return verdict.count() >= LIMIT;
        }
    }

    private UnpaidCounting() {
    }

    /**
     * The member's count.
     *
     * @param history  this system's presentations of the member's debits, from sent runs, in any order
     * @param opening  the opening arrears position, or {@code null}
     * @param captured the count captured by hand, or {@code null}
     * @param joinedOn the member's join date, or {@code null} when none is on record
     */
    public static Count count(List<TerminationRule.Presentation> history, Opening opening, Captured captured,
                              LocalDate joinedOn) {
        Captured capture = captured != null && joinedOn != null && captured.countedOn().isBefore(joinedOn)
                ? null : captured;
        List<TerminationRule.Presentation> counted = new ArrayList<>();
        for (TerminationRule.Presentation presentation : history) {
            if (joinedOn != null && presentation.actionDate().isBefore(joinedOn)) {
                continue;
            }
            if (capture != null && presentation.actionDate().isBefore(capture.countedOn())) {
                continue;
            }
            counted.add(presentation);
        }
        if (capture != null) {
            for (int i = 0; i < capture.unpaids(); i++) {
                // Dated the day before the capture, so they sort before anything presented from it on.
                counted.add(new TerminationRule.Presentation(-1_000L - i, capture.countedOn().minusDays(1),
                        AcbLineOutcome.UNPAID));
            }
        } else if (opening != null && (joinedOn == null || !opening.lastUnpaidMonth().isBefore(joinedOn))) {
            for (int i = 0; i < opening.unpaids(); i++) {
                // The old system's unpaids, dated in their last month -- before any of this system's own (SD3-J).
                counted.add(new TerminationRule.Presentation(-1L - i, opening.lastUnpaidMonth(), AcbLineOutcome.UNPAID));
            }
        }
        counted.sort(Comparator.comparing(TerminationRule.Presentation::actionDate)
                .thenComparingLong(TerminationRule.Presentation::lineId));
        TerminationRule.Verdict verdict = TerminationRule.judge(counted);

        // Walk the streak the verdict counted: the line an ending's letter hangs on, and the day it reached three.
        Long lastUnpaidLine = null;
        LocalDate third = null;
        int streak = 0;
        for (TerminationRule.Presentation presentation : counted) {
            if (presentation.outcome() == AcbLineOutcome.PAID) {
                streak = 0;
                lastUnpaidLine = null;
                third = null;
            } else if (presentation.outcome() == AcbLineOutcome.UNPAID) {
                streak++;
                if (presentation.lineId() > 0) {
                    lastUnpaidLine = presentation.lineId();
                }
                if (streak == LIMIT) {
                    third = presentation.actionDate();
                }
            }
        }
        return new Count(verdict, lastUnpaidLine, third, List.copyOf(counted));
    }
}

package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "If we have 3 unsuccessful retries we cancel membership" -- SASBO, 18 September 2026 -- as a
 * rule over a member's debits, in BOTH of the readings the sentence allows.
 *
 * <h2>Two readings, because the sentence has two</h2>
 * <ul>
 *   <li>{@link Reading#EVERY_UNPAID_COUNTS}: three unpaid debits in a row, the first one included --
 *       what the re-debit letter's "default on monthly payments for three months" suggests.</li>
 *   <li>{@link Reading#RETRIES_ONLY}: three unsuccessful presentations AGAIN, after the first -- what
 *       "3 unsuccessful retries" literally says.</li>
 * </ul>
 * In both, a debit that is paid resets the count. SASBO has been asked which reading is theirs
 * (question 27). Until they answer, the report shows who each reading would terminate, side by
 * side, and terminates nobody: a member's status changes only through the member's own service.
 *
 * <p>🔴 It is blind to the past. It counts the debits this system has sent, so a member already in
 * arrears in the old system starts at nought -- until the opening arrears position SASBO has been
 * asked for is imported (question 45). The report says so on its face.
 */
public final class TerminationRule {

    /** Three unsuccessful presentations cancel a membership. */
    public static final int LIMIT = 3;

    public enum Reading {
        EVERY_UNPAID_COUNTS,
        RETRIES_ONLY
    }

    /** One debit of a member's, as the rule sees it. */
    public record Presentation(long lineId, LocalDate actionDate, boolean rePresentation, AcbLineOutcome outcome) {
    }

    /**
     * @param count          unsuccessful presentations since the last one that was paid
     * @param unpaidMonths   the months of the presentations counted, oldest first
     */
    public record Verdict(boolean terminate, int count, List<YearMonth> unpaidMonths) {
    }

    private TerminationRule() {
    }

    /** What {@code reading} makes of a member's debits, in the order they were presented. */
    public static Verdict judge(List<Presentation> history, Reading reading) {
        List<Presentation> ordered = new ArrayList<>(history);
        ordered.sort(Comparator.comparing(Presentation::actionDate).thenComparingLong(Presentation::lineId));
        int count = 0;
        List<YearMonth> months = new ArrayList<>();
        for (Presentation presentation : ordered) {
            if (presentation.outcome() == AcbLineOutcome.PAID) {
                count = 0;
                months.clear();
            } else if (presentation.outcome() == AcbLineOutcome.UNPAID
                    && (reading == Reading.EVERY_UNPAID_COUNTS || presentation.rePresentation())) {
                count++;
                months.add(YearMonth.from(presentation.actionDate()));
            }
            // PENDING -- the bank has not answered yet -- neither counts nor resets.
        }
        return new Verdict(count >= LIMIT, count, List.copyOf(months));
    }
}

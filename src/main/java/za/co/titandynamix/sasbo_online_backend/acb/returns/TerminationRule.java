package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.AcbLineOutcome;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "If we have 3 unsuccessful retries we cancel membership" -- SASBO, 18 September 2026 -- as a
 * rule over a member's debits.
 *
 * <h2>Every unpaid presentation counts</h2>
 * The sentence could be read two ways, and SASBO was asked which (question 27). They answered on
 * 21 September 2026: <i>"Three unpaids, it could be a payment, a re-debit and another payment that
 * will count as 3 unpaids"</i>. So the month's own debit counts, and so does its re-debit: three can
 * fall inside TWO months -- a month's debit, then the next month's debit and the re-debit of the
 * first, on the same file. The other reading (retries only) is gone.
 *
 * <p>A debit that is paid resets the count. That half is PROVISIONAL: whether a payment restarts it
 * was not answered (question 49). The report shows who the rule would terminate and terminates
 * nobody: a member's status changes only through the member's own service.
 *
 * <p>🔴 It is blind to the past. It counts the debits this system has sent, so a member already in
 * arrears in the old system starts at nought -- until the opening arrears position is captured
 * (question 45). The report says so on its face.
 */
public final class TerminationRule {

    /** Three unsuccessful presentations cancel a membership. */
    public static final int LIMIT = 3;

    /** One debit of a member's, as the rule sees it: a re-debit is a presentation like any other. */
    public record Presentation(long lineId, LocalDate actionDate, AcbLineOutcome outcome) {
    }

    /**
     * @param count          unsuccessful presentations since the last one that was paid
     * @param unpaidMonths   the months of the presentations counted, oldest first -- a month
     *                       appears twice when its own debit and a re-debit both came back
     */
    public record Verdict(boolean terminate, int count, List<YearMonth> unpaidMonths) {
    }

    private TerminationRule() {
    }

    /** What the rule makes of a member's debits, in the order they were presented. */
    public static Verdict judge(List<Presentation> history) {
        List<Presentation> ordered = new ArrayList<>(history);
        ordered.sort(Comparator.comparing(Presentation::actionDate).thenComparingLong(Presentation::lineId));
        int count = 0;
        List<YearMonth> months = new ArrayList<>();
        for (Presentation presentation : ordered) {
            if (presentation.outcome() == AcbLineOutcome.PAID) {
                count = 0;
                months.clear();
            } else if (presentation.outcome() == AcbLineOutcome.UNPAID) {
                count++;
                months.add(YearMonth.from(presentation.actionDate()));
            }
            // PENDING -- the bank has not answered yet -- neither counts nor resets.
        }
        return new Verdict(count >= LIMIT, count, List.copyOf(months));
    }
}

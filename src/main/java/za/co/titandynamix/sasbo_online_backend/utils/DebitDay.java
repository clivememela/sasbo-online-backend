package za.co.titandynamix.sasbo_online_backend.utils;

/**
 * The permitted debit days, as ONE definition.
 *
 * <h2>What this replaces</h2>
 * The set {@code {20, 25, 31}} was hardcoded in four places — this validator,
 * {@code memberConstants.DEBIT_DAY_OPTIONS}, {@code CaptureMember}'s own
 * validator and an inline literal in {@code Members.jsx}. Four copies of a rule
 * is four chances to disagree, and they already did: the server rejected what the
 * reference data supplied.
 *
 * <h2>🔴 Why the old set was not merely narrow but actively broken</h2>
 * The server sets {@code debitDay} from the bank's subscription row and then
 * validated it against {@code {20, 25, 31}} — and <b>13 of the 15 subscription
 * rows fall outside that set</b> (Absa 5, Standard Bank 15, Capitec 1, FNB 10,
 * TymeBank 28…). Only Nedbank (20) and Goldman Sachs (25) could pass. The
 * validator runs on {@code create}, {@code update} <em>and</em>
 * {@code updateBanking}, so writing a debit-order member back <b>unchanged</b>
 * was a 400 for most of them.
 *
 * <h2>The range</h2>
 * The specification gives the range as <b>20–31</b>. The bottom of the range is
 * the 20th: subscriptions are collected in the second half of the month, and no
 * source offers a reason to allow earlier.
 *
 * <h2>The business-day rule lives in {@code acb.DebitWindow}, not here</h2>
 * A weekend or non-business day moves FORWARD to the next business day in the same month; only where
 * forward would leave the month does it move BACKWARD. So a date never falls below the 20th nor leaves
 * its month. That is SASBO's answer to question 33 (21 September 2026: "if a debit date is on the 25th
 * and it is a Saturday it will move to the following business day which is the 27 ... we never go into
 * the next month"), and their July 2026 bureau file did exactly that.
 * The signed specification (18 August session, "SASBO Membership System" line 1862, and the A.7
 * glossary) says BACKWARD first; SASBO's answer overrules it, and both repositories ran the backward
 * reading until the answer came. The two readings agree on all three of the specification's worked
 * examples and differ on 56 of 288 cells in 2026-2027 (day 25 in July 2026: Monday the 27th, not
 * Friday the 24th). The client's debitDate.js carries the same rule; both are tested against the one
 * generated fixture, debit-window.json.
 */
public final class DebitDay {

    /** The earliest permitted debit day. */
    public static final int EARLIEST = 20;

    /** The latest permitted debit day. A short month is clamped, not rejected. */
    public static final int LATEST = 31;

    private DebitDay() {
    }

    /**
     * @param day the day-of-month to test
     * @return {@code true} when {@code day} is within the permitted range
     */
    public static boolean isInRange(int day) {
        return day >= EARLIEST && day <= LATEST;
    }

    /**
     * @param day the day-of-month to check
     * @throws IllegalArgumentException naming the range, because this message
     *         reaches the user now that the client surfaces the server's body
     */
    public static void requireInRange(int day) {
        if (!isInRange(day)) {
            throw new IllegalArgumentException(message());
        }
    }

    /** The one wording, so the API and the form cannot describe the rule differently. */
    public static String message() {
        return "Debit day must be between " + EARLIEST + " and " + LATEST;
    }
}

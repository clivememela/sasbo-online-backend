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
 * <h2>The business-day rule is NOT here yet</h2>
 * Not computed on the server. The direction was CORRECTED on 2026-09-17: a
 * legacy SASBO re-debit letter (planning/branches/acb/legacy/) and the spec's
 * 18 August worked example both move a weekend or holiday FORWARD (a Sunday
 * 20th is debited Monday the 21st). The rule to implement is therefore: move
 * forward, except where forward would leave the month, then move backward --
 * so a date never falls below the 20th nor past month end. An earlier note here
 * said "backwards, never forwards", which would put a Saturday 20th on the
 * 19th, outside the window. The client's debitDate.js carries the rule and is
 * corrected in the capture-member re-port; the ACB arc computes collection dates.
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

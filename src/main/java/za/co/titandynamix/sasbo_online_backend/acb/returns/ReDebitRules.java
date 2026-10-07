package za.co.titandynamix.sasbo_online_backend.acb.returns;

import za.co.titandynamix.sasbo_online_backend.enums.ReDebit;
import za.co.titandynamix.sasbo_online_backend.enums.ReDebitWithdrawal;
import za.co.titandynamix.sasbo_online_backend.enums.UnpaidDecision;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Whether an unpaid may be presented again -- Stephne's rules of 6 October 2026, each a pure function with a test
 * named after her answer. No repository, no clock: the code's rule and the member's count are handed in.
 *
 * <ul>
 *   <li>"Code 2 means insufficient funds and allows to redebit" (DOD-03) -- a code that says YES;</li>
 *   <li>"Code 4 - cannot redebit because it indicates a payment stopped by the member" (DOD-03) and "30/31/32/34 -
 *       All the same - No authority to debit which means we cannot try to redebit" (DOD-04) -- codes that say NO;</li>
 *   <li>the codes her sheet lists without a ruling (6, 15, 18, 26: Friday item 9), and any code the table does not
 *       know, are UNKNOWN -- never re-debited until an administrator sets them;</li>
 *   <li>"Cannot present more then 3 unpaid debits" (DOD-03) -- at three, nothing is presented again;</li>
 *   <li>a member who comes back pays "Only the current month" (DOD-26), and one who rejoins "will now have a new joined
 *       date" (DOD-27) -- a re-debit from before is withdrawn.</li>
 * </ul>
 */
public final class ReDebitRules {

    /** What the rules make of a request to present an unpaid again. */
    public enum Verdict {
        /** The code allows it and the member is below three. */
        ALLOWED,
        /** The code says it may never be re-debited (DOD-03, DOD-04). */
        CODE_FORBIDS,
        /** Nobody has said whether the code may be re-debited (Friday item 9): not until an administrator sets it. */
        CODE_UNKNOWN,
        /** The member has three unpaids already (DOD-03). */
        LIMIT_REACHED
    }

    private ReDebitRules() {
    }

    /**
     * Whether an unpaid under a code with {@code rule} may be presented again, for a member whose count -- this unpaid
     * included -- is {@code unpaids}. The code is asked first: a code that forbids a re-debit forbids it at any count.
     */
    public static Verdict mayPresentAgain(ReDebit rule, int unpaids) {
        ReDebit what = rule == null ? ReDebit.UNKNOWN : rule;
        return switch (what) {
            case NO -> Verdict.CODE_FORBIDS;
            case UNKNOWN -> Verdict.CODE_UNKNOWN;
            case YES -> unpaids >= UnpaidCounting.LIMIT ? Verdict.LIMIT_REACHED : Verdict.ALLOWED;
        };
    }

    /**
     * What the screen suggests for an undecided unpaid: present it again only where the rules allow it; otherwise
     * suspend -- the debit is not presented again -- except for a code nobody has ruled on, where nothing is suggested
     * and the administrator decides once SASBO has said (never guessed).
     */
    public static UnpaidDecision suggestion(ReDebit rule, int unpaids) {
        return switch (mayPresentAgain(rule, unpaids)) {
            case ALLOWED -> UnpaidDecision.RE_PRESENT;
            case CODE_FORBIDS, LIMIT_REACHED -> UnpaidDecision.SUSPEND;
            case CODE_UNKNOWN -> null;
        };
    }

    /**
     * Whether a re-debit decided on for an unpaid of {@code unpaidOn} is withdrawn: the member rejoined after it, with a
     * new join date (DOD-27), or came back from a suspension on or after it -- "Only the current month" (DOD-26).
     *
     * @param joinedOn      the member's join date, or null
     * @param reinstatedOn  the day the member last came back to an active status, or null
     */
    public static Optional<ReDebitWithdrawal> withdrawal(LocalDate unpaidOn, LocalDate joinedOn, LocalDate reinstatedOn) {
        if (joinedOn != null && unpaidOn.isBefore(joinedOn)) {
            return Optional.of(ReDebitWithdrawal.REJOINED);
        }
        if (reinstatedOn != null && !unpaidOn.isAfter(reinstatedOn)) {
            return Optional.of(ReDebitWithdrawal.REACTIVATED);
        }
        return Optional.empty();
    }
}

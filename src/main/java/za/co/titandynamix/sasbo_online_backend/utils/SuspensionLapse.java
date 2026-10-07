package za.co.titandynamix.sasbo_online_backend.utils;

import java.time.Instant;
import java.time.LocalDate;

/**
 * When a suspension lapses -- the ONE place (Stephne, MEN-18: "The system should also remind us when the suspension
 * period for a member have lapsed, so we can delete. (suspensions is only for 3months)").
 *
 * <p>A suspension lapses {@value #MONTHS} calendar months after the day it began -- the day, in SASBO's zone, of the
 * member's latest change into a suspended status -- and has lapsed from that day on: begun on 2 June, it lapses on
 * 2 September; begun on 30 November, on 28 February (29 in a leap year), as a calendar counts months. Whether the three
 * months run from the day of the suspension is asked of SASBO (MEN-18's clarification); this is that reading.
 */
public final class SuspensionLapse {

    /** How long a suspension lasts before the member may be deleted. */
    public static final int MONTHS = 3;

    private SuspensionLapse() {
    }

    /** The day a suspension begun on {@code suspendedOn} lapses. */
    public static LocalDate lapsesOn(LocalDate suspendedOn) {
        return suspendedOn.plusMonths(MONTHS);
    }

    /** Whether a suspension begun on {@code suspendedOn} has lapsed by {@code asAt}: on the day it lapses, it has. */
    public static boolean lapsedBy(LocalDate suspendedOn, LocalDate asAt) {
        return !lapsesOn(suspendedOn).isAfter(asAt);
    }

    /**
     * The last day a suspension can have begun and have lapsed by {@code asAt} -- so a query asks for the suspensions
     * begun on or before it, and agrees with {@link #lapsedBy} to the day, month ends included.
     */
    public static LocalDate lastStartLapsedBy(LocalDate asAt) {
        LocalDate last = asAt.minusMonths(MONTHS);
        while (lapsedBy(last.plusDays(1), asAt)) {
            last = last.plusDays(1);
        }
        return last;
    }

    /** The first instant of the day after {@link #lastStartLapsedBy}: a suspension begun before it has lapsed. */
    public static Instant lapsedIfBegunBefore(LocalDate asAt) {
        return lastStartLapsedBy(asAt).plusDays(1).atStartOfDay(MonthRange.ZONE).toInstant();
    }
}

package za.co.titandynamix.sasbo_online_backend.acb;

import za.co.titandynamix.sasbo_online_backend.utils.DebitDay;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * The run-window rule: the date a member's debit day becomes in a given month.
 *
 * <h2>The rule</h2>
 * A member's debit day is a day from the 20th to the 31st. In a given month it is clamped to the
 * month's last day (the 31st of February is the 28th or 29th), and if that is not a business day it
 * moves to one. It never leaves the month, never falls below the 20th, and is never a weekend or a
 * public holiday in the calendar it was computed with.
 *
 * <h2>Which way it moves: FORWARD, backward only where forward would leave the month</h2>
 * SASBO's answer to question 33, 21 September 2026: <i>"We never go into the next month. If the debit
 * date is on the 30 or 31st and that fall on a weekend it moved back to the available business day.
 * If a debit date is on the 25th and it is a Saturday it will move to the following business day
 * which is the 27."</i> Their July 2026 bureau file had already done exactly that: nobody was debited
 * on Friday the 24th, 365 members on Monday the 27th.
 *
 * <p>Until then this class shipped the other reading -- <b>backward</b> to the most recent business
 * day, forward only where backward fell below the 20th -- because the specification SASBO validated
 * (18 August 2026, and its A.7 glossary) says so. Both readings satisfy all three of the
 * specification's worked examples, which is why no example ever told them apart; they differ on 56
 * of the 288 month-and-day cells of 2026 and 2027, 64 with the statutory holidays. The rule below is
 * written as "move the preferred way; if that leaves the window, move the other way", which is both
 * readings at once, so the answer changed one token, {@link #FIRST_MOVE}, and the fixture's diff is
 * exactly the cells that moved.
 *
 * <p>🟡 One seam is left open on SASBO's side (question 47): a Sunday the 30th with Monday the 31st
 * free goes forward here, and back if their sentence is read literally. Two cells in 2026-2027, the
 * first live on 30 May 2027; their August 2026 file answers it.
 *
 * <h2>One rule, one class, compared through a fixture</h2>
 * The client computes the same dates in its own language. The two are never tested against each
 * other -- two implementations that agree can both be wrong, and on 17 September they were. Both
 * are tested against one generated artefact, {@code debit-window.json}, which this class produces.
 */
public final class DebitWindow {

    /** Which way a date that is not a business day moves first. */
    public enum Direction {
        BACKWARD,
        FORWARD;

        Direction opposite() {
            return this == BACKWARD ? FORWARD : BACKWARD;
        }
    }

    /**
     * The direction, as one expression: forward, on SASBO's answer to question 33 (21 September
     * 2026). It was BACKWARD, the specification's reading, until that answer; the client's
     * {@code debitDate.js} flipped in the same sitting, and {@code debit-window.json} was regenerated.
     */
    public static final Direction FIRST_MOVE = Direction.FORWARD;

    /** The earliest day of the window. */
    public static final int FIRST_DAY = DebitDay.EARLIEST;

    /** The latest day of the window. A short month is clamped, never rejected. */
    public static final int LAST_DAY = DebitDay.LATEST;

    /**
     * A run of non-business days longer than this is not a calendar, it is a mistake in one --
     * South Africa's longest is four (Good Friday to Family Day). Bounding the walk turns a broken
     * holiday list into an error instead of a hang.
     */
    private static final int LONGEST_BREAK = 10;

    private final HolidayCalendar holidays;

    public DebitWindow(HolidayCalendar holidays) {
        this.holidays = holidays;
    }

    /** The calendar this window computes with -- stored on the run, and caveated on screen. */
    public HolidayCalendar holidays() {
        return holidays;
    }

    /** The name the window fixture publishes for this reading of the rule. */
    public static String ruleName() {
        return FIRST_MOVE == Direction.BACKWARD
                ? "backward-in-month-forward-below-20th"
                : "forward-in-month-backward-at-end";
    }

    /**
     * The date debit day {@code debitDay} becomes in {@code month}.
     *
     * @throws IllegalArgumentException when the day is outside the window -- a member on the 5th
     *         is excluded from a run by name before this is ever asked
     */
    public LocalDate actionDate(YearMonth month, int debitDay) {
        DebitDay.requireInRange(debitDay);
        LocalDate target = month.atDay(Math.min(debitDay, month.lengthOfMonth()));
        LocalDate preferred = move(target, FIRST_MOVE);
        return inWindow(preferred, month) ? preferred : move(target, FIRST_MOVE.opposite());
    }

    private boolean inWindow(LocalDate date, YearMonth month) {
        return YearMonth.from(date).equals(month) && date.getDayOfMonth() >= FIRST_DAY;
    }

    private LocalDate move(LocalDate from, Direction direction) {
        LocalDate date = from;
        for (int step = 0; step <= LONGEST_BREAK; step++) {
            if (holidays.isBusinessDay(date)) {
                return date;
            }
            date = direction == Direction.BACKWARD ? date.minusDays(1) : date.plusDays(1);
        }
        throw new IllegalStateException("No business day within " + LONGEST_BREAK + " days of "
                + from + " in the '" + holidays.name() + "' calendar -- the calendar is wrong");
    }
}

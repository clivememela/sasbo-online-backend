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
 * <h2>🔴 Which way it moves is CONTESTED, and it is one line: {@link #FIRST_MOVE}</h2>
 * The specification SASBO validated (18 August 2026, and its A.7 glossary) says a non-business day
 * moves <b>backward</b> to the most recent business day, forward only where backward would fall
 * below the 20th. SASBO's own answer of 18 September says the opposite -- <i>"it moves to the next
 * available business day"</i> -- and their July 2026 bureau file agrees with the answer: Saturday the
 * 25th was debited on Monday the 27th. Both readings satisfy all three of the specification's
 * worked examples, which is why no example ever told them apart; they differ on 56 of the 288
 * month-and-day cells of 2026 and 2027.
 *
 * <p>SASBO has been asked the one case that separates them. Until they answer, this class ships the
 * <b>specification's</b> reading, which is also what the client's {@code debitDate.js} runs, so the
 * two agree today. The day the answer is "forward", the change is {@code FIRST_MOVE} and nothing
 * else: the rule below is written as "move the preferred way; if that leaves the window, move the
 * other way", which is both readings at once. The window fixture's rule name follows it, and the
 * 56 cells that move are the fixture's diff.
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
     * 🔴 THE CONTESTED FACT, AS ONE EXPRESSION. The specification's reading. If SASBO answers
     * "forward" (question 33), this token changes and the fixture is regenerated -- nothing else.
     */
    public static final Direction FIRST_MOVE = Direction.BACKWARD;

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

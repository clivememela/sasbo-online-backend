package za.co.titandynamix.sasbo_online_backend.domain.debit;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * The debit-day rule, in one place, so no screen has to re-derive it.
 *
 * <h2>The rule</h2>
 * A member's debit day is a day-of-month in {@link DebitDay#EARLIEST}..{@link
 * DebitDay#LATEST}. If that day is not a business day, the debit moves
 * <strong>backwards</strong> to the last business day before it — <em>never
 * forwards</em>, because a debit must not slip into the following month and take
 * two subscriptions with it.
 *
 * <h2>🔑 Why it iterates instead of stepping once</h2>
 * Because one step is not enough, and the case where it is not enough is common
 * rather than exotic:
 * <ul>
 *   <li>The 31st on a <b>Sunday</b> steps to Saturday — still not a business day.
 *   <li>Under the Public Holidays Act a public holiday on a <b>Sunday</b> makes
 *       the <b>Monday</b> a holiday too. So a Tuesday debit can step onto a
 *       holiday Monday, then onto a Sunday, then onto a Saturday.
 * </ul>
 * Three consecutive non-business days is an ordinary Easter weekend. A
 * step-once implementation is wrong precisely when the calendar is busiest.
 *
 * <h2>Why it cannot run away</h2>
 * The search is bounded by the start of the month: the earliest permitted debit
 * day is the 20th, so the walk can cover at most nineteen days before hitting the
 * 1st, and the loop asserts that bound rather than trusting it.
 */
public final class BusinessDays {

    private final PublicHolidays holidays;

    public BusinessDays(PublicHolidays holidays) {
        this.holidays = holidays;
    }

    /**
     * @return {@code true} when {@code date} is a weekday that is not a public holiday
     */
    public boolean isBusinessDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY
                && !this.holidays.isPublicHoliday(date);
    }

    /**
     * Moves {@code date} back to the last business day on or before it.
     *
     * @param date the nominal debit date
     * @return the same date when it is already a business day, otherwise the
     *         latest business day before it, never crossing into the previous month
     * @throws IllegalStateException if the whole month before {@code date} is
     *         non-business — impossible for a real calendar, and a signal that a
     *         holiday source is answering {@code true} for everything
     */
    public LocalDate adjustBackwards(LocalDate date) {
        LocalDate candidate = date;
        LocalDate firstOfMonth = date.withDayOfMonth(1);
        while (!isBusinessDay(candidate)) {
            if (!candidate.isAfter(firstOfMonth)) {
                throw new IllegalStateException(
                        "No business day on or before " + date + " within its own month. "
                                + "The holiday source is answering true for every date.");
            }
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    /**
     * The actual date a member on {@code debitDay} is debited in a given month.
     *
     * <p>Handles the short-month case that makes the 31st special: February has no
     * 31st, so the nominal date is clamped to the last day of the month first and
     * only then adjusted backwards. Clamping and adjusting are two different rules
     * and doing them in the wrong order gives the wrong answer in February.
     *
     * @param debitDay the member's chosen day-of-month
     * @param inMonth  any date in the month being calculated
     * @return the effective debit date
     */
    public LocalDate debitDateFor(int debitDay, LocalDate inMonth) {
        DebitDay.requireInRange(debitDay);
        int lastDayOfMonth = inMonth.lengthOfMonth();
        LocalDate nominal = inMonth.withDayOfMonth(Math.min(debitDay, lastDayOfMonth));
        return adjustBackwards(nominal);
    }

    /**
     * Whether the answers this instance gives have been checked against public
     * holidays, or only against weekends. Surfaced to the user; see
     * {@link PublicHolidays#coversPublicHolidays()}.
     */
    public boolean coversPublicHolidays() {
        return this.holidays.coversPublicHolidays();
    }
}

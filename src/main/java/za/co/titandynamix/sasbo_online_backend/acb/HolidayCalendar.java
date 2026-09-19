package za.co.titandynamix.sasbo_online_backend.acb;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The public holidays a debit may not be taken on, as DATA.
 *
 * <h2>Why a holiday is a date and never a rule in code</h2>
 * South Africa's Public Holidays Act gives a schedule, Easter's two days and a Sunday rule, and
 * then the President declares others ad hoc -- an election, a day of mourning. A calendar computed
 * in code is wrong the first time that happens, silently. A calendar that is a list of dates, with
 * the last date it is known to be complete for, can be corrected by adding a row, and says out loud
 * when a month lies beyond what it knows.
 *
 * <h2>{@link #WEEKENDS_ONLY} is honest, not a shortcut</h2>
 * It is what the run uses while no maintained calendar is wired, and it says so: its
 * {@link #knownUntil()} is empty, so every screen showing a date it produced prints the caveat
 * that holidays were not considered. Christmas Day 2026 is a Friday; on this calendar a member on
 * the 25th is debited on it.
 */
public interface HolidayCalendar {

    /** The weekends-only calendar: no public holiday is known, and it says so. */
    HolidayCalendar WEEKENDS_ONLY = of("weekends-only", null, Set.of());

    /** A stable name, stored on every run that used this calendar. */
    String name();

    /** Whether {@code date} is a public holiday in this calendar. Weekends are not asked here. */
    boolean isHoliday(LocalDate date);

    /** The last date this calendar is complete for, or empty when it knows no holidays at all. */
    Optional<LocalDate> knownUntil();

    /** The holidays, sorted -- what the window fixture publishes for this set. */
    Set<LocalDate> dates();

    /** Whether any public holiday is known to this calendar at all. */
    default boolean holidaysKnown() {
        return knownUntil().isPresent();
    }

    /** A business day: Monday to Friday, and not a holiday in this calendar. */
    default boolean isBusinessDay(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> !isHoliday(date);
        };
    }

    /**
     * A calendar over a fixed set of dates.
     *
     * @param knownUntil the last date the list is complete for; {@code null} for a calendar that
     *                   claims to know no holidays, which is only honest when {@code dates} is empty
     */
    static HolidayCalendar of(String name, LocalDate knownUntil, Collection<LocalDate> dates) {
        if (knownUntil == null && !dates.isEmpty()) {
            throw new IllegalArgumentException(
                    "A calendar that lists holidays must say until when it is complete");
        }
        Set<LocalDate> sorted = java.util.Collections.unmodifiableSet(new TreeSet<>(dates));
        return new HolidayCalendar() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public boolean isHoliday(LocalDate date) {
                return sorted.contains(date);
            }

            @Override
            public Optional<LocalDate> knownUntil() {
                return Optional.ofNullable(knownUntil);
            }

            @Override
            public Set<LocalDate> dates() {
                return sorted;
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }
}

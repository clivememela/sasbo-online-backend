package za.co.titandynamix.sasbo_online_backend.acb;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Stream;

/**
 * The calendars and the range the window fixture covers -- written down once, for the generator
 * and every test that reads a date through it.
 *
 * <h2>The statutory holidays are DATA, listed, not computed</h2>
 * South Africa's Public Holidays Act 36 of 1994 for 2026 and 2027: the fixed schedule, Good Friday
 * and Family Day, and the Act's rule that a holiday falling on a Sunday makes the Monday one
 * (9 August 2026, 21 March 2027, 26 December 2027). Listed rather than computed from an Easter
 * algorithm because the calendar is data (a day declared ad hoc is a row added, never a code
 * change) -- and a list can be checked against a printed calendar, which is how these 27 were
 * checked, and re-derived independently by the planning pack's own script.
 *
 * <p>⚠️ Days declared ad hoc -- the 2026 local-government election day among them -- are NOT here,
 * which is exactly why the set carries a known-until date and the run says which set it used.
 */
public final class AcbCalendars {

    /** The fixture's range: pinned constants, never derived from today's date. */
    public static final YearMonth FROM = YearMonth.of(2026, 1);
    public static final YearMonth TO = YearMonth.of(2027, 12);

    public static final List<LocalDate> ZA_STATUTORY_2026_2027 = Stream.of(
                    "2026-01-01", "2026-03-21", "2026-04-03", "2026-04-06", "2026-04-27",
                    "2026-05-01", "2026-06-16", "2026-08-09", "2026-08-10", "2026-09-24",
                    "2026-12-16", "2026-12-25", "2026-12-26",
                    "2027-01-01", "2027-03-21", "2027-03-22", "2027-03-26", "2027-03-29",
                    "2027-04-27", "2027-05-01", "2027-06-16", "2027-08-09", "2027-09-24",
                    "2027-12-16", "2027-12-25", "2027-12-26", "2027-12-27")
            .map(LocalDate::parse)
            .toList();

    /** The statutory set, complete to the end of the fixture's range and no further. */
    public static final HolidayCalendar ZA_STATUTORY =
            HolidayCalendar.of("za-statutory", LocalDate.of(2027, 12, 31), ZA_STATUTORY_2026_2027);

    /** Both sets the fixture publishes, weekends-only first. */
    public static final List<HolidayCalendar> SETS = List.of(HolidayCalendar.WEEKENDS_ONLY, ZA_STATUTORY);

    /** Every month of the range, in order. */
    public static List<YearMonth> months() {
        return Stream.iterate(FROM, m -> !m.isAfter(TO), m -> m.plusMonths(1)).toList();
    }

    private AcbCalendars() {
    }
}
